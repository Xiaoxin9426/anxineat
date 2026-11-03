const api = require('../../utils/api')
const storeUtil = require('../../utils/store')

Page({
  data: {
    productId: 0,
    storeId: 0,
    detail: null,
    selectedSpecs: {},     // { groupId: optionId }
    toppings: [],          // [{id,name,price,maxCount,stock,_count}]
    quantity: 1,
    imgErr: false,
    loading: true,
    unitPrice: 0,
    totalPrice: 0,
    requiredReady: false
  },

  onLoad(q) {
    this.setData({ productId: Number(q.id), storeId: Number(q.storeId) })
  },

  onShow() {
    if (!this.data.storeId) {
      const cur = storeUtil.getCurrent()
      if (cur) this.setData({ storeId: cur.id })
    }
    this.load()
  },

  async load() {
    this.setData({ loading: true })
    try {
      const d = await api.productDetail(this.data.productId, this.data.storeId)
      // 选默认
      const sel = {}
      ;(d.specGroups || []).forEach(g => {
        const def = (g.options || []).find(o => o.isDefault) || (g.options || [])[0]
        if (def) sel[g.id] = def.id
      })
      const tops = (d.toppings || []).map(t => Object.assign({}, t, { _count: 0 }))
      this.setData({
        detail: d, selectedSpecs: sel, toppings: tops,
        quantity: 1, imgErr: false, loading: false
      })
      this.recalc()
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  /**
   * 计算单价 / 总价
   * 关键逻辑：specGroups 中 code==='CUP' 的组是「库存维度」，
   * 该组由 SKU 价格承担，不重复加 extraPrice；
   * 其他规格组的 extraPrice 累加；小料按 price*_count 累加。
   */
  recalc() {
    const d = this.data.detail
    if (!d) return
    let unit = 0
    // 1) SKU 价（必须已选 CUP）
    const cupGroup = (d.specGroups || []).find(g => g.code === 'CUP')
    let skuPrice = 0
    if (cupGroup) {
      const optId = this.data.selectedSpecs[cupGroup.id]
      const opt = (cupGroup.options || []).find(o => o.id === optId)
      if (opt) {
        const sku = (d.skus || []).find(s => s.cupSize === opt.label)
        skuPrice = sku ? sku.price : (d.storePrice || 0)
      }
    } else {
      skuPrice = d.storePrice || 0
    }
    unit += skuPrice
    // 2) 非 CUP 规格 extraPrice
    ;(d.specGroups || []).forEach(g => {
      if (g.code === 'CUP') return
      const optId = this.data.selectedSpecs[g.id]
      if (!optId) return
      const opt = (g.options || []).find(o => o.id === optId)
      if (opt) unit += (opt.extraPrice || 0)
    })
    // 3) 小料
    let top = 0
    this.data.toppings.forEach(t => { top += (t._count || 0) * (t.price || 0) })
    unit += top
    this.setData({
      unitPrice: unit,
      totalPrice: unit * this.data.quantity,
      requiredReady: this.isRequiredReady()
    })
  },

  /** 是否所有必选规格都已选（写到 data 上供模板使用） */
  isRequiredReady() {
    const d = this.data.detail
    if (!d) return false
    for (const g of (d.specGroups || [])) {
      if (g.isRequired && !this.data.selectedSpecs[g.id]) return false
    }
    return true
  },

  onSelectSpec(e) {
    const { groupId, optionId } = e.currentTarget.dataset
    const sel = Object.assign({}, this.data.selectedSpecs, { [groupId]: optionId })
    this.setData({ selectedSpecs: sel })
    this.recalc()
  },

  onTopInc(e) {
    const id = e.currentTarget.dataset.id
    const tops = this.data.toppings.map(t => {
      if (t.id !== id) return t
      if ((t._count || 0) >= (t.maxCount || 0)) return t
      return Object.assign({}, t, { _count: (t._count || 0) + 1 })
    })
    this.setData({ toppings: tops })
    this.recalc()
  },
  onTopDec(e) {
    const id = e.currentTarget.dataset.id
    const tops = this.data.toppings.map(t => {
      if (t.id !== id) return t
      if ((t._count || 0) <= 0) return t
      return Object.assign({}, t, { _count: (t._count || 0) - 1 })
    })
    this.setData({ toppings: tops })
    this.recalc()
  },

  onInc() {
    if (this.data.quantity >= 99) return
    this.setData({ quantity: this.data.quantity + 1 })
    this.recalc()
  },
  onDec() {
    if (this.data.quantity <= 1) return
    this.setData({ quantity: this.data.quantity - 1 })
    this.recalc()
  },

  /** 是否所有必选规格都已选 */
  isRequiredReady() {
    const d = this.data.detail
    if (!d) return false
    for (const g of (d.specGroups || [])) {
      if (g.isRequired && !this.data.selectedSpecs[g.id]) return false
    }
    return true
  },

  async onAdd() {
    if (!this.data.detail) return
    if (!this.data.detail.available) return wx.showToast({ title: this.data.detail.soldOutReason || '商品不可售', icon: 'none' })
    if (!this.isRequiredReady()) return wx.showToast({ title: '请先选择必选规格', icon: 'none' })
    const d = this.data.detail
    const cupGroup = (d.specGroups || []).find(g => g.code === 'CUP')
    let skuId = null
    if (cupGroup) {
      const optId = this.data.selectedSpecs[cupGroup.id]
      const opt = (cupGroup.options || []).find(o => o.id === optId)
      const sku = (d.skus || []).find(s => s.cupSize === opt.label)
      skuId = sku ? sku.id : null
    } else {
      // 非 CUP：选第一个 SKU 或用 storePrice 对应的 SKU
      skuId = (d.skus && d.skus[0] && d.skus[0].id) || null
    }
    if (!skuId) return wx.showToast({ title: '未找到对应规格', icon: 'none' })
    // 规格选项 id 列表
    const specOptionIds = Object.values(this.data.selectedSpecs)
    const toppings = this.data.toppings.filter(t => (t._count || 0) > 0)
      .map(t => ({ toppingId: t.id, count: t._count }))
    try {
      const cart = await api.cartAdd({
        storeId: this.data.storeId,
        productId: this.data.productId,
        skuId,
        quantity: this.data.quantity,
        specOptionIds,
        toppings
      })
      wx.showToast({ title: '已加入购物车', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 500)
    } catch (e) {
      // 业务码已由 request 自动 toast
    }
  },

  onImgErr() { this.setData({ imgErr: true }) }
})
