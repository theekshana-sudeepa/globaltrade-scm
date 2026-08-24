#!/usr/bin/env bash
# Verify everything this project needs before you start. Read-only.
: "${WILDFLY_HOME:=$HOME/wildfly-31.0.1.Final}"
ok(){ printf "  OK    %s\n" "$1"; }
bad(){ printf "  FAIL  %s\n" "$1"; FAILED=1; }
FAILED=0
echo "Environment check"
echo

V=$(java -version 2>&1 | head -1)
case "$V" in *\"1[7-9].*|*\"2[0-9].*) ok "Java: $V";; *) bad "Java must be 17+, found: $V";; esac

command -v mvn >/dev/null && ok "Maven: $(mvn -v 2>/dev/null | head -1)" || bad "Maven not on PATH"
command -v mysql >/dev/null && ok "MySQL client: $(mysql --version)" || bad "mysql not on PATH (brew install mysql)"
[ -x "$WILDFLY_HOME/bin/standalone.sh" ] && ok "WildFly: $WILDFLY_HOME" || bad "WildFly not at $WILDFLY_HOME"

if nc -z localhost 3306 2>/dev/null; then ok "MySQL server is listening on 3306"; else bad "Nothing on port 3306 (brew services start mysql)"; fi
if nc -z localhost 9990 2>/dev/null; then ok "WildFly management port 9990 is up"; else echo "  ....  WildFly not running yet (that is fine before script 1)"; fi
if nc -z localhost 8443 2>/dev/null; then ok "HTTPS port 8443 is up"; else echo "  ....  8443 not up yet"; fi

echo
[ "$FAILED" = 1 ] && echo "Fix the FAIL lines above first." || echo "All good. Run ./run/1-start-wildfly.sh"
