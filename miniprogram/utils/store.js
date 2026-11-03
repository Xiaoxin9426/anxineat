/**
 * 当前门店上下文
 * - 切换门店要清空购物车
 * - 暴露 setStore/clear/getCurrent
 */
const { storageKeys } = require('../config/index')
const api = require('./api')

/** 同步内存副本：保证 getApp().getStore()/getStoreId() 与 storage 单一事实一致 */
function syncGlobal(s) {
  try {
    const app = typeof getApp === 'function' ? getApp() : null
    if (app && app.globalData) app.globalData.currentStore = s
  } catch (e) { /* App 尚未就绪时忽略 */ }
}

function getCurrent() { return wx.getStorageSync(storageKeys.currentStore) || null }
function setCurrent(s) {
  wx.setStorageSync(storageKeys.currentStore, s)
  syncGlobal(s)
}
function clear() {
  wx.removeStorageSync(storageKeys.currentStore)
  syncGlobal(null)
}

/**
 * 切换门店：若非同一门店，先弹确认，再清空原门店购物车
 * @param {object} target 目标门店
 * @param {object} page 当前页面实例（用于 wx.showModal / this.setData）
 * @returns {Promise<boolean>} 是否最终切换成功
 */
async function switchTo(target, page) {
  const cur = getCurrent()
  if (!cur || cur.id === target.id) {
    setCurrent(target)
    return true
  }
  // 不同门店：询问是否清空购物车
  // 注意：wx.showModal 的 confirmText/cancelText 最多 4 个字符（官方文档约束），
  // 超长时接口直接 fail 且弹窗不显示；若只挂 success 无 fail 兜底，
  // Promise 将永远 pending，切换流程整体挂死（表现为点门店毫无反应）。
  const ok = await new Promise(res => {
    let done = false
    const finish = v => { if (!done) { done = true; res(v) } }
    const timer = setTimeout(() => finish(false), 15000) // 极端兜底：15s 无回调则放弃本次
    wx.showModal({
      title: '切换门店',
      content: '切换门店将清空当前门店购物车，是否继续？',
      confirmText: '确认切换',
      success: r => { clearTimeout(timer); finish(!!r.confirm) },
      fail: (err) => {
        // 弹窗失败时降级为直接切换：清空购物车本就是切换的既定规则，
        // 决不允许流程挂死（历史 bug：confirmText 5 字超限导致永久挂起）
        clearTimeout(timer)
        console.warn('[anxineat] showModal fail，降级为直接切换:', (err && err.errMsg) || err)
        finish(true)
      }
    })
  })
  if (!ok) return false
  try {
    await api.cartClear(cur.id)
  } catch (e) { /* 容忍清空失败 */ }
  setCurrent(target)
  if (page && page.setData) page.setData({ currentStore: target })
  return true
}

module.exports = { getCurrent, setCurrent, clear, switchTo }
