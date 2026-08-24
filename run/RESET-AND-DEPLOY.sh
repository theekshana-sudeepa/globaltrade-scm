#!/usr/bin/env bash
# =====================================================================
# Clean reset + deploy for GlobalTrade SCM on WildFly.
#
# Run this whenever a deployment has failed and left the server in a
# half-configured state. Safe to run repeatedly.
#
# Usage:  DB_PASS=yourpassword ./run/RESET-AND-DEPLOY.sh
#
# Requires: WildFly already running with standalone-full.xml,
#           and 03-datasource-wildfly.sh already run successfully.
# =====================================================================
set -uo pipefail
cd "$(dirname "$0")/.."

WILDFLY_HOME="${WILDFLY_HOME:?Set WILDFLY_HOME}"
CLI="$WILDFLY_HOME/bin/jboss-cli.sh --connect"
DB_USER="${DB_USER:-globaltrade}"
DB_PASS="${DB_PASS:-CHANGE_ME}"
EAR="globaltrade-ear/target/globaltrade-scm.ear"

step() { echo; echo "==> $*"; }

step "0. Is the server up?"
if ! $CLI --command=":read-attribute(name=server-state)" 2>/dev/null | grep -q running; then
  echo "    WildFly is not running. Start it in your other tab first:"
  echo "      \"\$WILDFLY_HOME/bin/standalone.sh\" -c standalone-full.xml"
  exit 1
fi
echo "    OK        server running"

step "0b. Is the EAR built?"
if [ ! -f "$EAR" ]; then
  echo "    $EAR not found. Build it first:  mvn clean install"
  exit 1
fi
echo "    OK        $(ls -lh "$EAR" | awk '{print $5}')"

step "1. Removing any half-deployed application"
if $CLI --command="undeploy globaltrade-scm.ear" >/dev/null 2>&1; then
  echo "    OK        previous deployment removed"
else
  echo "    already   nothing was deployed"
fi

step "2. Clearing stale rows from the EJB timer table"
# A failed deploy leaves half-written auto-timer rows behind. On the next start
# the four @Schedule beans contend for locks on those rows, which is what
# produces 'Deadlock found when trying to get lock'.
if mysql -u "$DB_USER" -p"$DB_PASS" globaltrade -e "DELETE FROM JBOSS_EJB_TIMER;" 2>/dev/null; then
  echo "    OK        timer table cleared"
else
  echo "    FAILED    could not clear JBOSS_EJB_TIMER - check DB_USER / DB_PASS"
fi

step "3. Switching the datasource to READ_COMMITTED"
# InnoDB defaults to REPEATABLE READ, which takes gap locks on index ranges.
# Four timer beans inserting their auto-timer rows concurrently deadlock against
# those gaps. READ_COMMITTED does not take them. This is what WildFly recommends
# for the database-backed timer store, and it is the correct fix rather than
# simply retrying until the race happens to go the right way.
if $CLI --command="/subsystem=datasources/data-source=GlobalTradeDS:write-attribute(\
name=transaction-isolation,value=TRANSACTION_READ_COMMITTED)" >/dev/null 2>&1; then
  echo "    OK        transaction-isolation = TRANSACTION_READ_COMMITTED"
else
  echo "    FAILED    could not set transaction isolation"
fi

step "4. Reloading the server"
$CLI --command=":reload" >/dev/null 2>&1
for _ in $(seq 1 40); do
  sleep 2
  $CLI --command=":read-attribute(name=server-state)" 2>/dev/null | grep -q running && break
done
echo "    OK        server back up"

step "5. Deploying"
OUT=$($CLI --command="deploy $EAR --force" 2>&1)
if echo "$OUT" | grep -q "Deadlock"; then
  echo "    deadlock again - clearing the timer table and retrying once"
  mysql -u "$DB_USER" -p"$DB_PASS" globaltrade -e "DELETE FROM JBOSS_EJB_TIMER;" 2>/dev/null
  sleep 3
  OUT=$($CLI --command="deploy $EAR --force" 2>&1)
fi
echo "$OUT" | head -30

step "6. Result"
if $CLI --command="deployment-info --name=globaltrade-scm.ear" 2>/dev/null | grep -q "OK"; then
  echo "    DEPLOYED"
  TIMERS=$(mysql -N -u "$DB_USER" -p"$DB_PASS" globaltrade -e "SELECT COUNT(*) FROM JBOSS_EJB_TIMER;" 2>/dev/null)
  echo "    persistent timers registered: ${TIMERS:-?}"
  echo
  echo "    Open:  https://localhost:8443/globaltrade/"
  echo "    Login: admin@globaltrade.lk / Passw0rd!2026"
else
  echo "    NOT DEPLOYED - read the output above."
  echo "    Full detail:  tail -100 \"\$WILDFLY_HOME/standalone/log/server.log\""
  exit 1
fi
