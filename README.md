# 澳门公交 51A 数据系统

持续采集 51A（方向 0）车辆状态，记录入站和出站，并根据历史数据估计下一班到站时间范围。

| 原文件 | 整合后的文件 | 功能变化 |
| --- | --- | --- |
| `bus_51a_live.py` | `collector.py`、`regex_extract.py` | 正则提取；格式不变；超时刷新 |
| `51A_live.jsonl` | `data/51A_live.jsonl` | 保存采集数据，由 `importer.py` 入库 |
| `bus_crawler(1).sql` | `team_schema.sql`、`schema_extension.sql` | 移除删表语句；新增历史与事件表 |
| `一键启动(2)` | `start_team_backend.command` | 改为源码构建并启动 |
| [MacaoBusCrawler 源码](https://github.com/ZGLH2S/MacaoBusCrawler) | `team_backend/` | 新增只读接口；关闭重复采集 |
| `index_bus_api_connected_amap(1).html` | `static/index.html`、`static/dashboard.js` | 接入本地数据、地图和到站估计 |

数据流：`collector.py` → `data/51A_live.jsonl` → `importer.py` → MySQL → `api.py` → 网页。Java 后端可选；运行时由 `api.py` 优先读取它的最新快照接口。

## 本地运行（macOS / Windows）

安装 Python 3.11/3.12、MySQL 8；下载或克隆项目后，在项目目录打开终端（Windows 使用「命令提示符 / CMD」）。先创建虚拟环境并复制配置文件：

macOS：

```bash
python3 -m venv .venv
source .venv/bin/activate
cp .env.example .env
chmod 600 .env
```

Windows（CMD）：

```bat
py -3 -m venv .venv
.venv\Scripts\activate.bat
copy .env.example .env
```

两种系统接着运行：

```text
python -m pip install -r requirements.txt
python -m playwright install chromium
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS bus_crawler CHARACTER SET utf8mb4;"
```

Windows 还需运行 `python -m pip install tzdata`，供到站估计使用澳门时区。

将 `.env` 中的 `DB_USER`、`DB_PASSWORD` 改为**本机**有权访问 `bus_crawler` 的 MySQL 账号和密码，然后导入表（需能在终端运行 `mysql` 命令）：

```text
mysql -u 你的账号 -p bus_crawler < team_schema.sql
mysql -u 你的账号 -p bus_crawler < schema_extension.sql
```

macOS 执行 `./start_all.command`；Windows 则在项目目录打开三个 CMD 窗口，分别执行 `.venv\Scripts\python.exe importer.py --follow`、`.venv\Scripts\python.exe -m uvicorn api:app --host 127.0.0.1 --port 8000`、`.venv\Scripts\python.exe collector.py`。打开 <http://127.0.0.1:8000/> 查看结果。macOS 用 `./stop_all.command` 停止；Windows 在三个窗口分别按 `Ctrl+C`。

Java 后端可选（需 Java 21）：macOS 执行 `./start_team_backend.command`；Windows 在 CMD 执行 `set "DB_USER=你的账号"`、`set "DB_PASSWORD=你的密码"`、`team_backend\mvnw.cmd -q -f team_backend\pom.xml -DskipTests package`，然后运行 `java -jar team_backend\target\bus-0.0.1-SNAPSHOT.jar`。地图需自行填写 `static/map-config.local.json`（复制 `static/map-config.example.json`）。密码和历史数据不会上传到 GitHub。

状态 `1` 表示入站，`0` 表示出站；记录时间是采样时间，到站范围是历史估计，不保证赶上具体车辆。长期采集需保持电脑开机、联网，并定期检查最新记录与备份数据。
