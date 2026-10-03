export type AttendanceAction = 'create' | 'edit' | 'attendance' | 'finalize' | 'cancel' | 'correct'

export function attendanceNotice(action: AttendanceAction): string {
  switch (action) {
    case 'create':
    case 'edit': return 'Activity draft saved. No points have been applied.'
    case 'attendance': return 'Attendance draft saved. No points have been applied.'
    case 'finalize': return 'Attendance finalized. Points are recorded according to saved scoring and eligibility.'
    case 'cancel': return 'Activity draft cancelled. No points were applied.'
    case 'correct': return 'Attendance corrected. Any point adjustments are recorded; original history is preserved.'
  }
}

export function attendanceReadiness(unmarked: number, dirty: boolean): string {
  if (unmarked > 0) return `${unmarked} ${unmarked === 1 ? 'attendee is' : 'attendees are'} still unmarked. Complete and save attendance before finalizing.`
  if (dirty) return 'All attendees are marked. Save your changes before finalizing.'
  return 'All attendance is saved. Finalize attendance when you are ready to apply points.'
}
