const api = require('../../utils/api')

const TAGS = ['家', '公司', '学校', '其他']

Page({
  data: {
    id: 0,
    name: '',
    phone: '',
    province: '',
    city: '',
    district: '',
    detail: '',
    tag: '',
    isDefault: false,
    tags: TAGS,
    submitting: false
  },

  onLoad(q) {
    if (q.id) {
      this.setData({ id: Number(q.id) })
      this.load(Number(q.id))
    } else {
      // 默认带入当前用户
      const u = getApp().globalData.user
      if (u) this.setData({ name: u.nickname || '', phone: u.phone || '' })
    }
  },

  async load(id) {
    try {
      const list = await api.addressList()
      const a = (list || []).find(x => x.id === id)
      if (a) this.setData(a)
    } catch (e) {}
  },

  onInput(e) {
    const k = e.currentTarget.dataset.k
    this.setData({ [k]: e.detail.value })
  },

  onPickTag(e) {
    this.setData({ tag: e.currentTarget.dataset.t })
  },

  onToggleDefault(e) {
    this.setData({ isDefault: e.detail.value })
  },

  async onSave() {
    if (this.data.submitting) return
    const { name, phone, province, city, district, detail } = this.data
    if (!name || !phone) return wx.showToast({ title: '请填写收件人和电话', icon: 'none' })
    if (!/^1\d{10}$/.test(phone)) return wx.showToast({ title: '手机号格式不正确', icon: 'none' })
    if (!province || !city || !district || !detail) return wx.showToast({ title: '请完善地址', icon: 'none' })
    this.setData({ submitting: true })
    const payload = {
      name, phone, province, city, district, detail, tag: this.data.tag, isDefault: this.data.isDefault
    }
    try {
      if (this.data.id) await api.addressUpdate(this.data.id, payload)
      else await api.addressAdd(payload)
      wx.navigateBack()
    } catch (e) {} finally { this.setData({ submitting: false }) }
  }
})
