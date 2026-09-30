import request from '@/utils/request'

// 获取指标列表 -- 1-列表与查询
export function getMetricDataListAPI(query) {
  return request({
    url: '/api/v1/metrics',

    method: 'get',
    params: query
  })
}

//获取指标详情 -- 2-指标详情
export function getMetricDataDetailAPI(id) {
  return request({
    url: '/api/v1/metrics/' + id,

    method: 'GET',
    //params: query
  })
}

//新指标 -- 3-新建指标
export function newMetricDataAPI(data) {
  return request({
    url: '/api/v1/metrics',

    method: 'POST',
    data: data
  })
}

//编辑指标 -- 4-编辑指标
export function editMetricDataAPI(id,data) {
  return request({
    url: '/api/v1/metrics/' + id,

    method: 'PUT',
    data: data
  })
}

//下线指标 -- 5-下线指标
export function offlineMetricDataAPI(id) {
  return request({
    url: '/api/v1/metrics/' + id + '/offline',

    method: 'get',
    //data: data
  })
}

//删除指标 -- 6-删除指标
export function deleteMetricDataAPI(id) {
  return request({
    url: '/api/v1/metrics/' + id,

    method: 'DELETE',
    //data: data
  })
}

// 获取指标列表 -- 7-获取指标列表
export function getShowMetricDataListAPI(query) {
  return request({
    url: '/api/v1/metrics/list',

    method: 'get',
    params: query
  })
}

// 获取指标列表 -- 7-获取指标列表
export function getShowMetricGroupDataListAPI(query) {
  return request({
    url: '/api/v1/metrics/list-with-groups',

    method: 'get',
    params: query
  })
}

//下线指标 -- 5-上线指标
export function onlineMetricDataAPI(id) {
  return request({
    url: '/api/v1/metrics/' + id + '/online',

    method: 'get',
    //data: data
  })
}


export const typeMetricEnum = {
  atom: {value: 'atom',name: '原子指标' },
  calc: {value: 'calc',name: '计算指标'},
  derive: {value: 'derive',name: '派生指标'},
}

export const statusEnum = {
  //{value: 0,name: '草稿' },
  //{value: 1,name: '审批中'},
  2: {value: 2,name: '已上线'},
  3: {value: 3,name: '已下线'},
}

export const decimalEnum = [
  {value: 0,name: '无' },
  {value: 1,name: '.0'},
  {value: 2,name: '.00'},
  {value: 3,name: '.000'},
  {value: 4,name: '.0000'},
]

/*export const unitTypeEnum = [
  {value: 0,name: '不指定' },
  {value: 1,name: '金额'},
  {value: 2,name: '百分比'},
  {value: 3,name: '.000'},
  {value: 4,name: '.0000'},
]*/

export const symbolEnum = [
  {value: '=',name: '=' },
  {value: '>',name: '>'},
  {value: '<',name: '<'},
  {value: '≠',name: '≠'},
]
