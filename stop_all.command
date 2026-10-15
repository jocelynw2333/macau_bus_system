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
    fi
    rm -f "$pid_file"
  fi
done
