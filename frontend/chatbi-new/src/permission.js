import router from './router'
import { getToken } from '@/utils/auth'
import {isMobile, szLoginLink} from "@/utils/common.js"

const whiteList = ['/login','/web/login']

/*router.beforeEach((to, from, next) => {
  if (getToken()) {
    if (to.path === '/login') {
      next({ path: '/' })
    } else {
      next()
    }
  } else if (whiteList.indexOf(to.path) !== -1) {
    next()
  } else {
    next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
  }
})*/


const PATH_MAP = {
  '/': '/web',           // PC首页 -> 移动首页
  '/login': '/web/login', // PC关于 -> 移动关于
  // 如果有动态路由，比如 /detail/123 -> /m/detail/123，需要特殊处理
};

// 获取对应的目标路径
function getTargetPath(path, isToMobile) {
  if (isToMobile) {
    // 移动端：查映射表，如果找不到则加 /m 前缀（兼容动态路由）
    return PATH_MAP[path] || `/web${path}`;
  } else {
    // PC端：去掉 /m 前缀，如果没有前缀则保留原路径
    return path.replace(/^\/web/, '') || '/';
  }
}

/*const IGNORE_ERRORS = ['Redirected', 'NavigationDuplicated', 'Navigation cancelled']

const originalPush = router.push
router.push = function push(location) {
  return originalPush.call(this, location).catch(err => {
    if (IGNORE_ERRORS.some(msg => err.message?.includes(msg))) {
      return err
    }
    return Promise.reject(err)
  })
}

router.onError((error) => {
  const IGNORE_ERRORS = ['Redirected', 'NavigationDuplicated', 'Navigation cancelled'];
  if (IGNORE_ERRORS.some(msg => error.message?.includes(msg))) {
    return;
  }
  console.error('路由错误:', error);
});*/


router.beforeEach((to, from, next) => {
if (to.path === from.path) return next()

  const dev = process.env.VUE_APP_PRODUCTION !== 'true'
  if(dev){
      if (getToken()) {
    if (to.path === '/login') {
      return next({ path: '/' })
    } else {
      return next()
    }
  } else if (whiteList.indexOf(to.path) !== -1) {
    return next()
  } else {
    //return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    return next(`${szLoginLink}?redirect=${encodeURIComponent(to.fullPath)}`)
  }
  }else{
  const isMobileDevice = isMobile();
  if (!isMobileDevice && to.path.startsWith('/web')) {
    const target = getTargetPath(to.fullPath, false);
    if (getToken()) {
      if (target !== to.fullPath) {
        if (target.path === '/login') {
          return next({ path: '/' })
        } else {
          return next(target)
        }
      }
    } else if (whiteList.indexOf(target) !== -1) {
      return next(target)
    } else {
      //return next(`/login?redirect=${encodeURIComponent(target)}`)
      return next(`${szLoginLink}?redirect=${encodeURIComponent(target)}`)
    }
  }
  //移动端
  if (isMobileDevice && !to.path.startsWith('/web')) {
    const target = getTargetPath(to.fullPath, true);
    /*if (target !== to.fullPath) {
      next(target);
      return;
    }*/

    if (getToken()) {
      if (target !== to.fullPath) {
        if (target.path === '/web/login') {
          return next({ path: '/web' })
        } else {
          return next(target)
        }
      }
    } else if (whiteList.indexOf(target) !== -1) {
      return next(target)
    } else {
      //return next(`/web/login?redirect=${encodeURIComponent(target)}`)
      return next(`${szLoginLink}?redirect=${encodeURIComponent(target)}`)
    }
  }
  next();
}

})