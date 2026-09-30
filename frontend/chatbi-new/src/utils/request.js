import axios from 'axios'
import { Notification, Message, Loading } from 'element-ui'
import router from '@/router'
import { getToken, clearAuth } from '@/utils/auth'
import { errorCode, tansParams, isMobile, szLoginLink } from '@/utils/common'
import cache from '@/plugins/cache'

let downloadLoadingInstance;

// 是否正在跳转登录页，避免重复跳转
let isRedirectingToLogin = false;

axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8'
axios.defaults.headers['tenantid'] = 1

function redirectToLogin(message) {
  if (isRedirectingToLogin || router.currentRoute.path === '/login' || router.currentRoute.path === '/web/login') {
    return
  }
  isRedirectingToLogin = true
  clearAuth()
  Message.warning(message || '登录状态已过期，请重新登录')
  //const target = router.currentRoute.fullPath;
  const isMobileDevice = isMobile();
  if(isMobileDevice){
    //isRedirectingToLogin = false
    if(szLoginLink) window.location.href = szLoginLink
    else{
      router.push(`/web/login`).finally(() => {
        isRedirectingToLogin = false
      })
    }
    /*router.push(`/web/login`).finally(() => {
      isRedirectingToLogin = false
    })*/
  }else{
    //isRedirectingToLogin = false
    if(szLoginLink) window.location.href = szLoginLink
    else{
      router.push(`/login`).finally(() => {
        isRedirectingToLogin = false
      })
    }

    /*router.push(`/login`).finally(() => {
      isRedirectingToLogin = false
    })*/
  }
}

// 创建axios实例
const service = axios.create({
  // axios中请求配置有baseURL选项，表示请求URL公共部分
  baseURL: process.env.VUE_APP_BASE_API,
  // 超时
  timeout: 600000
})

// request拦截器
service.interceptors.request.use(config => {
  // 是否需要设置 token
  const isToken = (config.headers || {}).isToken === false
  // 是否需要防止数据重复提交
  const isRepeatSubmit = (config.headers || {}).repeatSubmit === false
  if (getToken() && !isToken) {
    config.headers['Authorization'] = 'Bearer ' + getToken() // 让每个请求携带自定义token 请根据实际情况自行修改
  }

  // get请求映射params参数
  if (config.method === 'get' && config.params) {
    let url = config.url + '?' + tansParams(config.params);
    url = url.slice(0, -1);
    config.params = {};
    config.url = url;
  }
  if (!isRepeatSubmit && (config.method === 'post' || config.method === 'put')) {
    const requestObj = {
      url: config.url,
      data: typeof config.data === 'object' ? JSON.stringify(config.data) : config.data,
      time: new Date().getTime()
    }
    const sessionObj = cache.session.getJSON('sessionObj')
    if (sessionObj === undefined || sessionObj === null || sessionObj === '') {
      cache.session.setJSON('sessionObj', requestObj)
    } else {
      const s_url = sessionObj.url;                  // 请求地址
      const s_data = sessionObj.data;                // 请求数据
      const s_time = sessionObj.time;                // 请求时间
      const interval = 500;                         // 间隔时间(ms)，小于此时间视为重复提交
      if (s_data === requestObj.data && requestObj.time - s_time < interval && s_url === requestObj.url) {
        const message = '数据正在处理，请勿重复提交';
        console.warn(`[${s_url}]: ` + message)
        return Promise.reject(new Error(message))
      } else {
        cache.session.setJSON('sessionObj', requestObj)
      }
    }
  }
  return config
}, error => {
    console.log(error)
    Promise.reject(error)
})

// 响应拦截器
service.interceptors.response.use(res => {
    // 未设置状态码则默认成功状态
    const code = res.data.code || 200;
    // 获取错误信息
    const msg = res.data.message || res.data.msg || errorCode[code] || errorCode['default']
    // 二进制数据则直接返回
    if(res.request.responseType ===  'blob' || res.request.responseType ===  'arraybuffer'){
      return res.data
    }
    if(code == 400){//用于预览错误的
      return res.data
    }

    if (code === 401) {
      redirectToLogin(msg)
      return Promise.reject('无效的会话，或者会话已过期，请重新登录。')
    } else if (code === 500 || code === 404) {
      //Message({
      //  message: msg,
      //  type: 'error'
      //})
      //否则弹窗
      return res.data
      //return Promise.reject(new Error(msg))
    } else if (code !== 200 && code !== 1 && code !== 4003) {
      Notification.error({
        title: msg
      })
      //否则弹窗
      return res.data
      //return Promise.reject('error')
    } else {
      return res.data
    }
  },
  error => {
    console.log('err' + error)
    if (error.response && error.response.status === 401) {
      redirectToLogin('登录状态已过期，请重新登录')
      return Promise.reject(error)
    }

    let { message } = error;
    if (message == "Network Error") {
      message = "后端接口连接异常";
    }
    else if (message.includes("timeout")) {
      message = "系统接口请求超时";
    }
    else if (message.includes("Request failed with status code")) {
      message = "系统接口" + message.substr(message.length - 3) + "异常";
    }
    Message({
      message: message,
      type: 'error',
      duration: 5 * 1000
    })
    return Promise.reject(error)
  }
)

export function download(url, params, filename) {
  downloadLoadingInstance = Loading.service({ text: "正在下载数据，请稍候", spinner: "el-icon-loading", background: "rgba(0, 0, 0, 0.7)", })

  axios({
    url: url, // 后端API地址
    method: 'POST',
    data: params,
    responseType: 'blob', // 重要：确保设置为'blob'以处理二进制数据
    headers: {
      'Authorization': getToken() ? `Bearer ${getToken()}` : '', // 根据后端要求调整格式
      // 若后端不用 Bearer 前缀，直接传 token: token
    },

  }).then(response => {

    const contentType = response.headers['content-type'] || '';
    
    // 如果返回的是 JSON（错误信息），而不是文件流
    if (contentType.includes('application/json')) {
      // 将 blob 转为 JSON 读取错误信息
      const reader = new FileReader();
      reader.onload = function() {
        try {
          const errorData = JSON.parse(reader.result);
          Message.error(errorData.message || '下载失败，请稍后重试');
        } catch (e) {
          Message.error('下载失败，请稍后重试');
        }
        downloadLoadingInstance.close();
      };
      reader.readAsText(response.data);
      return;
    }

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', filename); // 设置下载后的文件名，如果需要的话
      document.body.appendChild(link);
      link.click();
      window.URL.revokeObjectURL(url); // 清理内存中的URL对象
      document.body.removeChild(link); // 移除创建的<a>标签
      downloadLoadingInstance.close();

  }).catch(error => {
    console.error('下载失败:', error);
    Message.error('下载文件出现错误，请联系管理员!')
      downloadLoadingInstance.close();
  });
}

export default service
