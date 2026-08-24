#!/usr/bin/env bash
# Start WildFly on the FULL profile. Leave this running; open a new tab for the rest.
set -e
: "${WILDFLY_HOME:=$HOME/wildfly-31.0.1.Final}"
[ -x "$WILDFLY_HOME/bin/standalone.sh" ] || { echo "WildFly not found at $WILDFLY_HOME"; exit 1; }
echo "Starting WildFly from $WILDFLY_HOME (standalone-full.xml)"
exec "$WILDFLY_HOME/bin/standalone.sh" -c standalone-full.xml
