import test from 'node:test'
import assert from 'node:assert/strict'
import { attendanceNotice, attendanceReadiness } from '../src/attendanceFeedback.ts'

test('draft operations never imply that points were awarded', () => {
  for (const action of ['create', 'edit', 'attendance']) {
    assert.match(attendanceNotice(action), /No points have been applied/)
  }
  assert.match(attendanceNotice('cancel'), /No points were applied/)
})

test('finalization and corrections describe scoring and preserved history', () => {
  assert.match(attendanceNotice('finalize'), /saved scoring and eligibility/)
  assert.match(attendanceNotice('correct'), /original history is preserved/)
})

test('readiness distinguishes incomplete, unsaved and ready attendance', () => {
  assert.match(attendanceReadiness(1, true), /1 attendee is still unmarked/)
  assert.match(attendanceReadiness(3, false), /3 attendees are still unmarked/)
  assert.match(attendanceReadiness(0, true), /Save your changes before finalizing/)
  assert.match(attendanceReadiness(0, false), /All attendance is saved/)
})
