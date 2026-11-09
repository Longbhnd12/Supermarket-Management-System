import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createdUserNotice } from './userNotices.js';

test('email sent: success notice names the address', () => {
    const result = createdUserNotice({ email: 'mai.dt@sms.test', credentialsEmailSent: true });
    assert.equal(result.noticeType, 'success');
    assert.match(result.notice, /sent to mai\.dt@sms\.test/);
});

test('email failed: warning asks the Admin to hand over the details', () => {
    const result = createdUserNotice({ email: 'mai.dt@sms.test', credentialsEmailSent: false });
    assert.equal(result.noticeType, 'warning');
    assert.match(result.notice, /could not be sent/);
    assert.match(result.notice, /directly/);
});

test('no flag (older backend): plain success notice', () => {
    assert.deepEqual(createdUserNotice({ email: 'a@b.c' }), { notice: 'Added successfully.', noticeType: 'success' });
    assert.deepEqual(createdUserNotice(undefined), { notice: 'Added successfully.', noticeType: 'success' });
});
