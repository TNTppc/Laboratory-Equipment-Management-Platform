export const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'

function parseUtc(value: unknown) {
  if (!value) return null
  const text = String(value).trim().replace(' ', 'T')
  const hasZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(text)
  const date = new Date(hasZone ? text : `${text}Z`)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDateTime(value: unknown) {
  const date = parseUtc(value)
  if (!date) return value ? String(value) : '—'
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: browserTimeZone,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false
  }).formatToParts(date)
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find(item => item.type === type)?.value || ''
  return `${part('year')}年${part('month')}月${part('day')}日 ${part('hour')}:${part('minute')}`
}

export function timeZoneLabel() {
  return browserTimeZone === 'Asia/Shanghai' ? '北京时间' : `当前电脑时区（${browserTimeZone}）`
}

export function localInputToUtc(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) throw new Error('时间格式不正确')
  return date.toISOString().slice(0, 19)
}
