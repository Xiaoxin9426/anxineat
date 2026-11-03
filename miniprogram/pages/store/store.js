const api = require('../../utils/api')
const storeUtil = require('../../utils/store')
const { baseUrl } = require('../../config/index')
const { distanceText, priceText } = require('../../utils/format')

Page({
  data: {
    stores: [],
    keyword: '',
    city: '郑州市',
    locating: false,
    loading: true,
    currentStoreId: 0,
    // 加载失败原因（非空时页面显示错误态 + 重试按钮，杜绝静默空列表）
    error: ''
  },

  onLoad() { this.load() },
  onShow() { this.setData({ currentStoreId: (storeUtil.getCurrent() || {}).id || 0 }) },

  async load(lat, lng) {
    this.setData({ loading: true, error: '' })
    try {
      const params = { city: this.data.city }
      if (lat && lng) { params.lat = lat; params.lng = lng }
      if (this.data.keyword) params.keyword = this.data.keyword
      const list = await api.nearbyStore(params)
      this.setData({ stores: list || [], loading: false })
    } catch (e) {
      // 真机连不上后端（localhost 指向手机自身 / 换了 WiFi 后 IP 变动）时给出可行动的提示
      this.setData({
        stores: [],
        loading: false,
        error: '无法连接后端服务 ' + baseUrl + '。请确认：① 手机与电脑连同一 WiFi；② config/index.js 的 LAN_HOST 为电脑当前局域网 IP；③ 开发者工具已勾选「不校验合法域名」。'
      })
    }
  },

  onRetry() { this.load() },

  onLocate() {
    this.setData({ locating: true })
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        getApp().globalData.location = { lat: res.latitude, lng: res.longitude }
        this.load(res.latitude, res.longitude)
      },
      fail: () => {
        wx.showToast({ title: '未授权定位，按城市搜索', icon: 'none' })
        this.load()
      },
      complete: () => this.setData({ locating: false })
    })
  },

  onSearch(e) {
    this.setData({ keyword: e.detail.value })
    this.load()
  },

  onClearSearch() {
    this.setData({ keyword: '' })
    this.load()
  },

  async onPick(e) {
    const id = e.currentTarget.dataset.id
    // 字符串化比较，规避 dataset 在部分基础库下退化为 string 的类型陷阱
    const target = (this.data.stores || []).find(s => String(s.id) === String(id))
    if (!target) return
    if (target.businessStatus === 0 || !target.openNow) {
      return wx.showToast({ title: target.closedReason || '门店暂未营业', icon: 'none' })
    }
    try {
      const ok = await storeUtil.switchTo(target, this)
      if (!ok) return
      wx.showToast({ title: '已切换门店', icon: 'success' })
      setTimeout(() => {
        wx.switchTab({ url: '/pages/menu/menu' })
      }, 600)
    } catch (err) {
      // 任何异常都必须有反馈，绝不静默失败
      wx.showToast({ title: (err && err.message) || '切换失败，请重试', icon: 'none' })
    }
  }
})
