import request from '@/utils/request'

// 获取历史记录
export function getChatHistoryListAPI(query) {
  return request({
    url: '/api/v1/chat/session/list',

    method: 'get',
    params: query
  })
}

// 获取历史记录
export function getChatHistoryListDetailAPI(query) {
  return request({
    url: '/api/v1/chat/session/info',

    method: 'get',
    params: query
  })
}

// 获取历史记录
export function deleteHistoryAPI(sessionId) {
  return request({
    url: '/api/v1/chat/session/del/' + sessionId,

    method: 'get',
    //params: query
  })
}


export function saveAiBodyAPI(data) {
  return request({
    url: '/api/v1/aibody/save',
    method: 'POST',
    data: data
  });
}

//问数
export function sendChatAPI(data) {
  return request({
    url: '/api/v1/chat-server/chat',
    method: 'post',
    data,
  });
}

/*export function getChatInfo(params) {
  return request({
    url: '/api/v1/chat/info',
    method: 'get',
    params,
  });
}*/

export function getAiBodyListAPI(params) {
  return request({
    url: '/api/v1/aibody/list',
    method: 'get',
    params: params
  });
}

export function getAiBodyDetailAPI(code) {
  return request({
    url: '/api/v1/aibody/info/' + code,
    method: 'get',
    //params: params
  });
}

export function getAiBodyCandidateTablesAPI(params) {
  return request({
    url: '/api/v1/aibody/candidate-tables',
    method: 'get',
    params: params
  });
}

export function deleteAiBodyAPI(code) {
  return request({
    url: '/api/v1/aibody/del/' + code,
    method: 'get',
    //params: params
  });
}


export function stopChatAPI(params) {
  return request({
    url: '/api/v1/chat-server/chat/stop',
    method: 'get',
    params: params
  });
}


export function delRelationAPI(params) {
  return request({
    url: '/api/v1/aibody/relation/del',
    method: 'get',
    params: params
  });
}

export function getChatStepAPI(params) {
  return request({
    url: '/api/v1/chat/step',
    method: 'get',
    params: params
  });
}

export function getQuestionAPI(params) {
  return request({
    url: '/api/v1/recommend/question/page',
    method: 'get',
    params: params
  });
}

export function saveQuestionAPI(data) {
  return request({
    url: '/api/v1/recommend/question/save',
    method: 'post',
    data: data
  });
}

export function setQuestionStatusAPI(data) {
  return request({
    url: '/api/v1/recommend/question/status',
    method: 'post',
    params: data
  });
}

export function delQuestionAPI(id) {
  return request({
    url: '/api/v1/recommend/question/del/' + id,
    method: 'get',
    //params: params
  });
}

export function getTagListAPI(params) {
  return request({
    url: '/api/v1/recommend/tag/list',
    method: 'get',
    params: params
  });
}

export function saveTagAPI(data) {
  return request({
    url: '/api/v1/recommend/tag/save',
    method: 'post',
    data: data
  });
}

export function delTagAPI(id) {
  return request({
    url: '/api/v1/recommend/tag/del/' + id,
    method: 'get',
    //params: params
  });
}

export function isMultiValueOperator(op) {
  return op === 5 || op === 6 || op == 2 || op == 7;
}