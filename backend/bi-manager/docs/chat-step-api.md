# 单步骤查询结果

## 接口信息

| 项目 | 说明 |
|------|------|
| 请求方式 | `GET` |
| 路径 | `/v1/chat/step` |
| 权限 | 无鉴权，无需登录态 |

## 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `chatSessionId` | String | 是 | 会话 ID |
| `chatId` | String | 是 | 对话 ID（一轮问答的唯一标识） |
| `itemId` | Integer | 是 | 步骤序号。`0`=用户问题，`1`/`2`/`3`…=AI 各处理步骤 |

## 请求示例

```
GET /api/v1/chat/step?chatSessionId=5985b083-2eab-4a95-a947-7e71430116df&chatId=679bc297-0822-4e47-9585-fdcda50e5449&itemId=1
```

## 返回结果

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "type": "ai",
    "itemId": 1,
    "question": "今年各品类的销售额是多少？",
    "stepType": "query",
    "chartData": {
      "columns": ["品类", "销售额"],
      "rows": [
        ["服装", 1280000],
        ["数码", 960000],
        ["食品", 540000]
      ]
    },
    "think": {
      "status": 1,
      "message": "已从 Olap 数据源查询到 3 条结果",
      "speed": 1234
    },
    "status": 1,
    "interactionMode": 0,
    "enableContext": true,
    "host": "ip:port",
    "questionRewriter": "查询今年各品类的销售额合计",
    "updateUser": "admin",
    "updateTime": "2026-07-27 10:00:00"
  }
}
```

## data 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | String | 类型：`user`=用户问题，`ai`=AI 响应 |
| `itemId` | Integer | 步骤序号 |
| `question` | String | 问题/步骤描述 |
| `stepType` | String | 步骤类型：`query`（查数），`compute`（计算），`summarize`（总结），空字符串=无 |
| `chartData` | Object | 图表数据，包含 `columns`（列名）和 `rows`（数据行） |
| `think` | Object | 思考过程，含 `status`（0=中断,1=结束,2=输出中）、`message`（输出内容）、`speed`（耗时/ms） |
| `status` | Integer | 步骤状态：`0`=思考中，`1`=已完成，`2`=查数中 |
| `interactionMode` | Integer | 交互模式：`0`=NL2DSL，`1`=NL2SQL，`3`=知识问答，`4`=自动 |
| `enableContext` | Boolean | 是否开启上下文引用 |
| `host` | String | 服务端 host 地址 |
| `questionRewriter` | String | 重写后的问题 |
| `updateUser` | String | 修改人 |
| `updateTime` | String | 修改时间 |

## 错误响应

### 步骤不存在

```json
{
  "code": 404,
  "message": "步骤记录不存在",
  "data": null
}
```

### 步骤数据读取失败

```json
{
  "code": 500,
  "message": "步骤数据读取失败",
  "data": null
}
```

## 与 /info 接口对比

| | `/v1/chat/info` | `/v1/chat/step` |
|---|---|---|
| 用途 | 获取一轮对话的全部步骤 | 获取单个步骤 |
| 参数 | `chatSessionId` + `chatId` | `chatSessionId` + `chatId` + `itemId` |
| 返回类型 | `ChatDTO`（包含 `List<ChatItemInfo>`） | 单个 `ChatItemInfo` |
| 数据库查询 | 查该 chatId 下所有行 | 查 1 行（多了 `item_id` 条件） |
| MinIO 读取 | 循环读 N 个文件 | 只读 1 个文件 |
| MinIO 失败处理 | 静默跳过该条 | 抛 500 错误 |
