# getPermanentToken 接口

## 基本信息

- URL：`/upc/user/getPermanentToken`
- 方法：`POST`
- Content-Type：`application/json`
- 鉴权：无需鉴权头
- 说明：明文账号密码校验，签发长期 token

## 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 明文密码 |

```json
{
  "username": "zhangsan",
  "password": "123456"
}
```

## 响应

### 成功

```json
{
  "code": 1,
  "message": "操作成功",
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "user": {
      "id": 1,
      "username": "zhangsan",
      "status": 1,
      "...": "其余用户字段（不含 password）"
    }
  }
}
```

响应头：`accessToken: <token>`

### 失败

| code | message | 场景 |
|:----:|---------|------|
| 0 | 用户名或密码错误 | 账号不存在或密码不对 |
| 0 | 账户已禁用 | 账号 status=0 |

## 调用示例

```bash
curl -X POST http://localhost:8488/upc/user/getPermanentToken \
  -H 'Content-Type: application/json' \
  -d '{"username":"zhangsan","password":"123456"}'
```

## Token 使用

- 拿到的 token 可直接放在 `Authorization` 或 `Accesstoken` 请求头中访问其他接口
- token 载荷字段：`sub`（用户名）、`user_id`、`tenant_id`（固定 1）、`iat`（签发时间）、`exp`（过期时间 2099-12-31 23:59:59）
