const api = require('../../utils/api')

Page({
  data: {
    list: [],
    loading: true
  },

  onShow() { this.load() },

  async load() {
    this.setData({ loading: true })
    try {
      const list = await api.couponCenter()
      this.setData({ list: list || [], loading: false })
    } catch (e) { this.setData({ loading: false }) }
  },

  async onReceive(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    try {
      const r = await api.couponReceive(id)
      wx.showToast({ title: '已领取', icon: 'success' })
      this.load()
    } catch (err) {}
  }
})
