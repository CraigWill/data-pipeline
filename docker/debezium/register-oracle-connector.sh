#!/bin/bash
# 注册 Debezium Oracle Connector

set -e

echo "等待 Debezium Connect 启动..."
until curl -s http://localhost:8083/ > /dev/null; do
    echo "等待中..."
    sleep 5
done

echo "Debezium Connect 已启动，注册 Oracle Connector..."

# 注册 Oracle Connector
curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
  http://localhost:8083/connectors/ -d '{
  "name": "oracle-connector",
  "config": {
    "connector.class": "io.debezium.connector.oracle.OracleConnector",
    "tasks.max": "1",
    "database.hostname": "'"${DATABASE_HOST}"'",
    "database.port": "'"${DATABASE_PORT}"'",
    "database.user": "'"${DATABASE_USERNAME}"'",
    "database.password": "'"${DATABASE_PASSWORD}"'",
    "database.dbname": "'"${DATABASE_SID}"'",
    "database.server.name": "oracle_cdc_server",
    "table.include.list": "'"${DATABASE_SCHEMA}.${DATABASE_TABLES}"'",
    "database.history.kafka.bootstrap.servers": "kafka:9092",
    "database.history.kafka.topic": "schema-changes.oracle",
    "database.connection.adapter": "logminer",
    "log.mining.strategy": "online_catalog",
    "log.mining.continuous.mine": "true",
    "snapshot.mode": "schema_only",
    "database.autocommit": "false",
    "decimal.handling.mode": "string",
    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false",
    "transforms": "unwrap",
    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.unwrap.drop.tombstones": "false",
    "transforms.unwrap.delete.handling.mode": "rewrite"
  }
}'

echo ""
echo "Oracle Connector 注册完成！"
echo "检查 Connector 状态："
curl -s http://localhost:8083/connectors/oracle-connector/status | python3 -m json.tool
