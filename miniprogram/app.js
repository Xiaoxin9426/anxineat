/**
 * app.js
 * - 启动时若本地无 token 就自动登录（后端 dev 模式用 mock code）
 * - 拉取客户端配置，存到 globalData
 * - 维护 currentStore
 */
const { storageKeys } = require('./config/index')
const api = require('./utils/api')
const req = require('./utils/request')
const storeUtil = require('./utils/store')

App({
  globalData: {
    token: '',
    user: null,
    currentStore: null,
    clientConfig: null,
    // 启动定位（用户在 store 页面拒绝后此值为 null）
    location: null
  },

  onLaunch() {
    // 读取本地
    this.globalData.token = wx.getStorageSync(storageKeys.token) || ''
    this.globalData.user = wx.getStorageSync(storageKeys.user) || null
    this.globalData.currentStore = wx.getStorageSync(storageKeys.currentStore) || null

    // 拉取客户端配置（失败不影响主流程）
    api.clientConfig().then(cfg => {
      this.globalData.clientConfig = cfg || null
    }).catch(() => {})

    // 登录（dev/mock 自动成功）
    this.ensureLogin().catch(err => {
      console.warn('[anxineat] 启动登录失败', err)
    })
  },

  /** 保证已登录；并发安全 */
  ensureLogin() {
    if (this.globalData.token) return Promise.resolve(this.globalData.user)
    return req.doLogin().then(data => {
      this.globalData.token = data.token
      this.globalData.user = data.user
      return data.user
    })
  },

  /** 更新用户资料（如积分变动后） */
  refreshUser() {
    return api.userProfile({ toast: false }).then(u => {
      this.globalData.user = u
      wx.setStorageSync(storageKeys.user, u)
      return u
    }).catch(() => null)
  },

  /** 切换门店（封装一层） */
  setStore(s) { storeUtil.setCurrent(s); this.globalData.currentStore = s },
  getStore() { return this.globalData.currentStore },
  getStoreId() { return this.globalData.currentStore && this.globalData.currentStore.id },

  /** 应用级错误兜底 */
  onError(err) { console.error('[anxineat] onError', err) }
})
