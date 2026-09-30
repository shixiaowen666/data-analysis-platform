"""Pydantic 请求/响应模型"""
from pydantic import BaseModel, Field
from typing import Optional, Literal, List, Dict, Any


# ========== 数据范围（智能体） ==========
class ScopeSpec(BaseModel):
    """内联数据范围。三个列表都为空 = 不限制（全库）。
    tables 非空时：指标/维度默认为这些表下的全部；metrics/dimensions 非空时在此基础上再收窄。"""
    tables: List[str] = Field(default_factory=list, description="允许的表名（table_name）")
    metrics: List[str] = Field(default_factory=list, description="允许的指标 code（metric_code）")
    dimensions: List[str] = Field(default_factory=list, description="允许的维度 code（dimension_code）")


# ========== 召回请求 ==========
class RecallRequest(BaseModel):
    query: str = Field(..., description="用户原始 query")
    use_llm_judge: bool = Field(False, description="是否启用 LLM Stage-2 精判")
    parallel: bool = Field(True, description="四路并行执行（默认）")
    agent_id: Optional[str] = Field(None, description="智能体 ID：按其已注册的数据范围检索")
    scope: Optional[ScopeSpec] = Field(None, description="内联数据范围（优先级高于 agent_id）")


# ========== 召回结果 ==========
class CandidateItem(BaseModel):
    entity_type: str
    entity_id: int
    display_name: str
    description: str = ""
    score: float
    source: str
    table_id: Optional[int] = None
    dimension_id: Optional[int] = None
    entity_name: Optional[str] = None
    entity_key: Optional[str] = Field(None, description="稳定业务键 table:x / metric:x / dimension:x / value:d:v")
    legal_tables: List[int] = Field(default_factory=list, description="完整挂靠表集合（范围内）")
    rerank_score: Optional[float] = None


class RecallResponse(BaseModel):
    query: str
    clean_query: str
    time_terms: List[str]
    candidates: List[CandidateItem]
    by_path: Dict[str, List[CandidateItem]]
    timings_ms: Dict[str, float]
    scope: Optional[Dict[str, Any]] = None
    recall_text: Optional[str] = None
    intent_subs: List[str] = Field(default_factory=list)
    llm_judge: Optional[Dict[str, Any]] = None


# ========== 配置 API ==========
class ConfigUpdateRequest(BaseModel):
    recall_mode: Optional[Literal["top_k", "threshold", "hybrid"]] = None
    top_k: Optional[int] = Field(None, ge=1, le=500)
    threshold: Optional[float] = Field(None, ge=0.0, le=1.0)
    enabled: Optional[bool] = None
    description: Optional[str] = None


class BatchUpdateItem(BaseModel):
    path_name: str
    entity_type: str
    recall_mode: Optional[Literal["top_k", "threshold", "hybrid"]] = None
    top_k: Optional[int] = Field(None, ge=1, le=500)
    threshold: Optional[float] = Field(None, ge=0.0, le=1.0)
    enabled: Optional[bool] = None


class ConfigResponse(BaseModel):
    path_name: str
    entity_type: str
    recall_mode: str
    top_k: int
    threshold: float
    enabled: bool
    description: Optional[str] = None


# ========== slim 召回（缩圈版 database_meta） ==========
class SlimRecallRequest(BaseModel):
    query: str = Field(..., description="用户原始 query")
    parallel: bool = Field(True, description="四路并行执行（默认）")
    include_business_context: bool = Field(True, description="是否附带业务知识 business_context")
    dim_value_topn: int = Field(30, ge=0, le=500, description="每维度 possible_values 上限，0=不限")
    use_rewrite: bool = Field(True, description="链路①：知识注入/字典改写")
    use_rewrite_llm: bool = Field(False, description="链路①L2：LLM 改写（慢，失败自动降级 L1）")
    use_llm_judge: bool = Field(True, description="链路⑤：LLM 精判（关闭则走旧路径纯召回导出）")
    intent_split: Optional[bool] = Field(None, description="复合意图拆分（None=跟随全局配置）")
    out_table: bool = Field(True, description="是否输出 table_summaries")
    out_metric: bool = Field(True, description="是否输出 available_metrics")
    out_dimension: bool = Field(True, description="是否输出 available_dimensions（含维度内 possible_values）")
    agent_id: Optional[str] = Field(None, description="智能体 ID：按其已注册的数据范围检索")
    scope: Optional[ScopeSpec] = Field(None, description="内联数据范围（优先级高于 agent_id）")


class SyncRequest(BaseModel):
    rebuild: bool = Field(False, description="true=更新 database_meta.json 后重建本地库/向量索引并热替换")


class SyncStatusResponse(BaseModel):
    task_id: Optional[str] = None
    status: str  # running / success / failed / idle
    detail: Optional[Dict[str, Any]] = None


# ========== 批量测试 ==========
class BatchCaseItem(BaseModel):
    no: str = Field(..., description="题号")
    query: str = Field(..., description="问题")
    expect: Dict[str, str] = Field(default_factory=dict,
                                   description="期望列：metric/table/dimension/dim_value")


class BatchRunRequest(BaseModel):
    cases: List[BatchCaseItem] = Field(..., min_length=1, max_length=2000)
    concurrency: int = Field(3, ge=1, le=10, description="并发数 1-10")
    use_llm_judge: bool = Field(False, description="是否启用 LLM 精判（默认关闭）")
    use_rewrite: bool = Field(True, description="链路①：知识注入/字典改写")
    use_rewrite_llm: bool = Field(False, description="LLM 改写（慢）")
    dim_value_topn: int = Field(30, ge=0, le=500)
    intent_split: Optional[bool] = Field(None, description="复合意图拆分（None=跟随全局配置）")
    filename: str = Field("", description="来源文件名（记录用）")
    agent_id: Optional[str] = Field(None, description="按智能体范围批测")
    scope: Optional[ScopeSpec] = Field(None, description="内联范围批测")


# ========== 实体层：上游同步 / 智能体 ==========
class DimValuePatch(BaseModel):
    dimension_code: str
    values: List[Any] = Field(default_factory=list, description="[str] 或 [{value_name, synonyms}]")
    mode: Literal["upsert", "replace", "delete"] = "upsert"


class EntityDeletes(BaseModel):
    tables: List[str] = Field(default_factory=list)
    metrics: List[str] = Field(default_factory=list)
    dimensions: List[str] = Field(default_factory=list)
    knowledge: List[str] = Field(default_factory=list)


class EntityUpsertRequest(BaseModel):
    """上游数据管理 → 本模块 的变更推送（字段与 database_meta 同构；只传变化的对象）"""
    tables: List[Dict[str, Any]] = Field(default_factory=list, description="table_summaries 条目")
    metrics: List[Dict[str, Any]] = Field(default_factory=list, description="available_metrics 条目")
    dimensions: List[Dict[str, Any]] = Field(default_factory=list,
                                             description="available_dimensions 条目（含 possible_values 时整体替换该维度的值）")
    dim_values: List[DimValuePatch] = Field(default_factory=list, description="仅改维度值时用，避免整体替换")
    knowledge: List[Dict[str, Any]] = Field(default_factory=list, description="{knowledge_id, knowledgeAlias, knowledgeElement}")
    deletes: Optional[EntityDeletes] = None
    force: bool = Field(False, description="忽略 text_hash 强制重编（换模型/模板后用）")


class AgentUpsertRequest(BaseModel):
    agent_name: str = ""
    scope: ScopeSpec = Field(default_factory=ScopeSpec)
