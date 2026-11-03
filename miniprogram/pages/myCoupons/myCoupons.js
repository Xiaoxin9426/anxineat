const api = require('../../utils/api')

const TABS = [
  { key: 'UNUSED', label: '未使用' },
  { key: 'USED', label: '已使用' },
  { key: 'EXPIRED', label: '已过期' }
]

Page({
  data: {
    tabs: TABS,
    tab: 'UNUSED',
    list: [],
    loading: true
  },

  onShow() { this.load() },

  onTab(e) {
    const t = e.currentTarget.dataset.tab
    if (t === this.data.tab) return
    this.setData({ tab: t, list: [], loading: true })
    this.load()
  },

  async load() {
    try {
      const list = await api.myCoupons(this.data.tab)
      this.setData({ list: list || [], loading: false })
    } catch (e) { this.setData({ loading: false }) }
  }
})
