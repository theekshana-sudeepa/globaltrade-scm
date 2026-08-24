#!/usr/bin/env bash
# Payara 6 / GlassFish 7 JDBC configuration.
# Pool sizing rationale (documented in the Technical Documentation):
#   steadypoolsize 20  - matches typical idle load
#   maxpoolsize   100  - raised from the 32 default because REQUIRES_NEW on the
#                        audit path holds a SECOND connection concurrently; at
#                        200 concurrent shipment operations the 32-connection
#                        default produced pool-timeout exceptions under load test.
#   statement-cache-size 100 - prepared-statement reuse for the hot timer queries
set -e
ASADMIN=${ASADMIN:-asadmin}

$ASADMIN create-jdbc-connection-pool \
  --datasourceclassname com.mysql.cj.jdbc.MysqlDataSource \
  --restype javax.sql.DataSource \
  --steadypoolsize 20 --maxpoolsize 100 \
  --statementcachesize 100 \
  --isconnectvalidatereq=true --validationmethod=table --validationtable=DUAL \
  --property "user=globaltrade:password=CHANGE_ME:serverName=localhost:portNumber=3306:databaseName=globaltrade:useSSL=false:allowPublicKeyRetrieval=true:serverTimezone=UTC:rewriteBatchedStatements=true" \
  GlobalTradePool

$ASADMIN create-jdbc-resource --connectionpoolid GlobalTradePool jdbc/globaltrade

# Transaction timeout: 120 s. Long enough for the month-end batch, short enough
# that a stuck carrier call cannot hold a transaction open indefinitely.
$ASADMIN set server.transaction-service.timeout-in-seconds=120

# Persistent EJB timers backed by the same database, so a cluster elects a
# single node per automatic timer instead of all nodes firing.
$ASADMIN set configs.config.server-config.ejb-container.ejb-timer-service.timer-datasource=jdbc/globaltrade

$ASADMIN ping-connection-pool GlobalTradePool
echo "Datasource configured."
