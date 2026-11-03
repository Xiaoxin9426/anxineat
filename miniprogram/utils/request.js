/**
 * 统一请求封装
 * - 自动注入 Authorization
 * - 收到 code=1002 自动重新登录并重试一次
 * - code 非 0 走 toast 提示，调用方可传 silent:true 关闭
 * - 集中错误码：4010/3004/2012/2011 等由调用方单独处理
 *
 * 登录双模（由后端 /api/config/client 的 wxRealLogin 自动决定，无需改代码）：
 *   wxRealLogin = true  → 真实 wx.login，用 code 换 openid（需后端配置 appid/appsecret）
 *   wxRealLogin = false → 本地 mock 登录（mock: 前缀），未配置凭证时自动回落，保证可联调
 */
const { baseUrl, storageKeys, mockLoginCode } = require('../config/index')

function getToken() { return wx.getStorageSync(storageKeys.token) || '' }
function setToken(t) { t ? wx.setStorageSync(storageKeys.token, t) : wx.removeStorageSync(storageKeys.token) }
function getUser() { return wx.getStorageSync(storageKeys.user) || null }
function setUser(u) { u ? wx.setStorageSync(storageKeys.user, u) : wx.removeStorageSync(storageKeys.user) }

/**
 * 拉取后端公开配置（免鉴权，不会触发登录递归）。
 * 结果缓存，只在首次登录时请求一次。
 */
let clientConfigCache = null
function getClientConfig() {
  if (clientConfigCache) return Promise.resolve(clientConfigCache)
  return new Promise((resolve) => {
    wx.request({
      url: baseUrl + '/config/client',
      method: 'GET',
      success: (r) => {
        const body = r.data || {}
        clientConfigCache = (r.statusCode === 200 && body.code === 0) ? (body.data || {}) : {}
        resolve(clientConfigCache)
      },
      fail: () => {
        clientConfigCache = {}
        resolve(clientConfigCache)
      }
    })
  })
}

/** 把 code 换 token，并写入 storage / globalData */
function postLogin(code) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: baseUrl + '/auth/login',
      method: 'POST',
      header: { 'content-type': 'application/json' },
      data: { code },
      success: (r) => {
        const body = r.data || {}
        if (r.statusCode === 200 && body.code === 0 && body.data && body.data.token) {
          setToken(body.data.token)
          setUser(body.data.user || null)
          const app = getApp && getApp()
          if (app) {
            app.globalData.token = body.data.token
            app.globalData.user = body.data.user || null
          }
          resolve(body.data)
        } else {
          reject({ code: body.code || -1, message: body.message || '登录失败' })
        }
      },
      fail: () => reject({ code: -1, message: '网络异常，登录失败' })
    })
  })
}

/** 真实微信登录：wx.login 拿 code → 后端 code2Session 换 openid */
function loginByWx() {
  return new Promise((resolve, reject) => {
    wx.login({
      success: (res) => {
        if (!res.code) return reject({ code: -1, message: 'wx.login 未返回 code' })
        postLogin(res.code).then(resolve, reject)
      },
      fail: (err) => reject({
        code: -1,
        message: 'wx.login 失败，请检查开发者工具 AppID：' + ((err && err.errMsg) || '')
      })
    })
  })
}

// 防止并发触发多个登录请求
let loginPromise = null
function doLogin() {
  if (loginPromise) return loginPromise
  loginPromise = getClientConfig()
    .then((cfg) => (cfg && cfg.wxRealLogin) ? loginByWx() : postLogin(mockLoginCode))
    .then((data) => {
      loginPromise = null
      return data
    })
    .catch((e) => {
      loginPromise = null
      console.error('[anxineat] 登录失败:', e && e.message)
      throw e
    })
  return loginPromise
}

// 简易 toast 防重叠
let lastToast = 0
function toast(msg, icon) {
  const now = Date.now()
  if (now - lastToast < 1500) return
  lastToast = now
  wx.showToast({ title: String(msg || '').slice(0, 24) || '操作失败', icon: icon || 'none', duration: 1800 })
}

/**
 * 发起请求
 * @param {object} opt
 * @param {string} opt.url 不含 baseUrl，如 '/cart/add'
 * @param {string} [opt.method='GET']
 * @param {object} [opt.data]
 * @param {boolean} [opt.auth=true] 是否带 token
 * @param {boolean} [opt.toast=true] 是否自动 toast 错误
 * @param {boolean} [opt.loading=false] 是否显示 loading
 * @param {boolean} [opt.retry=true] 1002 时是否重试一次
 */
function request(opt) {
  const { url, method = 'GET', data, auth = true, toast: enableToast = true, loading = false, retry = true, header = {} } = opt || {}
  return new Promise((resolve, reject) => {
    const send = (isRetry) => {
      const h = Object.assign({ 'content-type': 'application/json' }, header)
      if (auth) {
        const tk = getToken()
        if (tk) h['Authorization'] = 'Bearer ' + tk
      }
      if (loading) wx.showLoading({ title: '加载中', mask: true })
      wx.request({
        url: baseUrl + url,
        method,
        data,
        header: h,
        success: async (res) => {
          const body = res.data || {}
          // HTTP 失败
          if (res.statusCode < 200 || res.statusCode >= 300) {
            const msg = '网络异常（' + res.statusCode + '）'
            if (enableToast) toast(msg)
            return reject({ code: res.statusCode, message: msg, raw: body })
          }
          // 业务失败
          if (body.code !== 0) {
            // token 失效：清 token、重新登录、重试一次
            if (body.code === 1002 && auth && retry && !isRetry) {
              setToken('')
              try {
                await doLogin()
                return send(true)
              } catch (e) {
                // 透出后端真实原因（如「appid/appsecret 未配置」），便于定位
                if (enableToast) toast((e && e.message) || '登录已过期，请重试')
                return reject({ code: 1002, message: (e && e.message) || '未登录' })
              }
            }
            if (enableToast) toast(body.message || '操作失败')
            return reject({ code: body.code, message: body.message || '操作失败', data: body.data })
          }
          resolve(body.data)
        },
        fail: (err) => {
          const msg = (err && err.errMsg) || '网络异常'
          if (enableToast) toast(msg.indexOf('url not in domain list') >= 0 ? '请在开发者工具关闭域名校验' : '网络异常')
          reject({ code: -1, message: msg })
        },
        complete: () => { if (loading) wx.hideLoading() }
      })
    }
    send(false)
  })
}

const get = (url, opt) => request(Object.assign({ url, method: 'GET' }, opt))
const post = (url, data, opt) => request(Object.assign({ url, method: 'POST', data }, opt))
const put = (url, data, opt) => request(Object.assign({ url, method: 'PUT', data }, opt))
const del = (url, opt) => request(Object.assign({ url, method: 'DELETE' }, opt))

module.exports = {
  request, get, post, put, del,
  doLogin, getToken, setToken, getUser, setUser,
  getClientConfig, toast
}
