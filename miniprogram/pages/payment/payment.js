const api = require('../../utils/api')
const { parseDateTime, countdownText, priceText } = require('../../utils/format')

let timer = null

Page({
  data: {
    orderId: 0,
    order: null,
    payNo: '',
    payAmount: 0,
    expireTs: 0,
    countdown: '15:00',
    expired: false,
    paying: false,
    loading: true,
    mock: true,
    prepay: null,
    notice: '这是【模拟支付】，用于本地演示，非真实微信支付。\n点击「支付成功」模拟支付回调成功；\n「支付失败」模拟支付失败；\n「重复回调验证」会重复发同一笔回调，后端通过 idempotentKey 防止重复处理订单状态。'
  },

  onLoad(q) {
    this.setData({ orderId: Number(q.orderId) })
  },

  onUnload() { if (timer) clearInterval(timer) },

  async onShow() {
    if (timer) clearInterval(timer)
    if (!this.data.orderId) return
    this.setData({ loading: true })
    try {
      const [order, pay] = await Promise.all([
        api.orderDetail(this.data.orderId),
        api.payCreate(this.data.orderId)
      ])
      const exp = parseDateTime(pay.expireTime)
      this.setData({
        order,
        payNo: pay.payNo,
        payAmount: pay.amount || order.payAmount,
        expireTs: exp ? exp.getTime() : 0,
        mock: !!pay.mock,
        prepay: pay.mock ? null : (pay.prepay || null),
        loading: false
      })
      this.startTimer()
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  startTimer() {
    if (!this.data.expireTs) return
    timer = setInterval(() => {
      const left = this.data.expireTs - Date.now()
      if (left <= 0) {
        this.setData({ countdown: '00:00', expired: true })
        clearInterval(timer)
        timer = null
        return
      }
      this.setData({ countdown: countdownText(this.data.expireTs) })
    }, 1000)
  },

  // ============ 模拟支付（MOCK 渠道） ============

  async doCallback(result, duplicate) {
    if (this.data.expired && result === 'SUCCESS') {
      return wx.showToast({ title: '订单已超时，请重新下单', icon: 'none' })
    }
    if (this.data.paying) return
    this.setData({ paying: true })
    try {
      const r = await api.payMockCallback({
        payNo: this.data.payNo,
        result,
        duplicate: !!duplicate
      })
      if (result === 'SUCCESS' && !duplicate) {
        wx.redirectTo({
          url: '/pages/result/result?orderId=' + this.data.orderId + '&success=1'
        })
      } else if (result === 'FAIL') {
        wx.redirectTo({
          url: '/pages/result/result?orderId=' + this.data.orderId + '&success=0'
        })
      } else {
        // 重复回调验证：展示幂等结果，再让用户决定是否查看订单
        wx.showModal({
          title: '幂等验证',
          content: (r.duplicateHandled ? '已处理（重复回调未重复变更订单）\n' : '已处理\n') + '订单状态：' + (r.orderStatus || 'PAID'),
          confirmText: '查看订单',
          success: (m) => {
            if (m.confirm) {
              wx.redirectTo({ url: '/pages/result/result?orderId=' + this.data.orderId + '&success=1' })
            }
          }
        })
      }
    } catch (e) {
      // 业务码已自动 toast；3002 状态不允许等
    } finally {
      this.setData({ paying: false })
    }
  },

  onPaySuccess() { this.doCallback('SUCCESS', false) },
  onPayFail() { this.doCallback('FAIL', false) },
  onPayDuplicate() { this.doCallback('SUCCESS', true) },

  // ============ 真实微信支付（WECHAT_JSAPI 渠道） ============

  async onWechatPay() {
    if (this.data.expired) {
      return wx.showToast({ title: '订单已超时，请重新下单', icon: 'none' })
    }
    if (this.data.paying) return
    const prepay = this.data.prepay
    if (!prepay || !prepay.paySign) {
      return wx.showToast({ title: '未获取到支付参数，请重试', icon: 'none' })
    }
    this.setData({ paying: true })
    wx.requestPayment({
      timeStamp: String(prepay.timeStamp),
      nonceStr: prepay.nonceStr,
      package: prepay.package,
      signType: prepay.signType || 'RSA',
      paySign: prepay.paySign,
      success: () => this.pollUntilPaid(),
      fail: (err) => {
        this.setData({ paying: false })
        // errMsg 形如 "requestPayment:fail cancel" 表示用户取消
        const cancelled = err && /cancel/.test(err.errMsg || '')
        wx.showToast({
          title: cancelled ? '已取消支付' : '支付失败',
          icon: 'none'
        })
      }
    })
  },

  pollUntilPaid() {
    let tries = 0
    const maxTries = 12
    const tick = async () => {
      if (tries++ >= maxTries) {
        this.setData({ paying: false })
        wx.showModal({
          title: '支付确认中',
          content: '已提交支付，正在确认结果。若已扣款，稍后可在「我的订单」查看；如未到账请联系商家。',
          showCancel: false
        })
        return
      }
      try {
        const r = await api.payQuery(this.data.orderId)
        if (r && r.orderStatus === 'PAID') {
          this.setData({ paying: false })
          wx.redirectTo({
            url: '/pages/result/result?orderId=' + this.data.orderId + '&success=1'
          })
          return
        }
      } catch (e) { /* 忽略，继续轮询 */ }
      setTimeout(tick, 1500)
    }
    tick()
  },

  async onCancelOrder() {
    wx.showModal({
      title: '取消订单',
      content: '确定要取消该订单吗？',
      success: async (r) => {
        if (!r.confirm) return
        try {
          await api.cancelOrder(this.data.orderId)
          wx.redirectTo({ url: '/pages/result/result?orderId=' + this.data.orderId + '&success=0&cancelled=1' })
        } catch (e) {}
      }
    })
  }
})
