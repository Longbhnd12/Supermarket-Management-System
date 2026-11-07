import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import './PasswordInput.css';

/**
 * Password field with a show/hide button (eye icon), like the Login screen.
 * Takes the same props as <input> (value, onChange, autoComplete, aria-invalid...).
 *
 * <PasswordInput value={password} onChange={...} autoComplete="new-password" />
 */
export default function PasswordInput({ className = '', ...inputProps }) {
    const [visible, setVisible] = useState(false);
    const label = visible ? 'Hide password' : 'Show password';

    return (
        <span className="pw-input">
            <input {...inputProps} type={visible ? 'text' : 'password'} className={`pw-input-field ${className}`.trim()} />
            <button
                type="button"
                className="pw-input-toggle"
                onClick={() => setVisible((shown) => !shown)}
                aria-label={label}
                aria-pressed={visible}
                title={label}
            >
                {visible ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
        </span>
    );
}
