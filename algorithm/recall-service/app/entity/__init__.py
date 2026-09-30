"""
实体层（增量向量化 + 智能体范围检索）

数据流：
  上游对象（表/指标/维度/维度值/知识） ──upsert──▶ src_* 源对象表
        │
        ▼ EntityBuilder（源对象 → 原子可检索实体，生成 embedding_text + text_hash）
  recall_entity（稳定 entity_key / faiss_id，向量持久化）
        │
        ├─ SyncService：变更传播 → 只对 text_hash 变化的实体重新编码 → upsert 索引
        ├─ IndexStore：IndexIDMap2(IndexFlatIP)，支持增删 + IDSelector 范围过滤
        └─ ScopeResolver：agent_scope → 允许的 faiss_id 集合（无需向量化）
"""
