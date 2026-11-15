# Local IoT Mock

用于在没有真实 IoT 业务系统时验证分层工程：

- `mock_gateway.py`：gateway HTTP mock（登录/设备绑定）
- `mock_mqtt.py`：MQTT CONNECT/CONNACK + PUBLISH 记录
- Kafka 使用 `deploy/docker-compose.middleware.yml` 中的 `easytest-kafka`
- MySQL 使用 `easytest-mysql`

```bash
./examples/iot-mock/start.sh
docker exec -i easytest-mysql mysql -uroot -peasytest123 < examples/iot-mock/schema.sql
# 创建 iot 用户（如果不存在）
docker exec easytest-mysql mysql -uroot -peasytest123 -e "
  CREATE USER IF NOT EXISTS 'iot'@'%' IDENTIFIED BY 'iot';
  GRANT ALL PRIVILEGES ON iot_home.* TO 'iot'@'%'; FLUSH PRIVILEGES;"
```

工程默认配置见 `examples/iot-api-engineering-repo/src/main/resources/iot-test.yaml`。
