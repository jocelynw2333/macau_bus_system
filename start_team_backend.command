#!/bin/zsh
set -e
unsetopt BG_NICE
cd "${0:A:h}"

if [[ ! -f .env ]]; then
  echo "缺少 .env，无法连接现有 MySQL 数据库。"
  exit 1
fi
if [[ -x team_backend/.jdk/Contents/Home/bin/java ]]; then
  export JAVA_HOME="$PWD/team_backend/.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
elif ! command -v java >/dev/null 2>&1 || ! java -version >/dev/null 2>&1; then
  echo "请安装 Java 21，或将 JDK 解压到 team_backend/.jdk。"
  exit 1
fi
JAVA_BIN=$(command -v java)
export MAVEN_USER_HOME="$PWD/team_backend/.m2"

set -a
source .env
set +a
mkdir -p logs run
if lsof -nP -iTCP:11451 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "11451 端口已有服务在运行；请先执行 ./stop_team_backend.command。"
  exit 1
fi
if [[ -f run/team_backend.pid ]]; then
  old_pid=$(<run/team_backend.pid)
  if kill -0 "$old_pid" 2>/dev/null; then
    echo "组员 Java 后端已运行（PID $old_pid）。"
    exit 0
  fi
fi

echo "编译组员 Java 后端……"
./team_backend/mvnw -q -f team_backend/pom.xml -Dmaven.repo.local="$MAVEN_USER_HOME/repository" -DskipTests package
nohup "$JAVA_BIN" -jar team_backend/target/bus-0.0.1-SNAPSHOT.jar \
  >> logs/team_backend.log 2>&1 < /dev/null &
echo $! > run/team_backend.pid
for attempt in {1..40}; do
  if curl -fsS --max-time 2 http://127.0.0.1:11451/busCrawler/api/team/health >/dev/null 2>&1; then
    echo "Java 后端已启动：http://127.0.0.1:11451/busCrawler/api/team/health"
    exit 0
  fi
  sleep 0.5
done
echo "Java 后端未能在 20 秒内启动，请查看 logs/team_backend.log。"
exit 1
