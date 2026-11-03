const api = require('../../utils/api')
const storeUtil = require('../../utils/store')
const ph = require('../../utils/placeholder')
const { priceText } = require('../../utils/format')

Page({
  data: {
    store: null,
    categories: [],
    grouped: {},
    products: [],
    currentCat: 0,
    intoView: '',
    cart: null,
    cartEmpty: true,
    loading: true,
    // 锁定联动：点击分类时短暂忽略 scroll 事件
    lockScroll: false,
    // 标记图片加载失败
    imgErr: {}
  },

  onLoad(query) { this.storeId = query.storeId ? Number(query.storeId) : null },

  onShow() {
    const cur = storeUtil.getCurrent()
    if (!cur) {
      // 缺门店：跳到门店页
      wx.showModal({
        title: '请先选择门店',
        content: '先去选一家心仪的门店吧',
        showCancel: false,
        success: () => wx.navigateTo({ url: '/pages/store/store' })
      })
      return
    }
    this.storeId = cur.id
    this.setData({ store: cur })
    wx.setNavigationBarTitle({ title: cur.name })
    this.load()
  },

  async load() {
    this.setData({ loading: true })
    try {
      const [menu, cart] = await Promise.all([
        api.storeMenu(this.storeId),
        api.cart(this.storeId).catch(() => ({ totalQuantity: 0, totalAmount: 0, items: [] }))
      ])
      const cats = (menu.categories || []).slice().sort((a, b) => a.sort - b.sort)
      const products = (menu.products || []).slice().sort((a, b) => a.sort - b.sort)
      // 分组
      const grouped = {}
      cats.forEach(c => { grouped[c.id] = [] })
      products.forEach(p => {
        const arr = grouped[p.categoryId] || (grouped[p.categoryId] = [])
        const cat = cats.find(c => c.id === p.categoryId)
        arr.push({ ...p, _ph: ph.product(p, cat && cat.name) })
      })
      this.setData({
        categories: cats,
        grouped,
        products,
        cart,
        cartEmpty: !(cart && cart.totalQuantity > 0),
        currentCat: 0,
        intoView: cats.length ? 'cat-' + cats[0].id : '',
        loading: false
      })
      // 缓存分类锚点位置，用于 scroll 联动
      setTimeout(() => this.measureTops(), 50)
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  measureTops() {
    const q = wx.createSelectorQuery().in(this)
    q.selectAll('.sec').boundingClientRect()
    q.select('.list-scroll').boundingClientRect()
    q.select('.list-scroll').scrollOffset()
    q.exec(res => {
      const list = res[0] || []
      const container = res[1] || { top: 0 }
      const offset = (res[2] || {}).scrollTop || 0
      this.tops = list.map(r => r.top - container.top + offset)
    })
  },

  onCatTap(e) {
    const idx = e.currentTarget.dataset.index
    const cat = this.data.categories[idx]
    if (!cat) return
    this.setData({ currentCat: idx, intoView: 'cat-' + cat.id, lockScroll: true })
    setTimeout(() => { this.lockScroll = false }, 400)
  },

  onListScroll(e) {
    if (this.lockScroll) return
    if (!this.tops || !this.tops.length) return
    const top = e.detail.scrollTop
    let idx = 0
    for (let i = 0; i < this.tops.length; i++) {
      if (top + 10 >= this.tops[i]) idx = i
      else break
    }
    if (idx !== this.data.currentCat) this.setData({ currentCat: idx })
  },

  goStore() { wx.navigateTo({ url: '/pages/store/store' }) },

  goProduct(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/product/product?id=' + id + '&storeId=' + this.storeId })
  },

  goCart() { wx.navigateTo({ url: '/pages/cart/cart?storeId=' + this.storeId }) },

  onImgErr(e) {
    const id = e.currentTarget.dataset.id
    this.setData({ ['imgErr.' + id]: true })
  }
})
