import request from '@/utils/request'

// 数据模型列表 -- 21-模型列表
export function getAILogListAPI(data) {
  return request({
    url: '/webapp/api/v1/logs/list',
    method: 'post',
    data: data,
  });
}

export function previewAILogAPI(data) {
  return request({
    url: '/webapp/api/v1/logs/preview',
    method: 'post',
    data: data,
  });
}


export function deleteAILogListAPI(data) {
  return request({
    url: '/webapp/api/v1/logs/delete',
    method: 'post',
    data: data,
  });
}
