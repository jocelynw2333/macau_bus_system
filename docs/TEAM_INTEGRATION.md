# 组员文件的作用与交接

## K 的数据库文件

`bus_crawler(1).sql` 定义 `bus_info`（车辆）、`company_info`（公司）、`route_info`（线路）、`route_station_info`（线路站序）、`station_info`（站名）、`station_bus_info`（站点车辆状态）。原件是 Navicat 导出，含 `DROP TABLE`；仓库的 `team_schema.sql` 保留字段但移除删除语句。

新增的 `collection_snapshot` 保存完整原始响应，`bus_observation` 保存每次采样，`bus_station_event` 保存推断的入站/出站事件。导入器把事件同步到 K 的 `station_bus_info`。站点代码和顺序已同步到 K 的表；站名仍留给 Y 补充。

## A 的 Java 源码和接口

[A 的 GitHub 仓库](https://github.com/ZGLH2S/MacaoBusCrawler)现在提供源码，本项目把指定提交放入 `team_backend/`。原代码是 Java 21 Spring Boot 服务，原有 `POST /company/refresh`、`/route/refresh`、`/station/refresh`、`/bus/refresh` 会抓取并写入 K 的基础表；它们不是前端读取历史数据的接口。本地配置使用 11451 端口和 `/busCrawler` 前缀。源码里原有的 `application-dev.yml` 带本机数据库设置，没有复制。

为了与现有两周采集流程兼容，Java 的定时采集和开机刷新默认关闭，并新增 `GET /api/team/health` 和 `GET /api/team/routes/51A/latest`。后者从同一 MySQL 的 `collection_snapshot`、`bus_observation`、`bus_station_event` 和 `station_info` 读数据，字段与 Python 的最新快照接口相同。`api.py` 在设置 `TEAM_BACKEND_URL` 且 Java 正常运行时优先使用该接口；否则保持原查询。事件、站间时间与到站范围暂由 Python 继续计算。不要在 Python 采集运行时开启 Java 定时采集，否则会重复请求官网并写入组员表。

## Y 的前端页面

Y 提供的 `index_bus_api_connected_amap(1).html` 已作为仪表盘布局整合进 `static/index.html`，交互代码在 `static/dashboard.js`。原页面依赖 A 的 `POST /route/refresh`、`/station/refresh`、`/bus/refresh`，在当前系统中会重复请求外部数据；整合版改用 Python 后端的只读查询接口，保留地图、车辆卡片、站点表和线路信息，并加入所选站点的事件与到站时间范围。页面只展示方向 0 的 51A 数据。

地图配置在忽略 Git 的 `static/map-config.local.json`，仓库只提交不含密钥的示例。`static/route-51a.geo.json` 从澳门官方开放数据生成，包含 51A 方向 0 的 21 个站点坐标和相邻站间道路折线；`tools/build_route_geo.py` 可在官方数据更新时重新生成。页面入站车标使用站点坐标，出站时显示所在路段；`/api/routes/51A/segment-times` 只按同车相邻站事件统计近 14 天行程时间，有可靠出站时间与足够样本时才显示推算车标。坐标统一为高德 GCJ-02；推算位置不是真实 GPS。若官方站序与当前采集快照不符，地图不显示定位，以免误标。

## 交接字段

- JSONL 一行含 `collected_at`、`route`、`direction`、`response`；站点在 `response.data.routeInfo[]`，其车辆在 `busInfo[]`。
- `sta_code` 是各组员之间的站点键。Y 可更新 `station_info.sta_zh_tw` / `sta_zh_cn`，前端会自动显示站名。
- `bus_code` 用于追踪车辆，`observed_at` 是本机收到响应的采样时间。
- `status=1` / `status=0` 的入出站含义来自 K 的 SQL 注释，仍需与官方接口核实。
- 查询接口：`/api/routes/51A/latest`（Java 可接管，出站记录附有 `departed_at`）、`/segment-times`、`/history`、`/stations/{sta_code}/events`、`/arrival-window`。`/api/backend/status` 标识最新快照实际由哪个后端提供。
