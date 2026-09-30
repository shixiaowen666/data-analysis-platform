import request from '@/utils/request'

// 数据模型列表 -- 21-模型列表
export function getDataModelListAPI(query) {
  return request({
    url: '/api/v1/data-models',
    method: 'get',
    params: query,
  });
}

// 数据模型详情 -- 22-模型详情
export function getDataModelDetailAPI(id) {
  return request({
    url: '/api/v1/data-models/' + id,
    method: 'get',
  })
}

//新数据模型 -- 23-新建模型
export function newDataModelAPI(data) {
  return request({
    url: '/api/v1/data-models',
    method: 'POST',
    data: data
  })
}

//更新数据模型 -- 24-编辑模型
export function editDataModelAPI(id,data) {
  return request({
    url: '/api/v1/data-models/' + id,
    method: 'PUT',
    data: data
  })
}

//更新数据模型 -- 25-删除模型
export function deleteDataModelAPI(id,) {
  return request({
    url: '/api/v1/data-models/' + id,
    method: 'DELETE',
    //data: data
  })
}

//可选主表列表 -- 26-可选主表列表
export function getDataModelCandidateFactTableAPI(query) {
  return request({
    url: '/api/v1/data-models/candidate-fact-tables',
    method: 'get',
    params: query
  })
}


//可选关联表及字段 -- 27-可选关联表及字段
export function getDataModelCandidateDimTableAPI(query) {
  return request({
    url: '/api/v1/data-models/candidate-dim-tables',
    method: 'get',
    params: query
  })
}

export const joinTypeEnum = {
  LEFTJOIN: {value: 'LEFT JOIN',name: '左连接' },
  INNERJOIN: {value: 'INNER JOIN',name: '内连接'},
  RIGHTJOIN: {value: 'RIGHT JOIN',name: '右连接'},
}

