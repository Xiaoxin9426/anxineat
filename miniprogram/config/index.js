/**
 * 全局配置
 * 环境自动判定：开发者工具（platform=devtools）走 localhost；真机（ios/android）自动走 LAN_HOST。
 * ⚠️ 真机演示三要素：
 *   1. LAN_HOST 必须是开发电脑的局域网 IP（电脑上 ipconfig 查看，IP 变了要同步改这里）
 *   2. 手机与电脑连同一个 WiFi
 *   3. 微信开发者工具「详情-本地设置」勾选「不校验合法域名」（project.config.json 已置 urlCheck=false）
 */
const HOSTS = {
  // 本机开发：微信开发者工具模拟器可直接访问
  dev: 'http://localhost:8080/api',
  // 局域网真机：默认取本机当前局域网 IP（192.168.31.134，2026-09-03 探测）
  lan: 'http://192.168.31.134:8080/api',
  // 正式环境
  prod: 'https://your-domain.com/api'
}

/** 平台探测：devtools=开发者工具；ios/android/windows/mac=真机 */
function detectPlatform() {
  try {
    if (typeof wx !== 'undefined' && wx.getDeviceInfo) return wx.getDeviceInfo().platform
    if (typeof wx !== 'undefined' && wx.getSystemInfoSync) return wx.getSystemInfoSync().platform
  } catch (e) { /* 探测失败按开发工具处理 */ }
  return 'devtools'
}

const CURRENT_ENV = detectPlatform() === 'devtools' ? 'dev' : 'lan'

module.exports = {
  baseUrl: HOSTS[CURRENT_ENV],
  currentEnv: CURRENT_ENV,
  platform: detectPlatform(),
  // 拉起支付的渠道（MOCK 模拟；真实环境会是 WXPAY）
  payChannel: 'MOCK',
  // 后端约定的本地联调免登录 code（mock: 前缀）
  mockLoginCode: 'mock:openid-demo-0001',
  // 本地 storage key 集中管理，便于搜索
  storageKeys: {
    token: 'anxineat_token',
    user: 'anxineat_user',
    currentStore: 'anxineat_store',
    welcomed: 'anxineat_welcomed',
    pickAddress: 'anxineat_pick_address',
    pickCoupon: 'anxineat_pick_coupon',
    lastLogin: 'anxineat_last_login'
  }
}
