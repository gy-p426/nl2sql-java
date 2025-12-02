#!/bin/bash

# 测试流式查询端点
# 使用方法: ./test-stream-endpoint.sh

echo "🧪 测试流式查询端点 POST /query/stream"
echo "=========================================="
echo ""

# 服务器地址
SERVER="http://localhost:8080"

# 测试数据
REQUEST_DATA='{
  "question": "查询所有员工信息",
  "windowId": "test-window",
  "sessionId": null
}'

echo "📤 发送请求..."
echo "URL: $SERVER/query/stream"
echo "数据: $REQUEST_DATA"
echo ""
echo "📥 接收流式响应:"
echo "----------------------------------------"

# 使用curl发送请求并接收SSE流
curl -N -X POST "$SERVER/query/stream" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d "$REQUEST_DATA"

echo ""
echo "----------------------------------------"
echo "✅ 测试完成"
