import test from 'node:test'
import assert from 'node:assert/strict'
import { attendanceSummary, attendanceCategories, termLink } from '../src/dashboard.ts'

test('attendance includes all four outcomes in the denominator', () => {
  assert.deepEqual(attendanceSummary({ present: 6, late: 2, excused: 1, absent: 1 }), { total: 10, attended: 8, percent: 80 })
  assert.deepEqual(attendanceCategories.map(c => c.label), ['Present', 'Late', 'Excused', 'Absent'])
})
test('zero expected attendees is not reported as a misleading zero percent', () => {
  assert.deepEqual(attendanceSummary({ present: 0, late: 0, excused: 0, absent: 0 }), { total: 0, attended: 0, percent: null })
})
test('dashboard links preserve selected term and specific activity', () => {
  assert.equal(termLink('/activities', 'closed-term', 'meeting'), '/activities?termId=closed-term&activityId=meeting')
  assert.equal(termLink('/points', 'closed-term'), '/points?termId=closed-term')
  assert.equal(termLink('/activities'), '/activities')
})
