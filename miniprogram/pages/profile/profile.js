const api = require('../../utils/api')
const storeUtil = require('../../utils/store')
const ph = require('../../utils/placeholder')
const { storageKeys } = require('../../config/index')

Page({
  data: {
    user: null,
    store: null,
    signed: false,
    avatar: { bg: 'linear-gradient(135deg,#FFB199,#E4002B)', emoji: '👤' },
    loading: true
  },

  onShow() {
    const u = getApp().globalData.user
    this.setData({ user: u, store: storeUtil.getCurrent(), avatar: ph.avatar(u) })
    getApp().refreshUser().then(nu => this.setData({ user: nu, avatar: ph.avatar(nu) }))
  },

  goTab(e) {
    const tab = e.currentTarget.dataset.tab
    wx.setStorageSync('anxineat_order_tab', tab)
    wx.switchTab({ url: '/pages/orders/orders' })
  },

  goOrders() { wx.switchTab({ url: '/pages/orders/orders' }) },
  goMyCoupons() { wx.navigateTo({ url: '/pages/myCoupons/myCoupons' }) },
  goPoints() { wx.navigateTo({ url: '/pages/points/points' }) },
  goAddress() { wx.navigateTo({ url: '/pages/address/address' }) },
  goStore() { wx.navigateTo({ url: '/pages/store/store' }) },
  goSignin() { wx.navigateTo({ url: '/pages/points/points' }) },

  onLogout() {
    wx.showModal({
      title: '退出登录', content: '退出后部分功能将不可用，确定吗？',
      success: r => {
        if (!r.confirm) return
        wx.removeStorageSync(storageKeys.token)
        wx.removeStorageSync(storageKeys.user)
        getApp().globalData.token = ''
        getApp().globalData.user = null
        wx.reLaunch({ url: '/pages/welcome/welcome' })
      }
    })
  }
})
