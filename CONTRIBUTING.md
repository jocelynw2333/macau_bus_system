# 团队协作

这个文件夹本身是独立 Git 仓库。Git 不会实时同步未保存的编辑；每位成员先在自己的电脑修改并提交，再推送到 GitHub，其他人拉取后才能看到。

1. 仓库创建后，每人用 VS Code 的“Clone Repository”克隆同一个 GitHub 地址。
2. 开始任务前先拉取主分支：`git switch main`、`git pull`。
3. 为自己的任务建分支，例如 `git switch -c feature/station-names`。
4. 修改后运行 `python -m unittest discover -s tests -v`，再用 VS Code Source Control 查看改动、提交并推送分支。
5. 在 GitHub 发 Pull Request，请另一位成员检查，再合并到 `main`。随后大家再次 `git pull`。

站名负责人主要编辑/更新 `station_info` 的数据来源和页面；数据库负责人维护 SQL；分析负责人可新增统计图；后端负责人若要把 Java 后端纳入仓库，请先提供源代码并移除硬编码密码。

不要提交 `.env`、`data/`、`logs/`、`run/`、`.venv/`、数据库导出、JAR 或 ZIP。需要共享样例时，另建少量脱敏测试数据。
