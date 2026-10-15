# A 组员的 Java 后端

本目录来自 [MacaoBusCrawler](https://github.com/ZGLH2S/MacaoBusCrawler)，检出提交 `079a09ad3f91a25c5aae93a2ca6d914fce714c11`。为与本项目配合，新增只读的 51A 查询接口；数据库连接改用环境变量；默认关闭 Java 自带的定时采集和启动刷新，避免与正在运行的 Python 采集器重复写库。原仓库的 `application-dev.yml` 含本机数据库连接配置，因此没有复制。

Java 21、MySQL 8 和 Maven Wrapper 是运行要求。项目根目录的 `start_team_backend.command` 会加载本机 `.env`，编译并启动 Java 服务；`stop_team_backend.command` 单独停止它。`TEAM_BACKEND_COLLECTOR_ENABLED` 默认为 `false`，如需改为由 Java 负责采集，必须先停用 Python 采集/导入并重新规划数据写入，不能只打开开关。

Java 直接接口：

- `GET http://127.0.0.1:11451/busCrawler/api/team/health`
- `GET http://127.0.0.1:11451/busCrawler/api/team/routes/51A/latest`

原有的 `POST /company/refresh`、`/route/refresh`、`/station/refresh`、`/bus/refresh` 仍在，但会抓取外部数据并写数据库，正常运行时不由前端调用。Python 网页服务设有 `TEAM_BACKEND_URL=http://127.0.0.1:11451/busCrawler` 时，会优先从 Java 获取最新快照；Java 不可用时自动继续从相同 MySQL 数据库查询。入出站事件、历史记录、站间时间和到站范围仍由 Python 查询接口提供。
