#!/bin/sh

CONNECT_URL="http://connect-ticket:8083"

echo "Waiting for Kafka Connect to be available at ${CONNECT_URL}..."
while ! curl -s -o /dev/null ${CONNECT_URL}/; do
  echo "Kafka Connect is not available yet. Retrying in 5 seconds..."
  sleep 5
done

echo "Kafka Connect is up!"

CONNECTOR_NAME="order-outbox-connector"
CONNECTOR_URL="${CONNECT_URL}/connectors/${CONNECTOR_NAME}"

# 使用 http_code 检查 connector 是否存在 (200 表示存在)
http_code=$(curl -s -o /dev/null -w "%{http_code}" ${CONNECTOR_URL})

if [ "$http_code" -eq 200 ]; then
  echo "Connector '${CONNECTOR_NAME}' already exists."
  # 可选：可以进一步检查状态并决定是否重启任务
  # curl -s "${CONNECTOR_URL}/status"
else
  echo "Connector '${CONNECTOR_NAME}' not found. Creating..."
  # Use a variable to store the response
  response=$(curl -s -w "\n%{http_code}" -X POST -H "Content-Type: application/json" --data "@/config/register-mysql-connector.json" "${CONNECT_URL}/../connectors")

  # Extract the body and the http_code
  http_code=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')

  echo "Response Body: $body"
  echo "HTTP Code: $http_code"

  # Check if the HTTP status code is in the 2xx range (success)
  if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
    echo "Connector configuration submitted successfully."
    exit 0
  else
    echo "ERROR: Failed to submit connector configuration."
    exit 1 # Exit with an error code
  fi
fi