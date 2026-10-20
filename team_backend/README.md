# Java 后端

源码基于 [MacaoBusCrawler](https://github.com/ZGLH2S/MacaoBusCrawler) 的提交 `079a09ad3f91a25c5aae93a2ca6d914fce714c11`。需要 Java 21 和 MySQL 8，在项目根目录执行 `./start_team_backend.command`。

新增只读接口 `/busCrawler/api/team/health` 和 `/busCrawler/api/team/routes/51A/latest`。定时采集和启动刷新默认关闭，避免与 `collector.py` 重复采集；Java 停止时，`api.py` 会改用本地数据库查询。
