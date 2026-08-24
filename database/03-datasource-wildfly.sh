#!/usr/bin/env bash
# =====================================================================
# WildFly 31+ configuration for the GlobalTrade SCM platform.
#
# Run AFTER: WildFly is started with standalone-full.xml.
#
# Usage:  DB_PASS=yourpassword ./03-datasource-wildfly.sh
#
# IDEMPOTENT: safe to re-run. Every step tolerates "already exists"
# (WFLYCTL0212 Duplicate resource) and continues. An earlier revision
# aborted on the first duplicate because of `set -e`, which silently
# skipped the JASPI fix in step 5 and produced 401 on every request.
# =====================================================================
set -uo pipefail          # deliberately NOT -e: duplicates must not abort the run

WILDFLY_HOME="${WILDFLY_HOME:?Set WILDFLY_HOME to your WildFly install directory}"
CLI="$WILDFLY_HOME/bin/jboss-cli.sh --connect"
DB_USER="${DB_USER:-globaltrade}"
DB_PASS="${DB_PASS:-CHANGE_ME}"

# run <description> <cli-command> - reports success/already-present, never aborts
run() {
  local desc="$1"; shift
  local out
  if out=$($CLI --command="$*" 2>&1); then
    echo "    OK        $desc"
  elif echo "$out" | grep -q "WFLYCTL0212"; then
    echo "    already   $desc (duplicate resource - left as-is)"
  else
    echo "    FAILED    $desc"
    echo "$out" | sed 's/^/              /'
  fi
}

