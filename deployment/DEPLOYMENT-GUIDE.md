# GlobalTrade SCM — Deployment Guide (WildFly + MySQL)

Primary target: **WildFly 31+ (Jakarta EE 10 Full Platform)** with **MySQL 8**.
An alternate Payara/GlassFish path is documented in §8.

---

## 0. Prerequisites

| Component | Version | Check with |
|---|---|---|
| JDK | 17 or later | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| MySQL Server | 8.0+ | `mysql --version` |
| WildFly | 31.0.0.Final or later, **Full Platform** | `ls $WILDFLY_HOME/bin/standalone.sh` |

### 0.1 Installing MySQL on macOS

`zsh: command not found: mysql` means the MySQL **server** is not installed — MySQL
Workbench is only a GUI client and does not include one.

```bash
# Homebrew (install Homebrew first if you don't have it)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

brew install mysql
brew services start mysql
mysql_secure_installation          # set a root password

mysql --version                    # verify
```

If MySQL is installed but `mysql` is still not found, it is a PATH problem:

```bash
# Apple Silicon (Homebrew)
echo 'export PATH="/opt/homebrew/opt/mysql/bin:$PATH"' >> ~/.zshrc
# Intel Mac, or the official .dmg installer
echo 'export PATH="/usr/local/mysql/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

### 0.2 Installing WildFly

```bash
cd ~
curl -LO https://github.com/wildfly/wildfly/releases/download/31.0.1.Final/wildfly-31.0.1.Final.zip
unzip -q wildfly-31.0.1.Final.zip
export WILDFLY_HOME=~/wildfly-31.0.1.Final
echo "export WILDFLY_HOME=~/wildfly-31.0.1.Final" >> ~/.zshrc
```

---

## 1. Database

```bash
mysql -u root -p < database/01-schema.sql

mysql -u root -p -e "
  CREATE USER IF NOT EXISTS 'globaltrade'@'localhost' IDENTIFIED BY 'CHANGE_ME';
  GRANT SELECT, INSERT, UPDATE, DELETE ON globaltrade.* TO 'globaltrade'@'localhost';
  FLUSH PRIVILEGES;"

mysql -u root -p < database/02-seed.sql
```

`02-seed.sql` already contains real PBKDF2 hashes. Every seeded account uses the
password **`Passw0rd!2026`**. Note that the five stored hashes are all different
despite sharing one password — that is the per-user salt doing its job.

Verify:

```bash
mysql -u globaltrade -p globaltrade -e "
  SELECT email, LEFT(password_hash, 12) AS hash_prefix FROM app_user;
  SELECT tracking_number, status FROM shipment;"
```

> **Prefer MySQL Workbench?** File → Open SQL Script → `database/01-schema.sql` →
> click the ⚡ icon. Repeat for `02-seed.sql`. Workbench still needs a running
> MySQL **server** (§0.1).

---

## 2. WildFly configuration

Start the server on the **full** profile, then run the setup script.

```bash
$WILDFLY_HOME/bin/standalone.sh -c standalone-full.xml &

# add a management user the first time only
$WILDFLY_HOME/bin/add-user.sh          # ManagementRealm, e.g. admin / Admin#2026

