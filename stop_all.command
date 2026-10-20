#!/bin/zsh
cd "${0:A:h}"
for name in collector importer web caffeinate; do
  pid_file="run/${name}.pid"
  if [[ -f "$pid_file" ]]; then
    pid=$(<"$pid_file")
    case "$name" in
      collector) expected="collector.py" ;;
      importer) expected="importer.py" ;;
      web) expected="uvicorn" ;;
      caffeinate) expected="caffeinate" ;;
    esac
    command_line=$(ps -p "$pid" -o command= 2>/dev/null || true)
    if [[ "$command_line" == *"$expected"* ]]; then
      kill "$pid"
      echo "已停止 $name（PID $pid）"
    elif [[ -z "$command_line" ]] && lsof -t -p "$pid" >/dev/null 2>&1; then
      echo "无法核实 $name 的进程（PID $pid），已保留 PID 记录，请在本机终端检查。"
      continue
    fi
    rm -f "$pid_file"
  fi
done
