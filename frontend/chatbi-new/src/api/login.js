import request from '@/utils/request'

// 登录方法
export function login(data) {
  return request({
    url: '/upc/user/login',
    headers: {
      isToken: false
    },
    method: 'post',
    data: data
  })
}


/*export function loginTo(data) {
  return request({
    url: '/szjlservice/sso/chatbi/login',
    headers: {
      isToken: false
    },
    method: 'get',
    params: data
  })
}*/



