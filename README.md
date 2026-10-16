# 澳门公交 51A 项目

本项目持续采集澳门交通事务局 51A（方向 0）的车辆状态，保存历史数据，识别入站/出站事件，并根据近 14 天同站点的历史到车间隔估计下一班的时间范围。

```text
公交网页 → collector.py → data/51A_live.jsonl → importer.py → MySQL
                                                   ↓
                                  api.py → static/index.html
                                     ↑
                    team_backend/（可选 Java 只读最新快照接口）
```

## 组员的工作

| 负责的内容 | 在本项目中的连接 |
| --- | --- | --- |
| 51A 动态采集 | `collector.py` 和正则提取模块 `regex_extract.py` |
| 站点数据、前端 | 她的仪表盘已整合为 `static/index.html` 和 `static/dashboard.js`；`station_info` 存站名 |
| MySQL 数据库 | `team_schema.sql` 来自其原 SQL；`schema_extension.sql` 增加历史表 |
| 可视化 | 使用 `bus_station_event`、`bus_observation` 或查询 API |
| Java 后端 | [GitHub 源码](https://github.com/ZGLH2S/MacaoBusCrawler) 已放入 `team_backend/`；提供兼容的只读最新快照接口，见 [团队整合说明](docs/TEAM_INTEGRATION.md) |

原始 `bus_crawler(1).sql` 含有删除表的语句，采集开始后不要再执行它。仓库的 `team_schema.sql` 删除了这些语句，适合新成员首次建库。`importer.py` 将入站/出站事件同步写到组员的 `station_bus_info`，`sync_catalog.py` 把 51A 站点顺序写到组员的线路/站点表。

Y 的原页面请求 A 的 Java 后端 `localhost:4399/busCrawler`。整合后的页面沿用 Y 的布局，改从本项目的只读 `/api/routes/51A` 接口获取实时快照、所选站点事件和到站范围，不会重复触发公交官网抓取。原页面把状态 0/1 的入出站含义写反；现已按 K 的 SQL 定义统一为 1 入站、0 出站。原始 `passengerFlow` 含义尚不确定，页面只展示字段原值，不作为客流人数。

地图在本机使用 `static/map-config.local.json`，它已被 Git 忽略。新成员可以复制 `static/map-config.example.json`，填入自己的高德 JS API Key 和安全密钥。地图上的蓝点是官方 51A 方向 0 站点；深色车标表示入站，固定在站点坐标；出站时高亮当前站到下一站的道路。仅在采样状态连续、已识别出站时间、近 14 天同路段至少有 5 组完整行程且采样仍新鲜时，才显示橙色“推算位置”。这是按历史中位行程时间沿道路长度推算，不是车辆 GPS；超过该路段历史 90% 行程时间或缺数据时，仅显示整段道路，不猜具体位置。“线路”按钮可切换到站序示意。未配置地图或加载失败时，仍可查看线路示意、车辆和站点数据。

站点和道路来自[澳门政府开放数据平台的巴士路线资料](https://data.gov.mo/Detail?id=e7b2e84d-3333-42f0-b676-64ce95306f0d)，仓库内的 `static/route-51a.geo.json` 已匹配本项目方向 0 的 21 个站点。生成时按[澳门测绘暨地籍局坐标说明](https://www.dscc.gov.mo/files/geographical_geodetic_control/ENG/Macaucoord_2009_web_EN_v201702.pdf)把 Macao Grid (EPSG:8433) 转为 WGS84，再转为高德使用的 GCJ-02；不要把官方 SHP 的方格网坐标直接当经纬度。路线或站点改变时，先核对站序，再从官方页面下载 `ExportShapeFile.zip`，在项目虚拟环境中安装可选构建依赖 `pyshp pyproj xlrd`，运行 `python tools/build_route_geo.py /下载路径/ExportShapeFile.zip`，并检查地图。压缩包无需提交 Git。网页文件更新后刷新浏览器即可；`api.py` 更新后在项目文件夹运行 `./restart_web.command`，采集器无需重启。

## 已在这台 Mac 上完成的迁移

本机数据库已创建事件表，并从旧观测回填事件；不需要重复操作。代码更新要在程序重启后生效。先在手动运行采集器的终端按 Control+C，再在本目录执行：

```bash
./stop_all.command
./start_all.command
```

不要让两个采集器同时写同一个 JSONL。浏览器打开 <http://127.0.0.1:8000/>，确认最新采集时间持续变化；选择站点可看事件与估计范围。`./stop_all.command` 只会停止由启动脚本记录的进程。

## 接入的 Java 后端

Java 源码已放进 `team_backend/`，需要 Java 21 才能运行。它默认只读现有 MySQL 历史数据，**不会启动第二个 51A 定时采集器**。确认 `.env` 中仍是当前数据库账号后，在项目根目录运行 `./start_team_backend.command`；Java 服务的健康接口是 <http://127.0.0.1:11451/busCrawler/api/team/health>。在 `.env` 增加 `TEAM_BACKEND_URL=http://127.0.0.1:11451/busCrawler` 后，运行 `./restart_web.command`，网页的“最新快照”查询将通过本项目 API 优先使用 Java；Java 临时停止时会自动使用原 Python 查询，不会影响采集。访问 <http://127.0.0.1:8000/api/backend/status> 可确认实际使用哪个后端。本机若已有旧版 Java 和网页进程，运行 `./finish_backend_switch.command` 即可依次重启这两个服务，采集器和导入器保持运行。

Java 后端直接提供 `GET /busCrawler/api/team/routes/51A/latest`，返回与原 `/api/routes/51A/latest` 相同的字段；事件、历史和到站估计继续由 Python API 提供。原有 Java `POST .../refresh` 是额外采集和写库入口，日常网页不会调用，以免两套采集流程互相干扰。单独停止 Java 用 `./stop_team_backend.command`，不会停 Python 采集。

如果旧导入器一直运行到重启前，为补上迁移与重启之间的事件，重启后再执行一次 `mysql -u bus_app -p bus_crawler < backfill_events.sql`。该脚本可重复运行。

## 新成员首次安装

需要 Python 3.11/3.12 和 MySQL 8。克隆团队仓库后在 VS Code 中打开项目文件夹，运行：

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
playwright install chromium
cp .env.example .env
```

编辑 `.env` 填写自己的数据库账号和密码，在 MySQL 中创建 `bus_crawler` 数据库，然后执行：

```bash
mysql -u 你的数据库账号 -p bus_crawler < team_schema.sql
mysql -u 你的数据库账号 -p bus_crawler < schema_extension.sql
./start_all.command
```

已有旧 `bus_observation` 数据但没有事件记录时，运行 `mysql -u 你的数据库账号 -p bus_crawler < backfill_events.sql`，再执行 `python sync_catalog.py`。VS Code 的“运行和调试”里也有三个单独程序的入口。

在 VS Code 中使用“Python: Select Interpreter”选择本项目的 `.venv`，再使用“运行和调试”。Mac 和 Windows 的虚拟环境可执行文件路径不同，所以仓库不固定解释器路径。

## 时间含义

SQL 把状态 `1` 写为入站，`0` 写为出站。事件时间是第一次采样看到该状态的时间，不等于车门开关的精确时刻；`first_seen` 表示上一状态未被连续观测。到站范围用近 14 天同站点、相近时段历史间隔的 10%—90% 分位计算，样本少或最新记录过旧时不显示。站间行程时间另按同一车辆在相邻站的出站→入站事件配对计算，供地图推算使用，不等于到站预测。页面建议在估计最早时间前 2 分钟到站，不能保证赶上具体车辆。

持续两周采集时保持 Mac 开机、联网、登录且不要合盖；每天检查网页最新采集时间，并备份 JSONL 和数据库。

## Git 协作

Git 只跟踪代码、SQL、网页和说明；`.gitignore` 排除了密码、采集数据、日志、虚拟环境、JAR 和 ZIP。每人建立自己的分支，修改后运行 `python -m unittest discover -s tests -v`，再发 Pull Request。项目完成并确定团队 GitHub 仓库后设置 `origin` 并推送 `main`。
