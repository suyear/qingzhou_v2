# 安全说明

## 密钥与凭证

| 项 | 说明 |
|----|------|
| `AES_KEY` | 凭证 Secret、OpenAPI AppSecret 等 AES-GCM 加密；长度 16/24/32 字节 |
| `JWT_SECRET` | 控制台登录 JWT HMAC |
| `LICENSE_SIGN_KEY` | 离线 License 签名；泄露则他人可伪造 License |
| 数据库/Redis 密码 | 仅环境变量，勿提交仓库 |

## 控制台鉴权

- 全部 `/api/**`（除登录/引导/健康检查）需 `Authorization: Bearer <token>`  
- 角色：`ADMIN` / `DEVELOPER` / `VIEWER`  
- VIEWER 仅可读业务数据；不可改凭证、发布、启停调度  
- License 过期后写操作被拦截（只读模式）

## 开放平台（对外）

- 路径 `/openapi/**`，HMAC 签名 + Nonce 防重放 + 可选 IP 白名单 + QPS  
- 与控制台登录无关  
- 可选 `X-Idempotency-Key`（8–128 位）。同一应用、同一路径、同一请求体在 `qingzhou.openapi.idempotency-ttl-seconds`（默认 24 小时）内回放同一响应；请求体不同返回 409。Nonce 每次仍须唯一  
- 对外 `data.errorMsg` 不包含节点名称，也不回传 SQL / 驱动原文。控制台执行记录仍保留原始失败原因  
- HTTP 节点默认拒绝回环、私网、链路本地、IPv4-mapped IPv6 与 ULA。仅当 `HTTP_ALLOW_PRIVATE=true` 时放行私网（链路本地与云元数据主机名始终拒绝）

## 审计

关键写操作写入 `qz_audit_log`（登录、发布、删凭证、重置 Secret、用户变更、License 导入等）。管理员可在「审计日志」查阅。

## Swagger

- `/swagger-ui.html`、`/api/v3/api-docs` 仅 **ADMIN** 可访问  

## 建议基线

1. 生产强制改所有默认密钥  
2. 控制台与 OpenAPI 分离域名 / 网关限流  
3. 定期轮换凭证与 App Secret  
4. 配置执行日志保留天数（系统设置）并做备份
