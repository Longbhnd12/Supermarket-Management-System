import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { KeyRound } from 'lucide-react';
import authService from '../../services/authService';
import '../user/UserManagement.css';
import './AccountPages.css';

// UC03 - Change Password (SRS 3.2.2, SDD 4.2.4.1)
const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,255}$/;
const POLICY_MESSAGE = 'Password must be at least 8 characters and include uppercase, lowercase and a digit.';
const EMPTY_FORM = { identifier: '', currentPassword: '', newPassword: '', confirmPassword: '' };
const LABELS = {
    identifier: 'Username/Email',
    currentPassword: 'Current Password',
    newPassword: 'New Password',
    confirmPassword: 'Confirm New Password'
};

function validate(form) {
    const errors = {};
    Object.keys(LABELS).forEach((field) => {
        if (!form[field].trim()) errors[field] = `The ${LABELS[field]} field is required.`; // MSG02
    });
    if (!errors.newPassword && !PASSWORD_PATTERN.test(form.newPassword)) errors.newPassword = POLICY_MESSAGE;
    if (!errors.newPassword && form.currentPassword && form.newPassword === form.currentPassword) {
        errors.newPassword = 'New password must be different from the current password.'; // BR-02
    }
    if (!errors.confirmPassword && form.newPassword !== form.confirmPassword) {
        errors.confirmPassword = 'New password and confirmation do not match.'; // BR-03
    }
    return errors;
}

// Put the backend message next to the field it belongs to (SDD: "shown next to the field that failed")
function fieldOfServerMessage(message = '') {
    const text = message.toLowerCase();
    if (text.includes('does not match your account')) return 'identifier';           // AT1
    if (text.includes('current password is incorrect')) return 'currentPassword';    // AT2 (MSG04)
    if (text.includes('confirmation')) return 'confirmPassword';                     // AT3
    if (text.includes('username/email')) return 'identifier';
    if (text.includes('password')) return 'newPassword';                             // policy / BR-02
    return null;
}

export default function ChangePasswordPage() {
    const navigate = useNavigate();
    const [form, setForm] = useState(EMPTY_FORM);
    const [fieldErrors, setFieldErrors] = useState({});
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [saving, setSaving] = useState(false);

    const goBack = () => (window.history.length > 1 ? navigate(-1) : navigate('/'));

    const update = (field) => (event) => {
        setForm((current) => ({ ...current, [field]: event.target.value }));
        setFieldErrors((current) => ({ ...current, [field]: '' }));
    };

    const submit = async (event) => {
        event.preventDefault();
        setError('');
        setSuccess('');
        const errors = validate(form);
        setFieldErrors(errors);
        if (Object.keys(errors).length > 0) return;

        setSaving(true);
        try {
            await authService.changePassword({ ...form, identifier: form.identifier.trim() });
            setForm(EMPTY_FORM);
            setSuccess('Password changed. Returning to the previous screen…');
            setTimeout(goBack, 1500); // Step 8: display "Password changed" and return
        } catch (err) {
            const message = err.response?.data?.message || (err.response ? err.message : 'Cannot connect to server');
            const field = err.response?.status === 400 ? fieldOfServerMessage(message) : null;
            if (field) setFieldErrors((current) => ({ ...current, [field]: message }));
            else setError(message);
        } finally {
            setSaving(false);
        }
    };

    const input = (field, autoComplete, type = 'password') => (
        <label className="um-field">
            {LABELS[field]}
            <input
                type={type}
                value={form[field]}
                onChange={update(field)}
                autoComplete={autoComplete}
                maxLength={field === 'identifier' ? 150 : 255}
                aria-invalid={Boolean(fieldErrors[field])}
            />
            {fieldErrors[field] && <span className="um-field-error">{fieldErrors[field]}</span>}
        </label>
    );

    return (
        <div className="um-page">
            <div className="um-header">
                <div>
                    <h1>Change Password</h1>
                    <p>Confirm your account and current password, then choose a new password.</p>
                </div>
            </div>

            {success && <p className="um-alert um-alert-success" role="status">{success}</p>}
            {error && <p className="um-alert um-alert-error" role="alert">{error}</p>}

            <form className="um-card ac-card" onSubmit={submit} noValidate>
                <div className="ac-card-icon"><KeyRound size={22} /></div>
                <div className="ac-stack">
                    {input('identifier', 'username', 'text')}
                    {input('currentPassword', 'current-password')}
                    {input('newPassword', 'new-password')}
                    {!fieldErrors.newPassword && <span className="um-field-hint ac-hint">{POLICY_MESSAGE}</span>}
                    {input('confirmPassword', 'new-password')}
                    <Link to="/forgot-password" className="ac-link">Forgot password?</Link>
                    <div className="um-form-actions">
                        <button type="button" className="um-btn um-btn-outline" onClick={goBack} disabled={saving}>Cancel</button>
                        <button type="submit" className="um-btn um-btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
                    </div>
                </div>
            </form>
        </div>
    );
}
