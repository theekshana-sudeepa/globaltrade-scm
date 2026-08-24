#!/usr/bin/env bash
# Datasource, timer datastore, transaction timeout, JASPI fix, EJB pool, JWT key.
# Run this AFTER the server is up (script 1) — it connects to the running server.
set -e
: "${WILDFLY_HOME:=$HOME/wildfly-31.0.1.Final}"
: "${DB_PASS:=CHANGE_ME}"
cd "$(dirname "$0")/.."

echo "==> configuring WildFly"
DB_PASS="$DB_PASS" ./database/03-datasource-wildfly.sh

echo "==> setting the JWT signing key"
"$WILDFLY_HOME/bin/jboss-cli.sh" --connect \
  --command="/system-property=gt.jwt.secret:add(value=$(openssl rand -hex 32))" \
  || echo "   (key already set - continuing)"

echo
echo "Done. Next: ./run/3-deploy.sh"
