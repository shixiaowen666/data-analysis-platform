import request from '@/utils/request'

// 获取数据源列表 -- 11-数据源列表
export function getDataSourceListAPI(query) {
  return request({
    url: '/api/v1/data-sources',
    method: 'get',
    params: query
  })
}

// 获取数据源列表 -- 12-数据源详情
export function getDataSourceDetailAPI(id) {
  return request({
    url: '/api/v1/data-sources/' + id,
    method: 'get',
  })
}

//新数据源 -- 13-新建数据源
export function newDataSourceAPI(data) {
  return request({
    url: '/api/v1/data-sources',
    method: 'POST',
    data: data
  })
}
//更新数据源 -- 14-编辑数据源
export function editDataSourceAPI(id,data) {
  return request({
    url: '/api/v1/data-sources/' + id,
    method: 'PUT',
    data: data
  })
}

//删除数据源 -- 15-删除数据源
export function deleteDataSourceAPI(id) {
  return request({
    url: '/api/v1/data-sources/' + id,
    method: 'DELETE',
    //data: data
  })
}

//测试连接 -- 16-测试连接
export function testDataSourceAPI(data) {
  return request({
    url: '/api/v1/data-sources/test-connection',
    method: 'POST',
    data: data
  })
}

//获取JDBC前缀 17-预览-jdbc-url
/*export function getJDBCURLAPI(data) {
  return request({
    url: '/api/v1/data-sources/jdbc-url',
    method: 'POST',
    data: data
  })
}*/

//获取数据库列表 -- 18-数据库类型列表
export function getDBTypeAPI() {
  return request({
    url: '/api/v1/meta/db-type/list',
    method: 'get',
   // params: query
  })
}

//获取JDBC前缀 19-获取-jdbc-前缀
/*export function getJDBCPrefixAPI(query) {
  return request({
    url: '/api/v1/db-types/jdbc-prefix',

    method: 'get',
    params: query
  })
}*/

//启动元数据 -- 110-启动元数据采集
export function getBeginCollectAPI(id, data) {
  return request({
    url: '/api/v1/data-sources/'+ id + '/collect',

    method: 'POST',
    data: data
  })
}

//获取远程采集列表 -- 111-远程库表列表选表采集
export function getCollectRemoteTablesListAPI(data) {
  return request({
    url: '/api/v1/data-sources/remote-tables',
    method: 'post',
    data: data
  })
}

//查看选中列表 -- 112-查询选中表
/*export function getSelectTableListAPI(query) {
  return request({
    url: '/api/v1/meta/select-table/list',
    method: 'get',
    params: query
  })
}*/

//元数据列表 -- 113-元数据表列表
export function getMetaDataListAPI(data) {
  return request({
    //url: '/data-sources/'+ id + '/meta-tables',
    url: '/api/v1/meta/table/page',
    method: 'post',
    data: data
  })
}

//元数据字段列表 -- 114-元数据字段列表
export function getMetaDataColumnsListAPI(query) {
  return request({
    //url: '/data-sources/'+ id + '/columns',
    url: '/api/v1/meta/column/list',
    method: 'get',
    params: query
  })
}

//采集历史列表 -- 115-采集日志列表
export function getCollectHistoryListAPI(data) {
  return request({
    //url: '/data-sources/'+ id + '/collect-logs',
    url: '/api/v1/meta/collect-log/page',
    method: 'post',
    data: data
  })
}

//采集历史详情 -- 116-采集日志详情
export function getCollectHistoryDetailListAPI(id) {
  return request({
    url: '/api/v1/meta/collect-log/'+ id,
    method: 'get',
    //data: data
  })
}


export const statusEnum = [
  {value: 1,name: '已启用'},
  {value: 0,name: '已停用'},
]

export const collectTypeEnum = {
  FULL: {value: 'full',name: '全库采集'},
  SELECT: {value: 'select',name: '选表采集'},
}

/*export const collectStatusEnum = {
  RUNNING: {value: 'RUNNING',name: '执行中', color: 'blue', bgColor: '#0000ff2b' },
  SUCCESS: {value: 'SUCCESS',name: '成功', color: 'green', bgColor: '#0080002b'},
  FAIL: {value: 'FAIL',name: '失败', color: 'red', bgColor: '#ff00002b'},
}*/

