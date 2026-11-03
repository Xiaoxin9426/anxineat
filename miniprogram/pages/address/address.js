const api = require('../../utils/api')
const { storageKeys } = require('../../config/index')

Page({
  data: {
    list: [],
    from: '',
    selectedId: 0,
    loading: true
  },

  onLoad(q) {
    this.setData({
      from: q.from || '',
      selectedId: q.selectedId ? Number(q.selectedId) : 0
    })
  },

  onShow() { this.load() },

  async load() {
    this.setData({ loading: true })
    try {
      const list = await api.addressList()
      this.setData({ list, loading: false })
    } catch (e) { this.setData({ loading: false }) }
  },

  async onDefault(e) {
    const id = e.currentTarget.dataset.id
    try { await api.addressDefault(id); this.load() } catch (err) {}
  },

  onEdit(e) {
    const id = e.currentTarget.dataset.id
    if (id) wx.navigateTo({ url: '/pages/addressEdit/addressEdit?id=' + id })
    else wx.navigateTo({ url: '/pages/addressEdit/addressEdit' })
  },

  onDelete(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '删除地址', content: '确定删除该地址吗？',
      success: async (r) => {
        if (!r.confirm) return
        try { await api.addressDelete(id); this.load() } catch (err) {}
      }
    })
  },

  onPick(e) {
    if (this.data.from !== 'confirm') return
    const id = e.currentTarget.dataset.id
    const a = this.data.list.find(x => x.id === id)
    if (!a) return
    wx.setStorageSync(storageKeys.pickAddress, a)
    wx.navigateBack()
  }
})
