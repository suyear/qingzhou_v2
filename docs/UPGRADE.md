# 升级指南

按文件名顺序执行 `sql/` 下尚未应用的脚本。**不要重复执行**会 `ALTER` 已存在列的脚本。

| 脚本 | 说明 |
|------|------|
| `V1__init_schema.sql` | 新库基线 |
| `V2__seed_wecom_components.sql` | 企微预置组件 |
| `V3__schedule_types.sql` | 仅旧库补调度类型列；若 V1 已含这些列则跳过 |
| `V4__database_component.sql` | 数据库组件字段拓宽 + MYSQL 凭证 |
| `V5__credential_auth_types.sql` | HTTP_AUTH / DATABASE 鉴权扩展 |
| `V6__product_foundation.sql` | 用户/角色、审计、License、系统设置 |

## 升级到 V6（产品化）

```bash
mysql -h$DB_HOST -u$DB_USERNAME -p$DB_PASSWORD $DB_NAME < sql/V6__product_foundation.sql
```

然后重启后端。首次访问会提示「创建管理员」（若库中尚无用户）。

## 配置变更

新增环境变量（见 `.env.example`）：

- `JWT_SECRET` — 控制台登录 Token 签名  
- `LICENSE_SIGN_KEY` — License HMAC 签发密钥（厂商与客户部署共用约定）  
- `OPENAPI_PUBLIC_BASE_URL` — 对外文档展示的 API 根地址  

`application.yml` 中 DB/Redis 默认密码已清空，必须通过环境变量注入。
