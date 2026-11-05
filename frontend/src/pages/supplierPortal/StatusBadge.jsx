import { statusLabel } from './supplierStatuses';

export default function StatusBadge({ status }) {
    return <span className={`sp-status sp-status-${status}`}>{statusLabel(status)}</span>;
}
