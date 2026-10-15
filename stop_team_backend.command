#!/bin/zsh
set -e
cd "${0:A:h}"
if [[ -f run/team_backend.pid ]]; then
  pid=$(<run/team_backend.pid)
  command_line=$(ps -p "$pid" -o command= 2>/dev/null || true)
  if [[ "$command_line" == *"team_backend/target/bus-0.0.1-SNAPSHOT.jar"* ]]; then
    kill "$pid"
    for attempt in {1..30}; do
      if ! kill -0 "$pid" 2>/dev/null; then break; fi
      sleep 0.2
    done
    if kill -0 "$pid" 2>/dev/null; then
      echo "Java 后端尚未停止，请检查 PID $pid。"
      exit 1
    fi
    echo "已停止组员 Java 后端（PID $pid）。Python 采集继续运行。"
  else
    echo "PID $pid 并非本项目 Java 后端，未停止。"
    exit 1
  fi
  rm -f run/team_backend.pid
fi
