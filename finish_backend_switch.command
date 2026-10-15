#!/bin/zsh
set -e
cd "${0:A:h}"

./stop_team_backend.command
./start_team_backend.command
./restart_web.command

# The temporary verification page can be closed after port 8000 is healthy.
if curl -fsS --max-time 3 http://127.0.0.1:8000/api/backend/status | grep -q '"team-java"'; then
  temporary_pid=$(lsof -tiTCP:8002 -sTCP:LISTEN 2>/dev/null | head -1)
  if [[ -n "$temporary_pid" ]]; then
    command_line=$(ps -p "$temporary_pid" -o command= 2>/dev/null || true)
    if [[ "$command_line" == *"uvicorn api:app"* && "$command_line" == *"--port 8002"* ]]; then
      kill "$temporary_pid"
    fi
  fi
else
  echo "新网页已启动，但尚未确认连接到 Java；请检查两个服务的日志。"
  exit 1
fi

echo "已切到组员 Java 后端。验证地址：http://127.0.0.1:8000/api/backend/status"
