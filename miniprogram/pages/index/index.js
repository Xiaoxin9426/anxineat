const api = require('../../utils/api')
const storeUtil = require('../../utils/store')
const ph = require('../../utils/placeholder')

Page({
  data: {
    banners: [],
    currentStore: null,
    user: null,
    signed: false,
    loading: true
  },

  onLoad() { this.load() },
  onShow() {
    this.setData({
      currentStore: storeUtil.getCurrent(),
      user: getApp().globalData.user
    })
  },

  async load() {
    this.setData({ loading: true })
    try {
      const [banners, profile, store] = await Promise.all([
        api.bannerList().catch(() => []),
        getApp().refreshUser(),
        Promise.resolve(storeUtil.getCurrent())
      ])
      const list = (banners || []).map(b => ({ ...b, _ph: ph.banner(b) }))
      const u = profile || getApp().globalData.user
      this.setData({
        banners: list,
        user: u,
        currentStore: store,
        signed: false, // 签到态以 onShow 触发的 signin status 为准
        loading: false
      })
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  onBannerTap(e) {
    const { type, value } = e.currentTarget.dataset
    if (type === 'COUPON') { wx.switchTab({ url: '/pages/profile/profile' }); wx.navigateTo({ url: '/pages/coupons/coupons' }) }
    else if (type === 'POINT') { wx.navigateTo({ url: '/pages/points/points' }) }
    else if (type === 'PRODUCT') { wx.navigateTo({ url: '/pages/menu/menu' }) }
  },

  goMenu() {
    wx.switchTab({ url: '/pages/menu/menu' })
  },

  goStore() { wx.navigateTo({ url: '/pages/store/store' }) },

  onGotoCoupons() { wx.navigateTo({ url: '/pages/coupons/coupons' }) },
  onGotoMyCoupons() { wx.navigateTo({ url: '/pages/myCoupons/myCoupons' }) },

  async onSignin() {
    try {
      const r = await api.signin()
      wx.showToast({ title: '签到成功 +' + (r.awarded || 0) + ' 积分', icon: 'success' })
      getApp().refreshUser().then(u => this.setData({ user: u, signed: true }))
    } catch (e) {
      if (e && e.code === 4008) {
        this.setData({ signed: true })
        wx.showToast({ title: '今日已签到', icon: 'none' })
      }
    }
  },

  onReachBottom() {}
})