export WILDFLY_HOME=~/wildfly-31.0.1.Final
DB_PASS=CHANGE_ME ./database/03-datasource-wildfly.sh
```

The script performs seven steps, each of which is worth understanding because
each one appears in the Technical Documentation:

1. **Registers the MySQL driver as a server-level module** — not bundled in the
   EAR, matching the `provided` scope pattern.
2. **Creates the JTA datasource** `java:/jdbc/globaltrade`, pool 20–100.
3. **Configures a database-backed EJB timer store** — this is what makes
   persistent automatic timers fire on *one* cluster node instead of all of them.
4. **Sets the transaction timeout** to 120 s.
5. **Disables integrated JASPI** on the `other` security domain.
6. **Pins the stateless EJB pool** at 200 rather than CPU auto-derivation.
7. **Reloads** the server.

> ### The one line people lose a day to
> ```
> /subsystem=undertow/application-security-domain=other:write-attribute(name=integrated-jaspi,value=false)
> ```
> Without it, WildFly's own JASPIC layer swallows the request before the custom
> `JwtAuthenticationMechanism` ever runs, and every protected endpoint returns
> 401 no matter how valid the token is. The setup script applies it for you.

### 2.1 JWT signing key — mandatory

`JwtUtil` throws at class-initialisation time if no key is present. This is
deliberate: an application that silently starts with a default signing key is
worse than one that refuses to start.

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect \
  --command="/system-property=gt.jwt.secret:add(value=$(openssl rand -hex 32))"
```

---

## 3. Build

```bash
mvn clean install            # full reactor build
mvn -T 1C clean install      # parallel — record the wall-clock delta for the report
```

Artefact: `globaltrade-ear/target/globaltrade-scm.ear`

Verify the packaging — this output *is* your evidence for the packaging marks:

```bash
unzip -l globaltrade-ear/target/globaltrade-scm.ear
# META-INF/application.xml
# globaltrade-ejb.jar
# globaltrade-web.war
# lib/lk.jiat.globaltrade-globaltrade-common-1.0.0.jar
# lib/lk.jiat.globaltrade-globaltrade-domain-1.0.0.jar
# lib/com.auth0-java-jwt-4.6.0.jar
```

---

## 4. Deploy

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect \
  --command="deploy globaltrade-ear/target/globaltrade-scm.ear --force"

$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="deployment-info"
```

Watch the boot log for the evidence you will screenshot:

```bash
tail -f $WILDFLY_HOME/standalone/log/server.log
```

Expect to see EJB JNDI bindings, the persistence unit starting, and — critically
— the automatic timers being created.

### Modular redeploy — the payoff of the split directory structure

```bash
mvn -pl globaltrade-ejb -am install
mvn -pl globaltrade-ear install
$WILDFLY_HOME/bin/jboss-cli.sh --connect \
  --command="deploy globaltrade-ear/target/globaltrade-scm.ear --force"
```

Time a full reactor build and a single-module rebuild. Both numbers belong in the
report: they are the concrete evidence that the brief's *"monolithic deployment
causing difficulties in updating individual supply chain modules"* has been solved.

---

## 5. Smoke test

```bash
BASE=http://localhost:8080/globaltrade/api

