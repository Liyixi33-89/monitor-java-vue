export function formatBytes(bytes) {
  if (bytes == null) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let v = bytes
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

export function formatTime(dt) {
  if (!dt) return '-'
  return String(dt).replace('T', ' ').slice(0, 19)
}

export function percent(used, total) {
  if (used == null || total == null || total === 0) return '-'
  return `${((used * 100) / total).toFixed(1)}%`
}
