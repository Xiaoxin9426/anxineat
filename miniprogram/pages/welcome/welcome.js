const { storageKeys } = require('../../config/index')

const TOTAL = 1500 // 1.5 秒即可跳过（需求文档原「2 秒强制等待」已修订为不应强制）
let timer = null
let left = TOTAL

Page({
  data: { show: true, left: TOTAL, hide: false },

  onLoad() {
    // 非首次启动：直接进首页，不渲染欢迎页
    if (wx.getStorageSync(storageKeys.welcomed)) {
      this.setData({ hide: true })
      this.goHome()
      return
    }
    this.tick()
  },

  tick() {
    timer = setInterval(() => {
      left -= 100
      if (left <= 0) {
        this.skip()
        return
      }
      this.setData({ left })
    }, 100)
  },

  skip() {
    if (timer) clearInterval(timer)
    timer = null
    wx.setStorageSync(storageKeys.welcomed, true)
    this.goHome()
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },

  onUnload() { if (timer) clearInterval(timer) }
})
