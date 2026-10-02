export const attendanceCategories = [
  { key: 'present', label: 'Present', color: '#2e7d32' },
  { key: 'late', label: 'Late', color: '#b26a00' },
  { key: 'excused', label: 'Excused', color: '#3367ba' },
  { key: 'absent', label: 'Absent', color: '#b9364a' },
] as const

export function attendanceSummary(counts: Record<(typeof attendanceCategories)[number]['key'], number>) {
  const total = attendanceCategories.reduce((sum, category) => sum + counts[category.key], 0)
  const attended = counts.present + counts.late
  return { total, attended, percent: total ? Math.round(attended / total * 100) : null }
}

export function termLink(path: string, termId?: string, activityId?: string) {
  const query = new URLSearchParams()
  if (termId) query.set('termId', termId)
  if (activityId) query.set('activityId', activityId)
  return path + (query.size ? '?' + query.toString() : '')
}
