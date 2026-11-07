import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import authService from '../../services/authService';
import PasswordInput from '../../common/PasswordInput';
import './AuthPage.css';

// UC45 - Forgot Password (SRS 3.2.3, SDD 4.2.5.1)
// Step 1: Username/Email -> "Send reset code"
// Step 2: Reset Code + New Password + Confirm -> "Reset password" -> back to Login
const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,255}$/;
const POLICY_MESSAGE = 'Password must be at least 8 characters and include uppercase, lowercase and a digit.';
const RESEND_SECONDS = 60;

function messageOf(error) {
    return error.response?.data?.message || (error.response ? error.message : 'Cannot connect to server');
}

export default function ForgotPasswordPage() {
    const navigate = useNavigate();
    const [step, setStep] = useState(1);
    const [identifier, setIdentifier] = useState('');
    const [form, setForm] = useState({ code: '', newPassword: '', confirmPassword: '' });
    const [fieldErrors, setFieldErrors] = useState({});
    const [info, setInfo] = useState('');
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const [resendAt, setResendAt] = useState(0);
    const [now, setNow] = useState(() => Date.now());

    // Ticks once per second while the "Resend code" button is waiting
    useEffect(() => {
        if (resendAt <= Date.now()) return undefined;
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(timer);
    }, [resendAt]);
    const secondsLeft = Math.max(0, Math.ceil((resendAt - now) / 1000));

    const sendCode = async () => {
        setError('');
        setBusy(true);
        try {
            const response = await authService.forgotPassword({ identifier: identifier.trim() });
            // AT1 / BR-03: the same message whether or not the account exists
            setInfo(response?.message || 'If this account exists, a reset code has been sent');
            setStep(2);
            const start = Date.now();
            setNow(start);
            setResendAt(start + RESEND_SECONDS * 1000);
        } catch (err) {
            setError(messageOf(err)); // e.g. the email could not be sent
        } finally {
            setBusy(false);
        }
    };

    const submitStep1 = (event) => {
        event.preventDefault();
        if (!identifier.trim()) {
            setFieldErrors({ identifier: 'The Username/Email field is required.' }); // MSG02
            return;
        }
        setFieldErrors({});
        sendCode();
    };

    const update = (field) => (event) => {
        setForm((current) => ({ ...current, [field]: event.target.value }));
        setFieldErrors((current) => ({ ...current, [field]: '' }));
    };

    const submitStep2 = async (event) => {
        event.preventDefault();
        setError('');
        const errors = {};
        if (!form.code.trim()) errors.code = 'The Reset Code field is required.';
        if (!form.newPassword) errors.newPassword = 'The New Password field is required.';
        else if (!PASSWORD_PATTERN.test(form.newPassword)) errors.newPassword = POLICY_MESSAGE;
        if (!form.confirmPassword) errors.confirmPassword = 'The Confirm New Password field is required.';
        else if (!errors.newPassword && form.newPassword !== form.confirmPassword) {
            errors.confirmPassword = 'New password and confirmation do not match.';
        }
        setFieldErrors(errors);
        if (Object.keys(errors).length > 0) return;

        setBusy(true);
        try {
            await authService.resetPassword({
                identifier: identifier.trim(),
                code: form.code.trim(),
                newPassword: form.newPassword,
                confirmPassword: form.confirmPassword
            });
            // Step 9: "Password reset successful" and return to the Login screen
            navigate('/login', {
                replace: true,
                state: { notice: 'Password reset successful. Please sign in with your new password.', identifier: identifier.trim() }
            });
        } catch (err) {
            const message = messageOf(err);
            if (message.toLowerCase().includes('reset code')) setFieldErrors({ code: message }); // AT2
            else if (message.toLowerCase().includes('confirmation')) setFieldErrors({ confirmPassword: message });
            else if (message.toLowerCase().includes('password must')) setFieldErrors({ newPassword: message });
            else setError(message);
        } finally {
            setBusy(false);
        }
    };

    const startOver = () => {
        setStep(1);
        setForm({ code: '', newPassword: '', confirmPassword: '' });
        setFieldErrors({});
        setInfo('');
        setError('');
    };

    const fieldError = (field) => fieldErrors[field] && <span className="auth-field-error">{fieldErrors[field]}</span>;

    return (
        <main className="auth-page">
            <section className="auth-card">
                <h1>Forgot password</h1>
                {step === 1 ? (
                    <>
                        <p>Enter your username or email. We will send a reset code to the email registered for your account.</p>
                        <form onSubmit={submitStep1} noValidate>
                            <label>
                                Username/Email
                                <input
                                    value={identifier}
                                    onChange={(event) => { setIdentifier(event.target.value); setFieldErrors({}); }}
                                    autoComplete="username"
                                    maxLength={150}
                                    aria-invalid={Boolean(fieldErrors.identifier)}
                                    autoFocus
                                />
                                {fieldError('identifier')}
                            </label>
                            {error && <p className="auth-error" role="alert">{error}</p>}
                            <button type="submit" disabled={busy}>{busy ? 'Sending…' : 'Send reset code'}</button>
                        </form>
                    </>
                ) : (
                    <>
                        {info && <p className="auth-success" role="status">{info}</p>}
                        <p>Check the inbox (and the spam folder) of the email registered for <b>{identifier.trim()}</b>. The code is valid for 10 minutes and can be used once.</p>
                        <form onSubmit={submitStep2} noValidate>
                            <label>
                                Reset Code
                                <input
                                    value={form.code}
                                    onChange={update('code')}
                                    inputMode="numeric"
                                    autoComplete="one-time-code"
                                    maxLength={10}
                                    aria-invalid={Boolean(fieldErrors.code)}
                                    autoFocus
                                />
                                {fieldError('code')}
                            </label>
                            <label>
                                New Password
                                <PasswordInput value={form.newPassword} onChange={update('newPassword')} autoComplete="new-password" maxLength={255} aria-invalid={Boolean(fieldErrors.newPassword)} />
                                {fieldError('newPassword') || <span className="auth-hint">{POLICY_MESSAGE}</span>}
                            </label>
                            <label>
                                Confirm New Password
                                <PasswordInput value={form.confirmPassword} onChange={update('confirmPassword')} autoComplete="new-password" maxLength={255} aria-invalid={Boolean(fieldErrors.confirmPassword)} />
                                {fieldError('confirmPassword')}
                            </label>
                            {error && <p className="auth-error" role="alert">{error}</p>}
                            <button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Reset password'}</button>
                            <div className="auth-links-row">
                                <button type="button" className="auth-text-button" onClick={sendCode} disabled={busy || secondsLeft > 0}>
                                    {secondsLeft > 0 ? `Resend code (${secondsLeft}s)` : 'Resend code'}
                                </button>
                                <button type="button" className="auth-text-button" onClick={startOver} disabled={busy}>Use another account</button>
                            </div>
                        </form>
                    </>
                )}
                <p><Link to="/login">Back to sign in</Link></p>
            </section>
        </main>
    );
}
