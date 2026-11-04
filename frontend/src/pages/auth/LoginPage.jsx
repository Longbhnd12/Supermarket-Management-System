import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import authService from '../../services/authService';
import './AuthPage.css';

export default function LoginPage() {
    const navigate = useNavigate();
    const [identifier, setIdentifier] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const submit = async (event) => {
        event.preventDefault();
        setError('');
        setLoading(true);
        try {
            const response = await authService.login({ identifier, password });
            if (!response?.success) throw new Error(response?.message || 'Login failed.');
            navigate('/', { replace: true });
        } catch (requestError) {
            setError(requestError.response?.data?.message || requestError.message || 'Login failed.');
        } finally {
            setLoading(false);
        }
    };

    return <main className="auth-page"><section className="auth-card"><h1>RETAIL SMS</h1><p>Sign in to continue.</p><form onSubmit={submit}><label>Username or email<input value={identifier} onChange={(event) => setIdentifier(event.target.value)} autoComplete="username" required /></label><label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required /></label>{error && <p className="auth-error">{error}</p>}<button disabled={loading}>{loading ? 'Signing in…' : 'Sign in'}</button></form><p>New account? <Link to="/register">Register</Link></p></section></main>;
}
