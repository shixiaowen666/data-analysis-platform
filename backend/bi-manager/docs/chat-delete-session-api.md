# 删除会话接口文档

## 接口信息

| 项目 | 值 |
|---|---|
| 路径 | `GET /api/v1/chat/session/del/{sessionId}` |
| 方法 | GET |
| 认证 | `Authorization: Bearer {token}`（LoginFilter 校验） |
| 说明 | 删除指定会话的全部记录（会话记录 + 问答明细） |

## 请求参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| sessionId | 路径 | String | 是 | 会话 ID（UUID） |

## 响应

统一包装 `R<Void>`：

```json
{
  "code": 200,
  "message": "success",
  "data": null,
  "success": true
}
```

## 删除行为

1. 删除 `dcar_chat_model_qa` 中 `chat_session_id = #{sessionId} AND tenant_id = #{tenantId}` 的记录
2. 删除 `dcar_chat_record` 中 `chat_session_id = #{sessionId} AND tenant_id = #{tenantId}` 的记录
3. 两步在同一事务内，失败整体回滚

## 注意事项

- **租户隔离**：仅删除当前租户（tenant_id）下的会话记录，其他租户的同 ID 会话不受影响。
- **当前实现限制**：租户 ID 由服务端上下文取（`ChatController.getCurrentTenantId()`，当前实现写死 1L，TODO 待接登录上下文）。
- **MinIO 残留**：问答明细的实际内容存放在 MinIO（`minio_file_path`），本接口只删数据库记录，**不清理 MinIO 文件**，长期删除会话会在 MinIO 产生孤儿文件。
- 不存在的 sessionId 同样返回成功（幂等）。
