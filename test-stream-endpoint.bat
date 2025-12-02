@echo off
REM 测试流式查询端点 (Windows版本)
REM 使用方法: test-stream-endpoint.bat

echo 🧪 测试流式查询端点 POST /query/stream
echo ==========================================
echo.

REM 服务器地址
set SERVER=http://localhost:8080

echo 📤 发送请求...
echo URL: %SERVER%/query/stream
echo.
echo 📥 接收流式响应:
echo ----------------------------------------

REM 使用curl发送请求并接收SSE流
curl -N -X POST "%SERVER%/query/stream" ^
  -H "Content-Type: application/json" ^
  -H "Accept: text/event-stream" ^
  -d "{\"question\":\"查询所有员工信息\",\"windowId\":\"test-window\",\"sessionId\":null}"

echo.
echo ----------------------------------------
echo ✅ 测试完成
pause
