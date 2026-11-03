const api = require('../../utils/api')
const { parseDateTime, countdownText, formatShort, safeJson, statusLabel, priceText } = require('../../utils/format')

let timer = null

Page({
  data: {
    id: 0,
    order: null,
    items: [],
    log: [],
    countdown: '',
    showZoom: false,
    loading: true
  },

  onLoad(q) { this.setData({ id: Number(q.id) }) },
  onUnload() { if (timer) clearInterval(timer) },
  onShow() { this.load() },

  async load() {
    if (timer) clearInterval(timer)
    if (!this.data.id) return
    this.setData({ loading: true })
    try {
      const o = await api.orderDetail(this.data.id)
      const items = (o.items || []).map(it => {
        const spec = safeJson(it.specSnapshot, [])
        const top = safeJson(it.toppingSnapshot, [])
        return Object.assign({}, it, {
          specText: spec.map(s => s.label).join(' / '),
          topText: top.map(t => t.name + ' x' + t.count).join(' / ')
        })
      })
      this.setData({ order: o, items, log: o.statusLog || [], loading: false })
      if (o.status === 'PENDING_PAYMENT' && o.expireTime) {
        const ts = parseDateTime(o.expireTime)
        if (ts) {
          this.expireTs = ts.getTime()
          timer = setInterval(() => {
            const left = this.expireTs - Date.now()
            this.setData({ countdown: left > 0 ? countdownText(this.expireTs) : '00:00' })
          }, 1000)
        }
      }
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  zoom() { this.setData({ showZoom: true }) },
  unzoom() { this.setData({ showZoom: false }) },

  async onCancel() {
    if (!this.data.order) return
    wx.showModal({
      title: '取消订单',
      content: '确定取消该订单？',
      success: async (r) => {
        if (!r.confirm) return
        try { await api.cancelOrder(this.data.order.id); this.load() } catch (e) {}
      }
    })
  },

  onPay() {
    wx.navigateTo({ url: '/pages/payment/payment?orderId=' + this.data.id })
  },

  async onReorder() {
    if (!this.data.order) return
    try {
      const r = await api.reorder(this.data.order.id)
      let msg = '已加入购物车 ' + (r.addedCount || 0) + ' 件'
      if (r.skipped && r.skipped.length) {
        msg += '\n跳过：' + r.skipped.map(s => s.productName + '（' + s.reason + '）').join('、')
      }
      wx.showModal({ title: '再来一单', content: msg, showCancel: false, confirmText: '去购物车',
        success: (m) => { if (m.confirm) wx.switchTab({ url: '/pages/menu/menu' }) }
      })
    } catch (e) {}
  }
})
