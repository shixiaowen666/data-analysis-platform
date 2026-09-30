import request from '@/utils/request'

// 数据模型列表 -- 21-模型列表
export function getPromptGroupAPI(data) {
  return request({
    url: '/webapp/api/v1/prompts/groups',
    method: 'post',
    data: data,
  });
}


export function getPromptListAPI(data) {
  return request({
    url: '/webapp/api/v1/prompts/list',
    method: 'post',
    data: data,
  });
}

export function getPromptDetailAPI(data) {
  return request({
    url: '/webapp/api/v1/prompts/version/content',
    method: 'post',
    data: data,
  });
}


/*export function newPromptAPI(data) {
  return request({
    url: '/webapp/api/v1/prompts/upload',
    method: 'post',
    data: data,
  });
}*/

export function getCurrentPromptVersionAPI(data) {
  return request({
    url: '/webapp/api/v1/prompts/current',
    method: 'post',
    data: data,
  });
}


export function existsPromptVersionAPI(data) {
  return request({
    url: '/webapp/api/v1/prompts/version/exists',
    method: 'post',
    data: data,
  });
}


export function editPromptAPI(data) {
  return request({
    url: '/webapp/api/v1/prompts/update',
    method: 'post',
    data: data,
  });
}

export function deletePromptAPI(data) {
  return request({
    url: '/webapp/api/v1/prompts/delete',
    method: 'post',
    data: data,
  });
}

export function switchPromptAPI(data) {
  return request({
    url: '/webapp/api/v1/prompts/switch',
    method: 'post',
    data: data,
  });
}






