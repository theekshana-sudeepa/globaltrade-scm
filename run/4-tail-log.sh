#!/usr/bin/env bash
set -e
: "${WILDFLY_HOME:=$HOME/wildfly-31.0.1.Final}"
exec tail -f "$WILDFLY_HOME/standalone/log/server.log"
