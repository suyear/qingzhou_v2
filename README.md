# 轻舟（qingzhou）

低代码集成调度中台：接口组件（HTTP / 企微 / 数据库脚本） → 工作流编排 → 试运行 → 定时调度 → 开放平台调用 → 执行记录。

- 前端：`web/`（Vue 3 + Element Plus + Vite）
- 后端：`server/`（Spring Boot 3 / Java 21）
- 数据库脚本：`sql/`

## 环境

- JDK 21、Maven 3.9+
- Node.js 20+（前端）
- MySQL 8.0、Redis

数据库与 Redis 通过环境变量覆盖，勿把真实密码提交进仓库：

```bash
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=qingzhou
export DB_USERNAME=root
export DB_PASSWORD=your-password
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export REDIS_PASSWORD=
export AES_KEY=qingzhou-aes256-secret-key-00001
```

## 数据库迁移

按文件名顺序执行：

1. **新库**：`sql/V1__init_schema.sql`（已含调度类型字段）→ `sql/V2__seed_wecom_components.sql` → `sql/V4__database_component.sql`
2. **旧库升级**（`qz_schedule_job` 还没有 `schedule_type` / `trigger_input` 等列）：再执行 `sql/V3__schedule_types.sql`  
   若 V1 已是当前版本，**不要重复执行 V3**，否则会因列已存在而失败。
3. **数据库组件**：已有库再执行 `sql/V4__database_component.sql`（拓宽组件 URL/方法字段，凭证类型增加 MYSQL）。

V3 补齐的能力：固定间隔 / 每天 / 每周 / 一次性调度，以及调度触发入参。

V4 补齐的能力：数据库脚本组件。在「凭证管理」新增 **MySQL 数据源**（密码 AES 加密），在「接口组件」选择 **数据库脚本**，用 `:userId` 这类命名参数写 SQL，即可像 HTTP 组件一样拖进工作流。

### 数据库组件怎么用

1. 凭证管理 → 新建 → 类型选「MySQL 数据源」，填写主机/端口/库名/用户名/密码，可测连通。
2. 接口组件 → 新建 →「数据库脚本」：选数据源、选只读或允许写入、编写 SQL。
3. 工作流设计器左侧组件库会出现该组件，拖入后绑定参数，试运行可在节点日志看到脱敏 SQL 与行数/预览。

**支持的 SQL（MVP）**

- 只读：`SELECT` / `WITH` / `SHOW` / `DESCRIBE` / `EXPLAIN`
- 写入：另加 `INSERT` / `UPDATE` / `DELETE` / `REPLACE`
- 参数：命名参数 `:userId`，执行时绑定为 PreparedStatement

**安全边界**

- 默认禁止一次执行多条语句
- 拦截 `INTO OUTFILE` / `LOAD_FILE` / `SLEEP` / DDL / `CALL` 等
- 只读组件不能跑写语句
- 组件超时作用于语句 `queryTimeout`；查询默认最多 200 行（可配，上限 2000）
- 日志记录 SQL 与参数摘要（password/secret/token 类参数名会脱敏），响应侧记行数或预览，不落库明文密码

**下次可增强**：PostgreSQL/SQL Server 等更多数据源、事务、存储过程、IN 列表展开、连接池监控。

## 启动

```bash
# 后端，默认 http://127.0.0.1:18080
cd server
mvn spring-boot:run

# 前端，默认 http://127.0.0.1:5173（/api 代理到 18080）
cd web
npm install
npm run dev
```

后端未启动时，控制台会提示无法连接服务，而不是把空列表当成「没有数据」。

## 编译注意

Java 21 下 `server/pom.xml` 已为 `maven-compiler-plugin` 配置 Lombok `annotationProcessorPaths`。若本地覆盖了 compiler 插件配置，请保留该段，否则会出现找不到 getter/setter 的编译错误。
