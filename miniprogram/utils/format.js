/**
 * 通用工具
 * 金额一律为「分」，仅在这里做展示格式化；不进行任何加减乘除。
 */

function pad2(n) { return n < 10 ? '0' + n : '' + n }

/**
 * 分 → 元，保留两位小数（用于显示，不参与计算）
 * @param {number|string} fen
 * @returns {string}
 */
function fen2yuan(fen) {
  const n = Number(fen || 0)
  if (!isFinite(n)) return '0.00'
  return (n / 100).toFixed(2)
}

/**
 * 显示金额：自动加 ¥。负数场景用于展示折扣。
 */
function priceText(fen, withSymbol = true) {
  const v = fen2yuan(fen)
  if (!withSymbol) return v
  return v.startsWith('-') ? '-¥' + v.slice(1) : '¥' + v
}

/**
 * 「yyyy-MM-dd HH:mm:ss」解析为 Date。
 * 小程序在 iOS 上不直接支持「yyyy-MM-dd」分隔的字符串，要替换为「/」。
 */
function parseDateTime(str) {
  if (!str) return null
  const s = String(str).replace(/-/g, '/')
  const d = new Date(s)
  if (isNaN(d.getTime())) return null
  return d
}

/**
 * 时间格式化为「MM-dd HH:mm」
 */
function formatShort(str) {
  const d = parseDateTime(str)
  if (!d) return '--'
  return pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) + ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes())
}

/**
 * 完整格式化「yyyy-MM-dd HH:mm」
 */
function formatFull(str) {
  const d = parseDateTime(str)
  if (!d) return '--'
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) +
    ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes())
}

/**
 * 用于 expectTime 上送：「yyyy-MM-dd HH:mm:ss」
 */
function formatServerTime(d) {
  const x = d || new Date()
  return x.getFullYear() + '-' + pad2(x.getMonth() + 1) + '-' + pad2(x.getDate()) +
    ' ' + pad2(x.getHours()) + ':' + pad2(x.getMinutes()) + ':' + pad2(x.getSeconds())
}

/**
 * 倒计时文本：基于到期时间戳（毫秒）计算
 * @returns {string} 例如 14:59
 */
function countdownText(targetTs) {
  let diff = Math.floor((targetTs - Date.now()) / 1000)
  if (diff <= 0) return '00:00'
  const h = Math.floor(diff / 3600)
  diff -= h * 3600
  const m = Math.floor(diff / 60)
  const s = diff - m * 60
  if (h > 0) return pad2(h) + ':' + pad2(m) + ':' + pad2(s)
  return pad2(m) + ':' + pad2(s)
}

/**
 * 把后端 statusLog 转成中文描述
 */
function statusLabel(s) {
  return ({
    PENDING_PAYMENT: '待支付',
    PAID: '已支付',
    MAKING: '制作中',
    READY: '待取餐',
    DELIVERING: '配送中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    REFUNDED: '已退款'
  })[s] || s || ''
}

/**
 * 距离格式化：m / km，保留 1 位
 */
function distanceText(km) {
  if (km == null) return ''
  if (km < 1) return Math.round(km * 1000) + 'm'
  return km.toFixed(1) + 'km'
}

/**
 * 简化 UUID v4（无需加密强度）
 */
function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

/**
 * 安全 JSON.parse
 */
function safeJson(s, fallback) {
  if (!s) return fallback
  if (typeof s !== 'string') return s
  try { return JSON.parse(s) } catch (e) { return fallback }
}

module.exports = {
  fen2yuan, priceText, parseDateTime, formatShort, formatFull,
  formatServerTime, countdownText, statusLabel, distanceText, uuid, safeJson,
  pad2
}
