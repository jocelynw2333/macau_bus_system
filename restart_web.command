#!/bin/zsh
set -e
unsetopt BG_NICE
cd "${0:A:h}"

pid_file="run/web.pid"
if [[ -f "$pid_file" ]]; then
  pid=$(<"$pid_file")
  command_line=$(ps -p "$pid" -o command= 2>/dev/null || true)
  if [[ "$command_line" == *"uvicorn api:app"* ]]; then
    kill "$pid"
    for attempt in {1..20}; do
      if ! kill -0 "$pid" 2>/dev/null; then break; fi
      sleep 0.2
    done
    if kill -0 "$pid" 2>/dev/null; then
      echo "旧网页进程仍在运行，请检查 PID $pid。"
      exit 1
    fi
  fi
fi

mkdir -p logs run
source .venv/bin/activate
nohup uvicorn api:app --host 127.0.0.1 --port 8000 >> logs/web.log 2>&1 < /dev/null &
echo $! > "$pid_file"
sleep 1
if ! kill -0 $(<"$pid_file") 2>/dev/null; then
  echo "网页后端启动失败，请查看 logs/web.log。"
  exit 1
fi
echo "网页后端已重启：http://127.0.0.1:8000/。采集器和导入器未中断。"
