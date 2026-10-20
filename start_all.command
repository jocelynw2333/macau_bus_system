#!/bin/zsh
set -e
cd "${0:A:h}"

if [[ ! -f .env ]]; then
  echo "缺少 .env。请先复制 .env.example 并填写数据库账号和密码。"
  exit 1
fi

mkdir -p data logs run
for name in collector importer web; do
  pid_file="run/${name}.pid"
  if [[ -f "$pid_file" ]]; then
    pid=$(<"$pid_file")
    case "$name" in
      collector) expected="collector.py" ;;
      importer) expected="importer.py" ;;
      web) expected="uvicorn" ;;
    esac
    command_line=$(ps -p "$pid" -o command= 2>/dev/null || true)
    if [[ "$command_line" == *"$expected"* ]]; then
      echo "$name 已在运行（PID $pid）。请勿重复启动。"
      exit 1
    fi
    if [[ -z "$command_line" ]] && lsof -t -p "$pid" >/dev/null 2>&1; then
      echo "无法核实 $name 的进程（PID $pid），但它仍存在。为避免重复启动，已保留 PID 记录。"
      exit 1
    fi
    rm -f "$pid_file"
  fi
done
source .venv/bin/activate

python importer.py --follow >> logs/importer.log 2>&1 &
echo $! > run/importer.pid

uvicorn api:app --host 127.0.0.1 --port 8000 >> logs/web.log 2>&1 &
echo $! > run/web.pid

python collector.py >> logs/collector.log 2>&1 &
echo $! > run/collector.pid
caffeinate -dimsu -w $(<run/collector.pid) >/dev/null 2>&1 &
echo $! > run/caffeinate.pid

echo "系统已启动。"
echo "网页：http://127.0.0.1:8000"
echo "采集日志：logs/collector.log"
echo "按 stop_all.command 可停止全部进程。"