echo "==> 1. Registering the MySQL JDBC driver as a WildFly module"
# The driver is a SERVER-level module, not bundled in the EAR - the same
# pattern used for jakarta.jakartaee-api (provided scope). This keeps the
# deployable artefact free of environment-specific drivers.
DRIVER_JAR=$(ls "$HOME"/.m2/repository/com/mysql/mysql-connector-j/*/mysql-connector-j-*.jar 2>/dev/null | tail -1)
if [ -n "$DRIVER_JAR" ]; then
  "$WILDFLY_HOME/bin/jboss-cli.sh" --connect --command="module add \
    --name=com.mysql --resources=$DRIVER_JAR \
    --dependencies=java.sql,java.naming,java.management" >/dev/null 2>&1 \
    && echo "    OK        module com.mysql" \
    || echo "    already   module com.mysql"
else
  echo "    already   module com.mysql (no jar in ~/.m2 - assuming installed)"
fi

run "jdbc-driver mysql" "/subsystem=datasources/jdbc-driver=mysql:add(\
driver-name=mysql, driver-module-name=com.mysql, \
driver-class-name=com.mysql.cj.jdbc.Driver, \
driver-xa-datasource-class-name=com.mysql.cj.jdbc.MysqlXADataSource)"

echo "==> 2. Creating the JTA datasource"
# POOL SIZING RATIONALE (documented in the Technical Documentation):
#   min-pool-size 20  - matches typical idle load without churn
#   max-pool-size 100 - raised from WildFly's 20 default because REQUIRES_NEW
#                       on the audit path holds a SECOND connection concurrently.
#   prepared-statements-cache-size 100 - reuse for the hot timer queries
run "data-source GlobalTradeDS" "data-source add \
--name=GlobalTradeDS \
--jndi-name=java:/jdbc/globaltrade \
--driver-name=mysql \
--connection-url=jdbc:mysql://localhost:3306/globaltrade?serverTimezone=UTC\&useSSL=false\&allowPublicKeyRetrieval=true\&rewriteBatchedStatements=true \
--user-name=$DB_USER \
--password=$DB_PASS \
--min-pool-size=20 \
--max-pool-size=100 \
--prepared-statements-cache-size=100 \
--share-prepared-statements=true \
--validate-on-match=true \
--background-validation=false \
--valid-connection-checker-class-name=org.jboss.jca.adapters.jdbc.extensions.mysql.MySQLValidConnectionChecker \
--exception-sorter-class-name=org.jboss.jca.adapters.jdbc.extensions.mysql.MySQLExceptionSorter \
--use-ccm=true"

# If the datasource already existed from an earlier run, its stored credentials
# are whatever that run used. Force them to match THIS run's DB_USER/DB_PASS.
run "credentials -> $DB_USER" \
  "/subsystem=datasources/data-source=GlobalTradeDS:write-attribute(name=user-name,value=$DB_USER)"
run "credentials -> password" \
  "/subsystem=datasources/data-source=GlobalTradeDS:write-attribute(name=password,value=$DB_PASS)"

echo "==> 3. Persistent EJB timers backed by the database"
# This is what makes automatic timers fire on ONE node in a cluster instead of
# every node. With the default in-memory store, a 6-node cluster would poll the
# carrier API six times - 6x the cost and 6x the rate-limit risk.
run "database-data-store globaltrade-timers" \
  "/subsystem=ejb3/service=timer-service/database-data-store=globaltrade-timers:add(\
datasource-jndi-name=java:/jdbc/globaltrade, database=mysql, partition=globaltrade, \
refresh-interval=60000, allow-execution=true)"

run "default-data-store -> globaltrade-timers" \
  "/subsystem=ejb3/service=timer-service:write-attribute(name=default-data-store, value=globaltrade-timers)"

echo "==> 4. Transaction timeout"
# 120 s: long enough for the month-end batch, short enough that a stuck carrier
# call cannot hold a transaction open indefinitely.
run "transaction default-timeout 120s" \
  "/subsystem=transactions:write-attribute(name=default-timeout,value=120)"

echo "==> 5. Jakarta Security: allow the custom HttpAuthenticationMechanism to run"
# WITHOUT THIS LINE the custom JwtAuthenticationMechanism is silently bypassed
# by WildFly's integrated JASPIC layer and every protected request returns 401
# regardless of the token presented. This is the single most common WildFly
# gotcha with Jakarta Security custom mechanisms.
run "integrated-jaspi=false on security domain 'other'" \
  "/subsystem=undertow/application-security-domain=other:write-attribute(name=integrated-jaspi,value=false)"

echo "==> 6. Explicit EJB pool sizing (do not rely on CPU auto-derivation)"
# Auto-derived pools shrink on a smaller production host. Capacity planning
# should be deliberate, not incidental to whatever hardware we deploy on.
# WildFly 26+ ships this pool with derive-size=from-worker-pools, and the two
# attributes are mutually exclusive: setting max-pool-size while derive-size is
# defined fails with WFLYCTL0105. Clear the derivation first, then state the size.
run "clear derive-size (WFLYCTL0105 guard)" \
  "/subsystem=ejb3/strict-max-bean-instance-pool=slsb-strict-max-pool:undefine-attribute(name=derive-size)"

run "slsb-strict-max-pool max-pool-size 200" \
  "/subsystem=ejb3/strict-max-bean-instance-pool=slsb-strict-max-pool:write-attribute(name=max-pool-size,value=200)"

echo "==> 7. Reloading"
$CLI --command=":reload" >/dev/null 2>&1
echo "    waiting for the server to come back..."
for i in $(seq 1 30); do
  sleep 2
  if $CLI --command=":read-attribute(name=server-state)" 2>/dev/null | grep -q running; then break; fi
done

echo "==> 8. Verifying the datasource"
if $CLI --command="/subsystem=datasources/data-source=GlobalTradeDS:test-connection-in-pool" 2>&1 | grep -q '"outcome" => "success"'; then
  echo "    OK        connection test passed"
  echo
  echo "WildFly configuration complete."
else
  echo "    FAILED    connection test - the MySQL user/password does not match."
  echo
  echo "    Fix it in Navicat or the IntelliJ database console:"
  echo "      ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';"
  echo "      GRANT SELECT, INSERT, UPDATE, DELETE ON globaltrade.* TO '$DB_USER'@'localhost';"
  echo "      FLUSH PRIVILEGES;"
  echo "    then re-run this script."
  exit 1
fi

echo "Remember to set the JWT signing key before deploying:"
echo "  \$WILDFLY_HOME/bin/jboss-cli.sh --connect --command=\"/system-property=gt.jwt.secret:add(value=\$(openssl rand -hex 32))\""