# Login
TOKEN=$(curl -s $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin@globaltrade.lk","password":"Passw0rd!2026"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
echo "$TOKEN"

# Timers registered?
curl -s -H "Authorization: Bearer $TOKEN" $BASE/admin/monitoring/timers

# Interceptor metrics accumulating?
curl -s -H "Authorization: Bearer $TOKEN" $BASE/admin/monitoring/metrics

# Shipment lookup (exercises SUPPORTS + programmatic authorization)
curl -s -H "Authorization: Bearer $TOKEN" $BASE/shipments/GTLKGB0000001

# Overdue detection (seed data contains one deliberately overdue shipment)
curl -s -H "Authorization: Bearer $TOKEN" $BASE/shipments/overdue

# No token → expect 401
curl -s -o /dev/null -w '%{http_code}\n' $BASE/shipments/GTLKGB0000001

# Vendor rep hitting an admin endpoint → expect 403
VTOKEN=$(curl -s $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"rep@acme-supplies.com","password":"Passw0rd!2026"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $VTOKEN" $BASE/admin/monitoring/metrics
```

> **TLS note.** `web.xml` declares `<transport-guarantee>CONFIDENTIAL</transport-guarantee>`,
> so on a fully configured server `/api/*` redirects to HTTPS on port 8443. For local
> testing either use `https://localhost:8443/...` with `curl -k`, or temporarily comment
> out that single `<security-constraint>` block. **Re-enable it before you submit**, and
> say in the report that you tested over plain HTTP locally — an honest note costs
> nothing and an undisclosed one costs credibility.

---

## 6. Run the tests

```bash
mvn test                                                        # all modules
mvn -pl globaltrade-ejb test -Dtest=ExceptionPerformanceTest    # LO6 evidence
mvn -pl globaltrade-ejb test -Dtest=InterceptorOverheadTest     # LO2 evidence
mvn -pl globaltrade-ejb test -Dtest=ShipmentStatusTransitionTest
mvn -pl globaltrade-ejb test -Dtest=TransactionAttributeSemanticsTest
```

`ExceptionPerformanceTest` and `InterceptorOverheadTest` print their measurements
to stdout. **Screenshot that terminal output** — it is the raw material for the
performance table in the Test Report.

---

## 7. Timer persistence proof (the single best piece of evidence you can capture)

```bash
# 1. Before
mysql -u globaltrade -p globaltrade -e "SELECT COUNT(*) FROM JBOSS_EJB_TIMER;"
curl -s -H "Authorization: Bearer $TOKEN" $BASE/admin/monitoring/timers   # screenshot

# 2. Kill the server hard
pkill -f 'wildfly.*standalone'

# 3. Restart
$WILDFLY_HOME/bin/standalone.sh -c standalone-full.xml &
sleep 45

# 4. After — persistent timers are back, non-persistent ones are gone
curl -s -H "Authorization: Bearer $TOKEN" $BASE/admin/monitoring/timers   # screenshot
```

`SHIPMENT_STATUS_POLL` and the other `persistent = true` timers survive.
`ROUTE_CACHE_REFRESH` (`persistent = false`) is re-created only because the
annotation re-registers it at deployment — it carries no state across the restart.
That contrast, screenshotted, is the timer-reliability evidence the rubric asks for.

---

## 8. Alternate target: Payara 6 / GlassFish 7

```bash
cp globaltrade-domain/src/main/resources/META-INF/alternate-configs/persistence-payara.xml \
   globaltrade-domain/src/main/resources/META-INF/persistence.xml
mvn clean install
ASADMIN=$PAYARA_HOME/bin/asadmin ./database/03-datasource-payara.sh
$PAYARA_HOME/bin/asadmin deploy --force=true globaltrade-ear/target/globaltrade-scm.ear
```

No Java source changes are required. Every `@PersistenceContext` in the codebase
references the persistence-unit name `GlobalTradePU`, which both configurations
preserve — the JPA portability guarantee in concrete form.

---

## 9. Rollback

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="undeploy globaltrade-scm.ear"
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command="deploy globaltrade-scm-PREVIOUS.ear"
```

Keep the previous EAR. For 24/7 global operations, deploy one cluster node at a
time behind the load balancer, verify health, then proceed — a rolling deployment,
so no maintenance window is required. Never use the `deployments/` hot-deploy
directory in production: it is non-atomic, unversioned and unauditable.

---

## 10. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `zsh: command not found: mysql` | MySQL server not installed (Workbench is only a client) | §0.1 |
| `IllegalStateException: GT_JWT_SECRET must be supplied` | Signing key absent — deliberate fail-fast | §2.1 |
| Every request returns 401 with a valid token | WildFly's integrated JASPI is bypassing the custom mechanism | §2, step 5 |
| `WFLYCTL0180: Services with missing dependencies … jdbc/globaltrade` | Datasource not created, or JNDI name mismatch | Re-run §2; confirm `java:/jdbc/globaltrade` |
| `Table 'globaltrade.shipment' doesn't exist` | Schema script not run | §1 |
| Timers never fire | Server started on `standalone.xml`, or timer datastore not configured | Use `-c standalone-full.xml`; re-run §2 step 3 |
| `Access denied for user 'globaltrade'@'localhost'` | Grants not applied | Re-run the `GRANT` in §1 |
