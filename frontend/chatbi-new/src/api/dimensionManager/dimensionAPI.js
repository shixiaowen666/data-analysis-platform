import request from '@/utils/request'

// 获取维度列表 -- 1-列表与查询
export function getDimensionListAPI(query) {
  return request({
    url: '/api/v1/dimensions/page',
    method: 'get',
    params: query
  })
}

//获取维度详情 2-维度详情
export function getDimensionDetailAPI(id) {
  return request({
    url: '/api/v1/dimensions/' + id,
    method: 'GET',
    //params: query
  })
}

//新维度 -- 3-新建维度
export function saveDimensionDataAPI(data) {
  return request({
    url: '/api/v1/dimensions/save',
    method: 'POST',
    data: data
  })
}

//新维度 -- 3-新建维度
/*export function newDimensionDataAPI(data) {
  return request({
    url: '/api/v1/dimensions',

    method: 'POST',
    data: data
  })
}*/

//编辑维度 -- 4-编辑维度
/*export function editDimensionDataAPI(id,data) {
  return request({
    url: '/api/v1/dimensions/' + id,

    method: 'PUT',
    data: data
  })
}*/

//下线维度 -- 5-维度下线
export function offlineDimensionDataAPI(id) {
  return request({
    url: '/api/v1/dimensions/' + id + '/offline',
    method: 'get',
    //data: data
  })
}

//删除维度 -- 6-删除维度
export function deleteDimensionDataAPI(id, data) {
  return request({
    url: '/api/v1/dimensions/' + id,
    method: 'DELETE',
    data: data
  })
}

// 维度采集列表 -- 7. 维值采集列表
export function getDimensionValuesAPI(id) {
  return request({
    url: '/api/v1/dimensions/' + id + '/values',
    method: 'get',
    //params: query
  })
}


// 列表与查询 -- 列表与查询
export function getShowDimensionListAPI(query) {
  return request({
    url: '/api/v1/dimensions/list',
    method: 'get',
    params: query
  })
}

// 列表与查询 -- 上线
export function onlineDimensionDataAPI(id) {
  return request({
    url: '/api/v1/dimensions/' + id + '/online',
    method: 'get',
    //params: query
  })
}



export function getDimTaskConfigAPI() {
  return request({
    url: '/api/v1/dimensions/collect-config/get',
    method: 'get',
    //params: query
  })
}

export function saveDimTaskConfigAPI(data) {
  return request({
    url: '/api/v1/dimensions/collect-config/save',
    method: 'POST',
    data: data
  })
}




export const dimensionTypeEnum = {
  1: { value: 1, name: '标准维' },
  2: { value: 2, name: '杂项维' },
}

export const collectStatusEnum = {
  0: { value: 0, name: '未采集', color: '#a8a5a5', bgColor: '#c0c0c01f' },
  1: { value: 1, name: '全量采集', color: 'green', bgColor: '#0080002b' },
  2: { value: 2, name: '部分采集', color: 'blue', bgColor: '#0000ff2b' },
}

/*export const highLevelFlagEnum = [
  { value: 1, name: '高基维' },
  { value: 2, name: '普通维' },
]*/

/*export const statusEnum = [
  { value: 0, name: '草稿' },
  { value: 1, name: '审批中' },
  { value: 2, name: '已上线' },
  { value: 3, name: '已下线' },
]*/

export const statusEnum = {
  //{value: 0,name: '草稿' },
  //{value: 1,name: '审批中'},
  2: {value: 2,name: '已上线'},
  3: {value: 3,name: '已下线'},
}