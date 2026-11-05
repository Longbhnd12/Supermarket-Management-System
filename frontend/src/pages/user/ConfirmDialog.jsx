import { useEffect } from 'react';

// Confirmation before deactivating / deleting an account (UC05 AT2, AT3)
export default function ConfirmDialog({ title, message, confirmLabel = 'Confirm', danger = false, busy = false, onConfirm, onCancel }) {
    useEffect(() => {
        const onKey = (event) => event.key === 'Escape' && !busy && onCancel();
        document.addEventListener('keydown', onKey);
        return () => document.removeEventListener('keydown', onKey);
    }, [busy, onCancel]);

    return (
        <div className="um-dialog-backdrop" role="presentation" onClick={() => !busy && onCancel()}>
            <div className="um-dialog" role="dialog" aria-modal="true" aria-labelledby="um-dialog-title" onClick={(event) => event.stopPropagation()}>
                <h2 id="um-dialog-title">{title}</h2>
                <p>{message}</p>
                <div className="um-dialog-actions">
                    <button type="button" className="um-btn um-btn-outline" onClick={onCancel} disabled={busy}>Cancel</button>
                    <button type="button" className={`um-btn ${danger ? 'um-btn-danger' : 'um-btn-primary'}`} onClick={onConfirm} disabled={busy}>
                        {busy ? 'Please wait…' : confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}
