import request from '@/utils/request'


// 获取字段映射列表-- 2.1 分析表列表
export function getFieldMappingListAPI(query) {
  return request({
    url: '/api/v1/field-mappings/tables',
    method: 'get',
    params: query
  })
}

// 获取字段映射详细 -- 2.2 分析表详情
export function getFieldMappingDetailAPI(id) {
  return request({
    url: '/api/v1/field-mappings/tables/' + id,
    method: 'get',
    //params: query
  })
}

// 查询未注册离别 -- 2.3 查询未注册表列表
/*export function getFieldMappingunRegisterTableAPI(query) {
  return request({
    url: '/api/v1/field-mappings/tables/unregistered',

    method: 'get',
    params: query
  })
}*/

// 查询未注册离别 -- 2.3 查询未注册表列表
export function getFieldMappingunTableBySourceIDAPI(query) {
  return request({
    url: '/api/v1/field-mappings/tablesBySourceId',
    method: 'get',
    params: query
  })
}

//新建物理表 -- 2.4 注册物理表
export function newRegisterPhysicalAPI(data) {
  return request({
    url: '/api/v1/field-mappings/tables/register-physical',
    method: 'POST',
    data: data
  })
}

//新建视图 -- 2.5 注册视图
export function newRegisterViewAPI(data) {
  return request({
    url: '/api/v1/field-mappings/tables/register-view',
    method: 'POST',
    data: data
  })
}

//执行Sql解析 -- 2.6 执行 SQL 解析字段
export function parseTableSqlAPI(data) {
  return request({
    url: '/api/v1/field-mappings/tables/parse',
    method: 'POST',
    data: data
  })
}

//新建视图 -- 2.8 编辑视图 - 保存修改
/*export function editRegisterViewAPI(id, data) {
  return request({
    url: '/api/v1/field-mappings/tables/' + id + '/view',

    method: 'PUT',
    data: data
  })
}*/

export function getViewDetalAPI(query) {
  return request({
    //url: '/api/v1/field-mappings/tables/' + id + '/view',
    url: '/api/v1/field-mappings/tables/view/info',
    method: 'get',
    params: query
  })
}


//获取字段映射列表 -- 2.9 获取字段映射列表
/*export function getTablesFieldsAPI(id) {
  return request({
    url: '/api/v1/field-mappings/tables/' + id + '/fields',

    method: 'get',
    //params: query
  })
}*/


//保存语义映射 -- 2.10 保存语义映射
export function saveMappingsAPI(data) {
  return request({
    url: '/api/v1/field-mappings/mappings',
    method: 'POST',
    data: data,
  })
}

//批量注册 -- 2.11 批量注册维度/指标
/*export function batchRegisterAPI(data) {
  return request({
    url: '/api/v1/field-mappings/batch-register',

    method: 'POST',
    data: data,
  })
}*/

//删除分析表 -- 2.12 删除分析表
export function deleteFieldMappingsTablesAPI(id) {
  return request({
    url: '/api/v1/field-mappings/tables/' + id,
    method: 'DELETE',
    //data: data,
  })
}


export function getFieldMappingsPtdateAPI() {
  return request({
    url: '/api/v1/field-mappings/ptdate',
    method: 'get',
    //data: data,
  })
}

export function checkMetricsEnameAPI(data) {
  return request({
    url: '/api/v1/metrics/check-ename',
    method: 'post',
    data: data,
  })
}

export function checkMetricsCnameAPI(data) {
  return request({
    url: '/api/v1/metrics/check-cname',
    method: 'post',
    data: data,
  })
}

export function checkDimensionsEnameAPI(data) {
  return request({
    url: '/api/v1/dimensions/check-ename',
    method: 'post',
    data: data,
  })
}

export function checkDimensionsCnameAPI(data) {
  return request({
    url: '/api/v1/dimensions/check-cname',
    method: 'post',
    data: data,
  })
}

//获取数据源选项 -- 2.13 获取数据源选项（用于前端下拉选择）
export function getDataSourcesListAPI() {
  return request({
    url: '/api/v1/field-mappings/data-sources',
    method: 'get',
    //data: data,
  })
}


export const typeBusinessEnum = [
  { value: 'fact', name: 'fact', color: 'blue', bgColor: '#0000ff2b' },
  { value: 'dim', name: 'dim', color: 'blue', bgColor: '#0000ff2b' },
]

/*export const typeStorageEnum = {
  0: { value: '0', name: '物理表', color: 'green', bgColor: '#0080002b' },
  1: { value: '1', name: '视图', color: 'blue', bgColor: '#0000ff2b' },
}*/

/*export const statusEnum = {
  0: { value: '0', name: '删除', color: 'black', bgColor: 'white' },
  1: { value: '1', name: '审批中', color: 'black', bgColor: 'white' },
  2: { value: '2', name: '待上线', color: 'black', bgColor: 'white' },
  3: { value: '3', name: '已上线', color: 'black', bgColor: 'white' },
  4: { value: '4', name: '已下线', color: 'black', bgColor: 'white' },
  5: { value: '5', name: '已驳回', color: 'black', bgColor: 'white' },
}*/

export const summaryKeyEnum = {
  sum: { value: 'sum', name: 'sum' },
  count: { value: 'count', name: 'count' },
  count_distinct: { value: 'count_distinct', name: 'count_distinct' },
  avg: { value: 'avg', name: 'avg' },
  time_format: { value: 'time_format', name: 'time_format' },
}

export const compareStatusEnum = {
  0: { value: '0', name: '保留' },
  1: { value: '1', name: '新增' },
  2: { value: '2', name: '废弃' },
}