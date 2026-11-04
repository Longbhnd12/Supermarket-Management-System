import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import authService from '../../services/authService';
import './AuthPage.css';

export default function RegisterPage() {
    const navigate = useNavigate();
    const [form, setForm] = useState({ username: '', email: '', password: '', confirmPassword: '' });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }));

    const submit = async (event) => {
        event.preventDefault();
        setError('');
        setLoading(true);
        try {
            const response = await authService.register(form);
            if (!response?.success) throw new Error(response?.message || 'Registration failed.');
            navigate('/login', { replace: true });
        } catch (requestError) {
            setError(requestError.response?.data?.message || requestError.message || 'Registration failed.');
        } finally {
            setLoading(false);
        }
    };

    return <main className="auth-page"><section className="auth-card"><h1>Create account</h1><p>New accounts are registered as cashiers.</p><form onSubmit={submit}><label>Username<input value={form.username} onChange={update('username')} minLength="3" required /></label><label>Email<input type="email" value={form.email} onChange={update('email')} required /></label><label>Password<input type="password" value={form.password} onChange={update('password')} minLength="8" required /></label><label>Confirm password<input type="password" value={form.confirmPassword} onChange={update('confirmPassword')} minLength="8" required /></label>{error && <p className="auth-error">{error}</p>}<button disabled={loading}>{loading ? 'Creating…' : 'Create account'}</button></form><p>Already registered? <Link to="/login">Sign in</Link></p></section></main>;
}
