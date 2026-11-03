const api = require('../../utils/api')
const { parseDateTime, countdownText, formatShort, safeJson, statusLabel } = require('../../utils/format')

Page({
  data: {
    tab: 'UNPAID',          // UNPAID | ONGOING | DONE
    list: [],
    page: 1, size: 10,
    pages: 1,
    loading: true,
    finished: false,
    // 倒计时
    cd: {}
  },

  onShow() {
    const t = wx.getStorageSync('anxineat_order_tab')
    if (t) { wx.removeStorageSync('anxineat_order_tab'); this.setData({ tab: t }) }
    this.reload()
  },

  onPullDownRefresh() {
    this.reload().then(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.finished) return
    if (this.data.page >= this.data.pages) {
      this.setData({ finished: true })
      return
    }
    this.loadMore()
  },

  onTab(e) {
    const t = e.currentTarget.dataset.tab
    if (t === this.data.tab) return
    this.setData({ tab: t, list: [], page: 1, pages: 1, finished: false, loading: true })
    this.reload()
  },

  async reload() {
    this.setData({ list: [], page: 1, pages: 1, finished: false, loading: true })
    await this.fetch()
  },

  async loadMore() {
    this.setData({ page: this.data.page + 1 })
    await this.fetch(true)
  },

  async fetch(append) {
    try {
      const r = await api.orderList({ status: this.data.tab, page: this.data.page, size: this.data.size })
      const items = (r.records || []).map(o => this._map(o))
      const list = append ? this.data.list.concat(items) : items
      this.setData({
        list,
        page: r.current || this.data.page,
        pages: r.pages || 1,
        loading: false,
        finished: (r.current || 1) >= (r.pages || 1)
      })
      this.tickCountdown()
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  _map(o) {
    const items = (o.items || []).map(it => {
      const spec = safeJson(it.specSnapshot, [])
      const top = safeJson(it.toppingSnapshot, [])
      return Object.assign({}, it, {
        specText: spec.map(s => s.label).join(' / '),
        topText: top.map(t => t.name + ' x' + t.count).join(' / ')
      })
    })
    return Object.assign({}, o, { items, _totalQty: items.reduce((a, b) => a + (b.quantity || 0), 0) })
  },

  onCountdownTick() {
    this.tickCountdown()
  },

  tickCountdown() {
    const cd = {}
    this.data.list.forEach(o => {
      if (o.status === 'PENDING_PAYMENT' && o.expireTime) {
        const ts = parseDateTime(o.expireTime)
        if (ts) {
          const left = ts.getTime() - Date.now()
          if (left > 0) cd[o.id] = countdownText(ts.getTime())
          else cd[o.id] = '已超时'
        }
      }
    })
    this.setData({ cd })
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/orderDetail/orderDetail?id=' + id })
  },

  async onCancel(e) {
    const id = e.currentTarget.dataset.id
    try { await api.cancelOrder(id); this.reload() } catch (e) {}
  },

  async onPay(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/payment/payment?orderId=' + id })
  }
})
