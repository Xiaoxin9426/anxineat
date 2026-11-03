const api = require('../../utils/api')
const storeUtil = require('../../utils/store')
const ph = require('../../utils/placeholder')
const { priceText, safeJson } = require('../../utils/format')

Page({
  data: {
    storeId: 0,
    store: null,
    cart: null,
    loading: true,
    unavail: []
  },

  onLoad(q) { this.setData({ storeId: Number(q.storeId) || (storeUtil.getCurrent() || {}).id || 0 }) },
  onShow() { this.load() },

  async load() {
    if (!this.data.storeId) { this.setData({ loading: false }); return }
    this.setData({ loading: true })
    try {
      const cart = await api.cart(this.data.storeId)
      const items = (cart.items || []).map(it => {
        const spec = safeJson(it.specSnapshot, [])
        const top = safeJson(it.toppingSnapshot, [])
        return Object.assign({}, it, {
          specText: spec.map(s => s.label).join(' / '),
          topText: top.map(t => t.name + ' x' + t.count).join(' / ')
        })
      })
      const unavail = items.filter(it => !it.available)
      this.setData({
        cart: Object.assign({}, cart, { items }),
        unavail,
        store: storeUtil.getCurrent(),
        loading: false
      })
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  async onQty(e) {
    const { id, op } = e.currentTarget.dataset
    const it = this.data.cart.items.find(x => x.id === id)
    if (!it) return
    const next = it.quantity + (op === 'inc' ? 1 : -1)
    if (next < 0) return
    if (next > (it.maxQuantity || 99)) return wx.showToast({ title: '已达最大购买量', icon: 'none' })
    try {
      const cart = await api.cartUpdate(id, { quantity: next })
      const items = (cart.items || []).map(it => {
        const spec = safeJson(it.specSnapshot, [])
        const top = safeJson(it.toppingSnapshot, [])
        return Object.assign({}, it, {
          specText: spec.map(s => s.label).join(' / '),
          topText: top.map(t => t.name + ' x' + t.count).join(' / ')
        })
      })
      this.setData({ cart: Object.assign({}, cart, { items }), unavail: items.filter(x => !x.available) })
    } catch (err) {}
  },

  async onDelete(e) {
    const id = e.currentTarget.dataset.id
    try {
      const cart = await api.cartDelete(id)
      const items = (cart.items || []).map(it => {
        const spec = safeJson(it.specSnapshot, [])
        const top = safeJson(it.toppingSnapshot, [])
        return Object.assign({}, it, {
          specText: spec.map(s => s.label).join(' / '),
          topText: top.map(t => t.name + ' x' + t.count).join(' / ')
        })
      })
      this.setData({ cart: Object.assign({}, cart, { items }), unavail: items.filter(x => !x.available) })
    } catch (err) {}
  },

  onClear() {
    wx.showModal({
      title: '清空购物车',
      content: '将清空当前门店所有商品，确定吗？',
      success: async (r) => {
        if (!r.confirm) return
        try {
          await api.cartClear(this.data.storeId)
          this.load()
        } catch (e) {}
      }
    })
  },

  goConfirm() {
    if (this.data.unavail.length) {
      return wx.showToast({ title: '请移除不可用商品', icon: 'none' })
    }
    if (!this.data.cart || this.data.cart.totalQuantity <= 0) {
      return wx.showToast({ title: '购物车为空', icon: 'none' })
    }
    wx.navigateTo({ url: '/pages/confirm/confirm?storeId=' + this.data.storeId })
  }
})
