import Cookies from 'js-cookie'
import cache from '@/plugins/cache'


const TokenKey = 'Admin-Token'
const ExpiresInKey = 'Admin-Expires-In'
const UserKey = 'Admin-User'

const RememberKey = 'SQ-Remember';

export function getToken() {
  return Cookies.get(TokenKey)
}

export function setToken(token) {
  const normalizedToken = token && token.startsWith('Bearer ')
    ? token.slice(7)
    : token
  return Cookies.set(TokenKey, normalizedToken)
}

export function removeToken() {
  return Cookies.remove(TokenKey)
}

export function getExpiresIn() {
  return Cookies.get(ExpiresInKey) || -1
}

export function setExpiresIn(time) {
  return Cookies.set(ExpiresInKey, time)
}

export function removeExpiresIn() {
  return Cookies.remove(ExpiresInKey)
}

export function getUserInfo() {
  return cache.local.getJSON(UserKey)
}

export function setUserInfo(userInfo) {
  if (userInfo != null) {
    cache.local.setJSON(UserKey, userInfo)
  }
}

export function removeUserInfo() {
  cache.local.remove(UserKey)
}

export function clearAuth() {
  removeToken()
  removeExpiresIn()
  removeUserInfo()
}

export function getRememberedName() {
  try {
    return localStorage.getItem(RememberKey) || '';
  } catch (e) {
    return '';
  }
}

export function setRememberedName(name) {
  try {
    if (name) localStorage.setItem(RememberKey, name);
    else localStorage.removeItem(RememberKey);
  } catch (e) {
    /* ignore */
  }
}


export function isAdmin(){
    const userInfo = getUserInfo() || {}
    const displayName = userInfo.username || userInfo.name || '用户'

    const superAdminUser = window.CONFIG.APP_Super_Manager
    let superAdminList = []
    if(superAdminUser) superAdminList = superAdminUser.split(',')

    const adminUser = window.CONFIG.APP_Manager
    let adminList = []
    if(adminList) adminList = adminUser.split(',')

    const admin = adminList.concat(superAdminList);

    if(admin.findIndex(item=>item == displayName) > -1){
      return true
    }
    return false
}

export function isSuperAdmin(){
    const userInfo = getUserInfo() || {}
    const displayName = userInfo.username || userInfo.name || '用户'

    const superAdminUser = window.CONFIG.APP_Super_Manager
    let superAdminList = []
    if(superAdminUser) superAdminList = superAdminUser.split(',')

    if(superAdminList.findIndex(item=>item == displayName) > -1){
      return true
    }
    return false
}