import { useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import {
    AlertCircle,
    BarChart3,
    CheckCircle2,
    Eye,
    EyeOff,
    Loader2,
    Lock,
    ShoppingCart,
    Store,
    TriangleAlert,
    UserRound,
    Warehouse
} from 'lucide-react';
import { useAuth } from '../../context/authContext';
import { getReturnPath } from '../../utils/authRedirect';
import './AuthPage.css';
import './LoginPage.css';

// UC01 - Login (SRS 3.2.1, SDD 4.2.2)
const EMPTY_ERRORS = { identifier: '', password: '' };

// MSG02: The {field_name} field is required.
function validate(identifier, password) {
    return {
        identifier: identifier.trim() ? '' : 'The Username/Email field is required.',
        password: password ? '' : 'The Password field is required.'
    };
}

const FEATURES = [
    { icon: ShoppingCart, text: 'Fast checkout at the POS with invoices and VNPay' },
    { icon: Warehouse, text: 'Stock, receipts and purchase orders in one place' },
    { icon: BarChart3, text: 'Sales, revenue and inventory reports' }
];

export default function LoginPage() {
    const { status, currentUser, login } = useAuth();
    const location = useLocation();
    // Coming back from Forgot Password (UC45) or a session that expired: message + the account to sign in with
    const [identifier, setIdentifier] = useState(location.state?.identifier || '');
    const [notice, setNotice] = useState(location.state?.notice || '');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [fieldErrors, setFieldErrors] = useState(EMPTY_ERRORS);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const submit = async (event) => {
        event.preventDefault();
        setError('');
        setNotice('');

        const errors = validate(identifier, password);
        setFieldErrors(errors);
        if (errors.identifier || errors.password) return;

        setLoading(true);
        try {
            // AuthProvider stores the user; the render below then redirects to the role's home screen
            await login({ identifier: identifier.trim(), password });
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

    // Typing in a field removes its error message
    const changeField = (field, setter) => (event) => {
        setter(event.target.value);
        if (fieldErrors[field]) setFieldErrors((current) => ({ ...current, [field]: '' }));
    };

    if (status === 'loading') return <div className="auth-loading">Checking your session…</div>;
    // Already logged in (also right after a successful login, or a login in another tab):
    // go to the page the session expired on, otherwise to the role's home screen.
    if (status === 'authenticated') {
        return <Navigate to={getReturnPath(location.state?.from, currentUser?.role)} replace />;
    }

    const noticeIsWarning = location.state?.noticeTone === 'warning';

    return (
        <main className="login-page">
            <section className="login-brand" aria-hidden="true">
                <div className="login-brand-logo">
                    <span className="login-logo-icon"><Store size={26} /></span>
                    <span>
                        <strong>RETAIL SMS</strong>
                        <small>Store Operations</small>
                    </span>
                </div>
                <div className="login-brand-copy">
                    <h2>Supermarket Management System</h2>
                    <p>Everything the store team needs, from the checkout counter to the warehouse.</p>
                    <ul>
                        {FEATURES.map(({ icon: Icon, text }) => (
                            <li key={text}><span><Icon size={18} /></span>{text}</li>
                        ))}
                    </ul>
                </div>
                <p className="login-brand-footer">© 2026 Supermarket Management System</p>
            </section>

            <section className="login-panel">
                <div className="login-card">
                    <div className="login-mobile-logo">
                        <span className="login-logo-icon"><Store size={22} /></span>
                        <strong>RETAIL SMS</strong>
                    </div>

                    <h1>Welcome back</h1>
                    <p className="login-subtitle">Sign in to your account to continue.</p>

                    {notice && (
                        <div className={`login-alert ${noticeIsWarning ? 'login-alert--warning' : 'login-alert--success'}`} role="status">
                            {noticeIsWarning ? <TriangleAlert size={18} /> : <CheckCircle2 size={18} />}
                            <span>{notice}</span>
                        </div>
                    )}
                    {error && (
                        <div className="login-alert login-alert--error" role="alert">
                            <AlertCircle size={18} />
                            <span>{error}</span>
                        </div>
                    )}

                    <form onSubmit={submit} noValidate className="login-form">
                        <div className="login-field">
                            <label htmlFor="login-identifier">Username or email</label>
                            <div className={`login-input${fieldErrors.identifier ? ' login-input--invalid' : ''}`}>
                                <UserRound size={18} aria-hidden="true" />
                                <input
                                    id="login-identifier"
                                    value={identifier}
                                    onChange={changeField('identifier', setIdentifier)}
                                    placeholder="Enter your username or email"
                                    autoComplete="username"
                                    maxLength={150}
                                    aria-invalid={Boolean(fieldErrors.identifier)}
                                    aria-describedby={fieldErrors.identifier ? 'login-identifier-error' : undefined}
                                    autoFocus
                                />
                            </div>
                            {fieldErrors.identifier && (
                                <span id="login-identifier-error" className="login-field-error">
                                    <AlertCircle size={14} aria-hidden="true" />{fieldErrors.identifier}
                                </span>
                            )}
                        </div>

                        <div className="login-field">
                            <div className="login-label-row">
                                <label htmlFor="login-password">Password</label>
                                <Link className="login-forgot" to="/forgot-password">Forgot password?</Link>
                            </div>
                            <div className={`login-input${fieldErrors.password ? ' login-input--invalid' : ''}`}>
                                <Lock size={18} aria-hidden="true" />
                                <input
                                    id="login-password"
                                    type={showPassword ? 'text' : 'password'}
                                    value={password}
                                    onChange={changeField('password', setPassword)}
                                    placeholder="Enter your password"
                                    autoComplete="current-password"
                                    maxLength={255}
                                    aria-invalid={Boolean(fieldErrors.password)}
                                    aria-describedby={fieldErrors.password ? 'login-password-error' : undefined}
                                />
                                <button
                                    type="button"
                                    className="login-eye"
                                    onClick={() => setShowPassword((shown) => !shown)}
                                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                                    title={showPassword ? 'Hide password' : 'Show password'}
                                >
                                    {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                                </button>
                            </div>
                            {fieldErrors.password && (
                                <span id="login-password-error" className="login-field-error">
                                    <AlertCircle size={14} aria-hidden="true" />{fieldErrors.password}
                                </span>
                            )}
                        </div>

                        <button type="submit" className="login-submit" disabled={loading}>
                            {loading && <Loader2 size={18} className="login-spinner" aria-hidden="true" />}
                            {loading ? 'Signing in…' : 'Login'}
                        </button>
                    </form>
                </div>
            </section>
        </main>
    );
}
