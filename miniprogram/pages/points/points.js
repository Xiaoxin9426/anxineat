const api = require('../../utils/api')
const { formatShort } = require('../../utils/format')

Page({
  data: {
    user: null,
    signed: false,
    records: [],
    page: 1, size: 20, pages: 1, finished: false,
    exchange: [],
    loading: true
  },

  onShow() { this.load() },

  async load() {
    this.setData({ loading: true })
    try {
      const [u, rec, ex] = await Promise.all([
        getApp().refreshUser(),
        api.pointRecords({ page: 1, size: 20 }),
        api.couponExchangeList().catch(() => [])
      ])
      this.setData({
        user: u,
        records: (rec && rec.records) || [],
        page: (rec && rec.current) || 1,
        pages: (rec && rec.pages) || 1,
        finished: ((rec && rec.current) || 1) >= ((rec && rec.pages) || 1),
        exchange: ex || [],
        loading: false
      })
    } catch (e) { this.setData({ loading: false }) }
  },

  async onSignin() {
    try {
      const r = await api.signin()
      wx.showToast({ title: '签到成功 +' + (r.awarded || 0), icon: 'success' })
      this.load()
    } catch (e) {
      if (e && e.code === 4008) { this.setData({ signed: true }); wx.showToast({ title: '今日已签到', icon: 'none' }) }
    }
  },

  onReachBottom() {
    if (this.data.finished) return
    const next = this.data.page + 1
    api.pointRecords({ page: next, size: 20 }).then(rec => {
      this.setData({
        records: this.data.records.concat(rec.records || []),
        page: rec.current || next,
        finished: (rec.current || next) >= (rec.pages || 1)
      })
    }).catch(() => {})
  },

  async onExchange(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    try {
      const r = await api.pointExchange(id)
      wx.showToast({ title: '兑换成功 -' + (r.pointCost || 0) + ' 积分', icon: 'success' })
      this.load()
    } catch (err) {}
  }
})
