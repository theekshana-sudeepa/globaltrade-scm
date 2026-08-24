#!/usr/bin/env bash
# Build if needed, then deploy the EAR and show what is deployed.
set -e
: "${WILDFLY_HOME:=$HOME/wildfly-31.0.1.Final}"
cd "$(dirname "$0")/.."

EAR="globaltrade-ear/target/globaltrade-scm.ear"
if [ ! -f "$EAR" ]; then
  echo "==> $EAR not found, building first"
  mvn -q clean install
fi

echo "==> deploying"
"$WILDFLY_HOME/bin/jboss-cli.sh" --connect --command="deploy $EAR --force"
"$WILDFLY_HOME/bin/jboss-cli.sh" --connect --command="deployment-info"

echo
echo "Now open:  https://localhost:8443/globaltrade/"
echo "Sign in :  admin@globaltrade.lk / Passw0rd!2026"
