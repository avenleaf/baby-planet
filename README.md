# 小芽星球

面向三岁儿童家长的 React H5 网站。四个领域角色共享模型服务，分别使用英语、数学、国学、思维活动设计指令。家长可输入兴趣或粘贴资料文本，生成活动并记录完成情况。生成的教案保存在 MySQL，完成记录保存在当前浏览器。

## 本地运行

安装 Docker Compose 后，将 .env.example 复制为 .env，填写数据库密码 DB_PASSWORD 和服务端密钥 DEEPSEEK_API_KEY。密钥可从 [DeepSeek 开放平台](https://platform.deepseek.com/) 获取。原配置中的 AI_API_KEY、AI_ENDPOINT 和 AI_MODEL 已替换，升级时请填写 DeepSeek 官方密钥。

四个角色通过 Spring Boot 调用 DeepSeek 官方聊天补全接口，默认模型为 deepseek-flash。可用 DEEPSEEK_MODEL 指定官方支持 JSON 输出的其他对话模型。网页仅向本站 /api/activities 提交资料。未配置密钥时可体验内置活动。

```sh
docker compose up --build -d
```

访问 http://localhost:8080 。首次启动会创建 MySQL 8.0 数据卷与教案表。停止服务使用 docker compose down。

## 前端开发

运行 npm install 和 npm run dev。Vite 将 /api 转发至本机 8080。npm run build 生成 dist。Docker 构建会把前端产物嵌入 Spring Boot，使网页与 API 同源。

## 后端数据库集成测试

使用 Java 17、Maven 和独立的 MySQL 8 测试库。在运行 Maven 的终端设置 `DB_URL`、`DB_USER`、`DB_PASSWORD`，再设置 `PLANET_TEST_DB=true`。数据库用户需要在该测试库建表和读写数据的权限。普通构建默认跳过这项数据库测试。

```sh
mvn -f backend/pom.xml package
```

测试通过 Spring MVC 提交请求，验证非法时长被拒绝、符合格式的模型结果经 MyBatis-Plus 写入 MySQL，以及读取的中文步骤与原文一致。模型传输使用测试替身，无需模型密钥；测试写入在事务结束时回滚。

## 二期数据库与家长账号测试

新建 MySQL 数据库后，启动应用即可由 Flyway 创建一期教案表及二期账号表。已存在一期教案表的数据库，先备份并核对库名和表结构，再为首次升级设置 `DB_BASELINE_ON_MIGRATE=true`。确认 `flyway_schema_history` 记录了全部迁移后，撤销该设置。迁移账号应具备创建表和维护迁移记录所需权限，可通过 `SPRING_FLYWAY_USER` 与 `SPRING_FLYWAY_PASSWORD` 单独配置。

如需运行 `SchemaMigrationTest`，额外设置 `PLANET_TEST_FRESH_URL`、`PLANET_TEST_LEGACY_URL`，分别指向两个独立、可写的空库。测试会在后一数据库创建一期样例并执行升级。每次重跑使用新的测试库。

家长账号公开上线前需使用 HTTPS，并设置 `ACCOUNT_SECURE_COOKIE=true`。重置邮件通过 `SPRING_MAIL_HOST`、`SPRING_MAIL_PORT`、`SPRING_MAIL_USERNAME`、`SPRING_MAIL_PASSWORD` 和邮件服务所需的 SMTP TLS 配置接入。设置 `PLANET_ACCOUNT_RESET_ORIGIN` 为站点 HTTPS 根地址，`PLANET_ACCOUNT_MAIL_FROM` 为发件地址。账号提交限额默认按连接来源生效，反向代理部署时需由可信代理提供真实客户端地址并验收。
