/**
 * 商品 / Banner / 头像占位图
 * 种子数据 image 字段全部留空，按需渲染渐变背景 + emoji。
 * 用法：{{item._ph.bg}} {{item._ph.emoji}}
 * binderror 时把 _imgErr 置 true，模板里根据它显示占位元素。
 */

const PALETTE = [
  ['#FFB199', '#E4002B'],
  ['#FFD194', '#FF6F61'],
  ['#FFE29F', '#FFA751'],
  ['#C8E6C9', '#43A047'],
  ['#B3E5FC', '#039BE5'],
  ['#D1C4E9', '#5E35B1'],
  ['#F8BBD0', '#D81B60'],
  ['#FFE082', '#FF8F00'],
  ['#A5D6A7', '#1B5E20'],
  ['#80DEEA', '#00838F']
]

// 关键词 → emoji 优先级最高
const KEYWORD_EMOJI = [
  [/柠檬/, '🍋'],
  [/百香果/, '🥭'],
  [/蜜桃|桃/, '🍑'],
  [/葡萄|青提/, '🍇'],
  [/芒果|杨枝/, '🥭'],
  [/西米/, '🟤'],
  [/芋圆|珍珠|椰果|啵啵|布丁|燕麦/, '🟣'],
  [/圣代|冰淇淋|冰/, '🍦'],
  [/咖啡|美式|拿铁/, '☕'],
  [/奶茶|奶/, '🧋'],
  [/茶/, '🍵']
]

const CATEGORY_EMOJI = {
  '招牌推荐': '🌟',
  '鲜果茶': '🍓',
  '奶茶': '🧋',
  '冰淇淋': '🍦',
  '咖啡': '☕'
}

// 极简 hash
function hash(s) {
  let h = 0
  for (let i = 0; i < (s || '').length; i++) {
    h = (h * 31 + s.charCodeAt(i)) | 0
  }
  return Math.abs(h)
}

function pickEmoji(name, categoryName) {
  for (const [re, e] of KEYWORD_EMOJI) {
    if (re.test(name)) return e
  }
  if (categoryName && CATEGORY_EMOJI[categoryName]) return CATEGORY_EMOJI[categoryName]
  return '🥤'
}

function pickGradient(name) {
  const h = hash(name || 'x')
  const [a, b] = PALETTE[h % PALETTE.length]
  const angle = (h % 4) * 45 + 90 // 90, 135, 180, 225
  return 'linear-gradient(' + angle + 'deg, ' + a + ', ' + b + ')'
}

function product(product, categoryName) {
  if (!product) return { bg: pickGradient('anxineat'), emoji: '🥤' }
  return {
    bg: pickGradient(product.name),
    emoji: pickEmoji(product.name || '', categoryName || '')
  }
}

function banner(item) {
  const t = (item && item.title) || 'banner'
  return { bg: pickGradient(t), emoji: '🎉' }
}

function avatar(user) {
  const t = (user && user.nickname) || 'U'
  return { bg: pickGradient(t), emoji: '👤' }
}

module.exports = { product, banner, avatar, pickGradient, pickEmoji }
