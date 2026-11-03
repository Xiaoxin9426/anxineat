/**
 * 全部接口集中封装
 * 文件名/函数名按业务域划分，方便查找。
 */
const { get, post, put, del } = require('./request')

module.exports = {
  // 公开
  health: () => get('/health', { auth: false, toast: false }),
  login: (data) => post('/auth/login', data, { auth: false }),
  nearbyStore: (params) => get('/store/nearby', { data: params, auth: false, toast: false }),
  storeDetail: (id) => get('/store/' + id, { auth: false }),
  storeMenu: (id) => get('/store/' + id + '/menu', { auth: false }),
  productDetail: (id, storeId) => get('/product/' + id, { data: { storeId }, auth: false }),
  bannerList: () => get('/banner/list', { auth: false }),
  clientConfig: () => get('/config/client', { auth: false, toast: false }),

  // 购物车
  cart: (storeId) => get('/cart', { data: { storeId } }),
  cartAdd: (data) => post('/cart/add', data),
  cartUpdate: (itemId, data) => put('/cart/' + itemId, data),
  cartDelete: (itemId) => del('/cart/' + itemId),
  cartClear: (storeId) => del('/cart/clear', { data: { storeId } }),

  // 订单
  preview: (data) => post('/order/preview', data),
  createOrder: (data) => post('/order', data),
  cancelOrder: (id) => post('/order/' + id + '/cancel'),
  orderList: (params) => get('/order/list', { data: params }),
  orderDetail: (id) => get('/order/' + id),
  reorder: (id) => post('/order/' + id + '/reorder'),
  payQuery: (id) => post('/order/' + id + '/pay/query'),

  // 支付
  payCreate: (orderId) => post('/pay/create', { orderId }),
  payMockCallback: (data) => post('/pay/mock/callback', data),

  // 用户 / 积分
  userProfile: (opt) => get('/user/profile', opt),
  bindPhone: (data, opt) => post('/user/bind-phone', data, opt),
  pointRecords: (params, opt) => get('/user/point/records', { data: params, ...(opt || {}) }),
  signin: (opt) => post('/user/signin', undefined, opt),

  // 优惠券
  couponCenter: () => get('/coupon/center'),
  couponReceive: (templateId) => post('/coupon/receive/' + templateId),
  myCoupons: (status) => get('/coupon/mine', { data: status ? { status } : {} }),
  couponExchangeList: () => get('/coupon/exchange/list'),
  pointExchange: (templateId) => post('/point/exchange', { templateId }),

  // 地址
  addressList: () => get('/address/list'),
  addressAdd: (data) => post('/address', data),
  addressUpdate: (id, data) => put('/address/' + id, data),
  addressDelete: (id) => del('/address/' + id),
  addressDefault: (id) => post('/address/' + id + '/default')
}
