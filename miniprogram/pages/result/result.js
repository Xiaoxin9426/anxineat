const api = require('../../utils/api')
const { priceText, formatShort, statusLabel } = require('../../utils/format')

Page({
  data: {
    orderId: 0,
    order: null,
    success: 1,             // 1 成功 0 失败
    cancelled: 0,
    showZoom: false,
    loading: true
  },

  onLoad(q) {
    this.setData({
      orderId: Number(q.orderId),
      success: q.success === '1' || q.success === undefined ? 1 : 0,
      cancelled: q.cancelled === '1' ? 1 : 0
    })
  },

  onShow() { this.load() },

  async load() {
    if (!this.data.orderId) return
    this.setData({ loading: true })
    try {
      const order = await api.orderDetail(this.data.orderId)
      this.setData({ order, loading: false })
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  zoom() { this.setData({ showZoom: true }) },
  unzoom() { this.setData({ showZoom: false }) },

  goOrder() {
    wx.redirectTo({ url: '/pages/orderDetail/orderDetail?id=' + this.data.orderId })
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },

  retry() {
    wx.redirectTo({ url: '/pages/payment/payment?orderId=' + this.data.orderId })
  }
})
