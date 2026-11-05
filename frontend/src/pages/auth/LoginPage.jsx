import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import authService from '../../services/authService';
import { getHomePathByRole } from '../../utils/authRedirect';
import './AuthPage.css';

// UC01 - Login (SRS 3.2.1, SDD 4.2.2)
const EMPTY_ERRORS = { identifier: '', password: '' };

// MSG02: The {field_name} field is required.
function validate(identifier, password) {
    return {
        identifier: identifier.trim() ? '' : 'The Username/Email field is required.',
        password: password ? '' : 'The Password field is required.'
    };
}

export default function LoginPage() {
    const navigate = useNavigate();
    const location = useLocation();
    // Coming back from Forgot Password (UC45): success message + the account to sign in with
    const [identifier, setIdentifier] = useState(location.state?.identifier || '');
    const [notice, setNotice] = useState(location.state?.notice || '');
    const [password, setPassword] = useState('');
    const [fieldErrors, setFieldErrors] = useState(EMPTY_ERRORS);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [checkingSession, setCheckingSession] = useState(true);

    // Already logged in? Go straight to the role's home screen.
    useEffect(() => {
        let active = true;
        authService.getMe()
            .then((response) => {
                if (active && response?.success) {
                    navigate(getHomePathByRole(response.data?.role), { replace: true });
                }
            })
            .catch(() => { /* not logged in yet: stay on the login screen */ })
            .finally(() => active && setCheckingSession(false));
        return () => { active = false; };
    }, [navigate]);

    const submit = async (event) => {
        event.preventDefault();
        setError('');
        setNotice('');

        const errors = validate(identifier, password);
        setFieldErrors(errors);
        if (errors.identifier || errors.password) return;

        setLoading(true);
        try {
            const response = await authService.login({ identifier: identifier.trim(), password });
            if (!response?.success) throw new Error(response?.message || 'Login failed.');
            navigate(getHomePathByRole(response.data?.role), { replace: true });
        } catch (requestError) {
            // 401 -> MSG03, 403 -> account inactive / locked (message comes from the backend)
            const message = requestError.response?.data?.message
                || (requestError.response ? requestError.message : 'Cannot connect to server');
            setError(message);
            setPassword('');
        } finally {
            setLoading(false);
        }
    };

    // "Close" clears the login form
    const close = () => {
        setIdentifier('');
        setPassword('');
        setFieldErrors(EMPTY_ERRORS);
        setError('');
        setNotice('');
    };

    if (checkingSession) return <div className="auth-loading">Checking your session…</div>;

    return (
        <main className="auth-page">
            <section className="auth-card">
                <h1>RETAIL SMS</h1>
                <p>Sign in to continue.</p>
                {notice && <p className="auth-success" role="status">{notice}</p>}
                <form onSubmit={submit} noValidate>
                    <label>
                        Username/Email
                        <input
                            value={identifier}
                            onChange={(event) => setIdentifier(event.target.value)}
                            autoComplete="username"
                            maxLength={150}
                            aria-invalid={Boolean(fieldErrors.identifier)}
                            autoFocus
                        />
                        {fieldErrors.identifier && <span className="auth-field-error">{fieldErrors.identifier}</span>}
                    </label>
                    <label>
                        Password
                        <input
                            type="password"
                            value={password}
                            onChange={(event) => setPassword(event.target.value)}
                            autoComplete="current-password"
                            maxLength={255}
                            aria-invalid={Boolean(fieldErrors.password)}
                        />
                        {fieldErrors.password && <span className="auth-field-error">{fieldErrors.password}</span>}
                    </label>
                    {error && <p className="auth-error" role="alert">{error}</p>}
                    <Link className="auth-forgot" to="/forgot-password">Forgot password?</Link>
                    <div className="auth-actions">
                        <button type="button" className="auth-secondary" onClick={close} disabled={loading}>Close</button>
                        <button type="submit" disabled={loading}>{loading ? 'Signing in…' : 'Login'}</button>
                    </div>
                </form>
                <p>New account? <Link to="/register">Register</Link></p>
            </section>
        </main>
    );
}
