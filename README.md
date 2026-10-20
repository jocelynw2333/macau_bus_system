# 澳门公交 51A 数据系统

持续采集 51A（方向 0）车辆状态，记录入站和出站，并根据历史数据估计下一班到站时间范围。

| 原文件 | 整合后的文件 | 功能变化 |
| --- | --- | --- |
| `bus_51a_live.py` | `collector.py`、`regex_extract.py` | 正则提取；格式不变；超时刷新 |
| `51A_live.jsonl` | `data/51A_live.jsonl` | 保存采集数据，由 `importer.py` 入库 |
| `bus_crawler(1).sql` | `team_schema.sql`、`schema_extension.sql` | 移除删表语句；新增历史与事件表 |
| `一键启动(2)/` | `start_team_backend.command` | 改为源码构建并启动 |
| [MacaoBusCrawler 源码](https://github.com/ZGLH2S/MacaoBusCrawler) | `team_backend/` | 新增只读接口；关闭重复采集 |
| `index_bus_api_connected_amap(1).html` | `static/index.html`、`static/dashboard.js` | 接入本地数据、地图和到站估计 |

数据流：`collector.py` → `data/51A_live.jsonl` → `importer.py` → MySQL → `api.py` → 网页。Java 后端可选；运行时由 `api.py` 优先读取它的最新快照接口。

## 本地运行（macOS）

需要 Python 3.11/3.12、MySQL 8；使用 Java 后端还需 Java 21。先在 MySQL 创建 `bus_crawler` 数据库，然后在项目目录运行：

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
playwright install chromium
cp .env.example .env
chmod 600 .env
```

填写 `.env` 中的数据库账号和密码，再运行：

```bash
mysql -u 你的账号 -p bus_crawler < team_schema.sql
mysql -u 你的账号 -p bus_crawler < schema_extension.sql
./start_all.command
```

打开 <http://127.0.0.1:8000/>。需要 Java 后端时执行 `./start_team_backend.command`；停止采集执行 `./stop_all.command`。地图需自行填写 `static/map-config.local.json`（可复制 `static/map-config.example.json`）。密码和历史数据不会上传到 GitHub。

状态 `1` 表示入站，`0` 表示出站；记录时间是采样时间，到站范围是历史估计，不保证赶上具体车辆。长期采集需保持电脑开机、联网，并定期检查最新记录与备份数据。
