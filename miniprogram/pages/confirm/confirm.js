const api = require('../../utils/api')
const storeUtil = require('../../utils/store')
const { priceText, formatServerTime, formatShort, uuid, pad2 } = require('../../utils/format')
const { storageKeys } = require('../../config/index')

Page({
  data: {
    storeId: 0,
    store: null,
    orderType: 'PICKUP',     // PICKUP | DELIVERY
    fetchMode: 'NOW',        // NOW | BOOK
    fetchTime: '',
    address: null,
    addressList: [],
    addresses: [],
    remark: '',
    couponId: 0,
    coupons: [],
    usePoint: false,
    pointUsable: 0,
    pointDeductAmount: 0,
    pointBalance: 0,
    preview: null,           // 最新试算结果
    loading: true,
    submitting: false,
    showCouponSheet: false,
    seq: 0,                  // preview 请求序号
    now: ''                  // picker 起始时间
  },

  onLoad(q) {
    this.setData({ storeId: Number(q.storeId) || (storeUtil.getCurrent() || {}).id || 0 })
    // picker 不允许选历史时间，给当前时间作为最小值
    const n = new Date()
    this.setData({ now: n.getFullYear() + '-' + pad2(n.getMonth() + 1) + '-' + pad2(n.getDate()) + ' ' + pad2(n.getHours()) + ':' + pad2(n.getMinutes()) })
  },

  async onShow() {
    if (!this.data.storeId) return
    const store = storeUtil.getCurrent()
    // 渠道受 store 支持限制
    let orderType = this.data.orderType
    if (store && !store.supportPickup) orderType = 'DELIVERY'
    if (store && !store.supportDelivery) orderType = 'PICKUP'
    this.setData({ store, orderType })
    // 取用户选过的地址
    const pick = wx.getStorageSync(storageKeys.pickAddress) || null
    if (pick) {
      wx.removeStorageSync(storageKeys.pickAddress)
      this.setData({ address: pick })
    }
    const pickCoupon = wx.getStorageSync(storageKeys.pickCoupon)
    if (pickCoupon !== undefined && pickCoupon !== '') {
      wx.removeStorageSync(storageKeys.pickCoupon)
      this.setData({ couponId: pickCoupon ? Number(pickCoupon) : 0 })
    }
    // 加载地址列表（即便暂不显示，以便切回外卖时能立即渲染）
    try {
      const list = await api.addressList()
      const def = (list || []).find(a => a.isDefault) || (list || [])[0] || null
      this.setData({ addressList: list || [], addresses: list || [] })
      if (!this.data.address && def && this.data.orderType === 'DELIVERY') this.setData({ address: def })
    } catch (e) {}
    await this.recalc()
  },

  /** 任何参数变化都要重新试算 */
  async recalc() {
    if (!this.data.storeId) return
    const seq = ++this.data.seq
    const data = {
      storeId: this.data.storeId,
      orderType: this.data.orderType,
      fromCart: true,
      userCouponId: this.data.couponId || undefined,
      usePoint: this.data.usePoint || false,
      addressId: this.data.address ? this.data.address.id : undefined
    }
    this.setData({ loading: true })
    try {
      const p = await api.preview(data)
      if (seq !== this.data.seq) return // 已被新请求覆盖
      this.setData({
        preview: p,
        coupons: p.availableCoupons || [],
        pointUsable: p.pointUsable || 0,
        pointDeductAmount: p.pointDeductAmount || 0,
        pointBalance: p.pointAvailable || 0,
        loading: false
      })
    } catch (e) {
      if (seq !== this.data.seq) return
      this.setData({ loading: false, preview: null })
    }
  },

  onSwitchType(e) {
    const t = e.currentTarget.dataset.type
    if (t === this.data.orderType) return
    if (t === 'PICKUP' && !this.data.store.supportPickup) return
    if (t === 'DELIVERY' && !this.data.store.supportDelivery) return
    this.setData({ orderType: t })
    if (t === 'DELIVERY' && !this.data.address && this.data.addressList.length) {
      const def = this.data.addressList.find(a => a.isDefault) || this.data.addressList[0]
      this.setData({ address: def })
    }
    this.recalc()
  },

  onSwitchFetch(e) {
    const m = e.currentTarget.dataset.mode
    if (m === this.data.fetchMode) return
    this.setData({ fetchMode: m, fetchTime: '' })
  },

  onPickTime(e) {
    this.setData({ fetchTime: e.detail.value })
  },

  onRemark(e) { this.setData({ remark: e.detail.value }) },

  onTogglePoint(e) {
    this.setData({ usePoint: e.detail.value })
    this.recalc()
  },

  pickAddress() {
    wx.navigateTo({ url: '/pages/address/address?from=confirm&selectedId=' + (this.data.address ? this.data.address.id : '') })
  },

  addAddress() {
    wx.navigateTo({ url: '/pages/addressEdit/addressEdit' })
  },

  openCouponSheet() { this.setData({ showCouponSheet: true }) },
  closeCouponSheet() { this.setData({ showCouponSheet: false }) },
  pickCoupon(e) {
    const id = e.currentTarget.dataset.id || 0
    this.setData({ couponId: id, showCouponSheet: false })
    this.recalc()
  },

  async submit() {
    if (this.data.submitting) return
    const p = this.data.preview
    if (!p) return
    // 校验
    if (this.data.orderType === 'DELIVERY' && !this.data.address) {
      return wx.showToast({ title: '请选择收货地址', icon: 'none' })
    }
    if (p.payAmount == null) return
    this.setData({ submitting: true })
    try {
      const data = {
        storeId: this.data.storeId,
        orderType: this.data.orderType,
        fromCart: true,
        userCouponId: this.data.couponId || undefined,
        usePoint: !!this.data.usePoint,
        addressId: this.data.address ? this.data.address.id : undefined,
        remark: this.data.remark || '',
        expectTime: this.data.fetchMode === 'BOOK' && this.data.fetchTime
          ? this.data.fetchTime + ':00' : undefined,
        idempotentKey: 'ax-' + uuid()
      }
      const r = await api.createOrder(data)
      wx.redirectTo({
        url: '/pages/payment/payment?orderId=' + r.orderId
      })
    } catch (e) {
      // 业务码已自动 toast
    } finally {
      this.setData({ submitting: false })
    }
  }
})
