import { useEffect, useState } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import { KeyRound, UserRound } from 'lucide-react';
import ProfileService from '../../services/profileService';
import { getErrorMessage } from '../../utils/errorUtils';
import { roleLabel } from '../../utils/roleLabels';
import '../user/UserManagement.css';
import './AccountPages.css';

// UC44 - View/Update Profile (SRS 3.2.4, SDD 4.2.6.1)
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_PATTERN = /^\+?[0-9]{9,15}$/;

function statusLabel(status) {
    return status ? status.charAt(0) + status.slice(1).toLowerCase() : '';
}

export default function ProfilePage() {
    const { refreshUser } = useOutletContext() ?? {};
    const [profile, setProfile] = useState(null);
    const [form, setForm] = useState({ fullName: '', phone: '', email: '' });
    const [fieldErrors, setFieldErrors] = useState({});
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [saving, setSaving] = useState(false);

    const fill = (data) => {
        setProfile(data);
        setForm({ fullName: data.fullName || '', phone: data.phone || '', email: data.email || '' });
    };

    useEffect(() => {
        let active = true;
        ProfileService.get()
            .then(({ data }) => active && fill(data.data))
            .catch((err) => active && setError(getErrorMessage(err)));
        return () => { active = false; };
    }, []);

    const update = (field) => (event) => {
        setForm((current) => ({ ...current, [field]: event.target.value }));
        setFieldErrors((current) => ({ ...current, [field]: '' }));
        setNotice('');
    };

    const validate = () => {
        const errors = {};
        if (!form.email.trim()) errors.email = 'The Email field is required.';
        else if (!EMAIL_PATTERN.test(form.email.trim())) errors.email = 'Enter a valid email address.';
        if (profile?.hasEmployee) {
            if (!form.fullName.trim()) errors.fullName = 'The Full Name field is required.';
            if (!form.phone.trim()) errors.phone = 'The Phone field is required.';
            else if (!PHONE_PATTERN.test(form.phone.trim())) errors.phone = 'Phone must contain 9 to 15 digits.';
        }
        return errors;
    };

    const submit = async (event) => {
        event.preventDefault();
        setError('');
        setNotice('');
        const errors = validate();
        setFieldErrors(errors);
        if (Object.keys(errors).length > 0) return;

        setSaving(true);
        try {
            const { data } = await ProfileService.update({
                fullName: profile.hasEmployee ? form.fullName.trim() : null,
                phone: profile.hasEmployee ? form.phone.trim() : null,
                email: form.email.trim()
            });
            fill(data.data);
            setNotice('Profile updated');
            refreshUser?.(); // name / email in the header account menu
        } catch (err) {
            const message = getErrorMessage(err);
            // AT1: "This email is already in use" goes under the Email field
            if (err.response?.status === 400 && message.toLowerCase().includes('email')) {
                setFieldErrors((current) => ({ ...current, email: message }));
            } else {
                setError(message);
            }
        } finally {
            setSaving(false);
        }
    };

    const fieldError = (field) => fieldErrors[field] && <span className="um-field-error">{fieldErrors[field]}</span>;

    return (
        <div className="um-page">
            <div className="um-header">
                <div>
                    <h1>My Profile</h1>
                    <p>View your account information and update your personal details.</p>
                </div>
                <Link to="/change-password" className="um-btn um-btn-outline"><KeyRound size={16} /> Change Password</Link>
            </div>

            {notice && <p className="um-alert um-alert-success" role="status">{notice}</p>}
            {error && <p className="um-alert um-alert-error" role="alert">{error}</p>}

            {!profile && !error && <p className="um-empty">Loading profile…</p>}

            {profile && (
                <form className="um-card ac-card" onSubmit={submit} noValidate>
                    <div className="ac-card-icon"><UserRound size={22} /></div>
                    <div className="ac-stack">
                        <dl className="ac-readonly">
                            <div><dt>Username</dt><dd>{profile.username}</dd></div>
                            <div><dt>Role</dt><dd><span className="um-badge um-badge-role">{roleLabel(profile.role)}</span></dd></div>
                            <div>
                                <dt>Status</dt>
                                <dd><span className={`um-badge ${profile.status === 'ACTIVE' ? 'um-badge-active' : 'um-badge-inactive'}`}>{statusLabel(profile.status)}</span></dd>
                            </div>
                            {profile.hasEmployee && <div><dt>Employee</dt><dd>{profile.employeeCode}{profile.position ? ` · ${profile.position}` : ''}</dd></div>}
                        </dl>
                        <p className="ac-note">Role and status can only be changed by an Admin.</p>

                        {/* BR-03: accounts without an employee record only edit Email */}
                        {profile.hasEmployee && (
                            <>
                                <label className="um-field">
                                    Full Name
                                    <input value={form.fullName} onChange={update('fullName')} maxLength={150} aria-invalid={Boolean(fieldErrors.fullName)} />
                                    {fieldError('fullName')}
                                </label>
                                <label className="um-field">
                                    Phone
                                    <input value={form.phone} onChange={update('phone')} maxLength={20} inputMode="tel" aria-invalid={Boolean(fieldErrors.phone)} />
                                    {fieldError('phone')}
                                </label>
                            </>
                        )}
                        <label className="um-field">
                            Email
                            <input type="email" value={form.email} onChange={update('email')} maxLength={150} aria-invalid={Boolean(fieldErrors.email)} />
                            {fieldError('email') || <span className="um-field-hint">Used to log in and to receive password reset codes.</span>}
                        </label>

                        <div className="um-form-actions">
                            <button type="submit" className="um-btn um-btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Save changes'}</button>
                        </div>
                    </div>
                </form>
            )}
        </div>
    );
}
