import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
    INITIAL_AUTH_STATE,
    applySessionResult,
    applyUnauthorized,
    sessionResultFromError,
    sessionResultFromResponse
} from './authState.js';

const user = { userId: 1, username: 'admin', role: 'ADMIN', permissions: ['USER_VIEW'] };
const loggedIn = { status: 'authenticated', user, reason: null };
const loggedOut = { status: 'unauthenticated', user: null, reason: 'logout' };

test('a successful /auth/me logs the user in', () => {
    const result = sessionResultFromResponse({ success: true, data: user });
    assert.deepEqual(applySessionResult(INITIAL_AUTH_STATE, result), loggedIn);
});

test('401 on the first check is a normal "not logged in" (no expired message)', () => {
    const result = sessionResultFromError({ response: { status: 401 } });
    assert.deepEqual(applySessionResult(INITIAL_AUTH_STATE, result), { status: 'unauthenticated', user: null, reason: null });
});

test('401 while logged in means the session expired', () => {
    const result = sessionResultFromError({ response: { status: 401 } });
    assert.equal(applySessionResult(loggedIn, result).reason, 'expired');
    assert.equal(applyUnauthorized(loggedIn).reason, 'expired');
});

test('a second 401 keeps the first logout reason', () => {
    const result = sessionResultFromError({ response: { status: 401 } });
    assert.equal(applySessionResult(loggedOut, result), loggedOut);
    assert.equal(applyUnauthorized(loggedOut), loggedOut);
});

test('backend down or 5xx never logs a logged-in user out', () => {
    for (const error of [{ code: 'ERR_NETWORK' }, { response: { status: 500 } }, { response: { status: 503 } }]) {
        assert.equal(applySessionResult(loggedIn, sessionResultFromError(error)), loggedIn);
    }
});

test('backend down on the first check shows "unreachable" instead of the Login page', () => {
    const result = sessionResultFromError({ code: 'ERR_NETWORK' });
    assert.equal(applySessionResult(INITIAL_AUTH_STATE, result).status, 'unreachable');
});

test('401 while still loading does not change the state', () => {
    assert.equal(applyUnauthorized(INITIAL_AUTH_STATE), INITIAL_AUTH_STATE);
});
