import './Pagination.css';

/**
 * Shared reusable Pagination component for SMS data tables.
 * Used across UC31 (Inventory List), UC37 (Stock Receipt List), etc.
 *
 * @param {Object} props
 * @param {number} [props.page=0] - 0-based page index
 * @param {number} [props.size=20] - Rows per page
 * @param {number} [props.totalElements=0] - Total count of records
 * @param {number} [props.totalPages=0] - Total number of pages
 * @param {number[]} [props.pageSizeOptions=[5, 10, 20, 50]] - Page size choices
 * @param {Function} props.onPageChange - Callback when page changes (newPage: number) => void
 * @param {Function} props.onSizeChange - Callback when page size changes (newSize: number) => void
 * @param {string} [props.itemLabel='items'] - e.g. 'products' or 'receipts'
 * @param {string} [props.idPrefix='pagination'] - HTML ID prefix for test stability
 * @param {boolean} [props.disabled=false] - Whether controls are disabled
 * @param {string} [props.className=''] - Additional CSS classes
 */
export default function Pagination({
    page = 0,
    size = 20,
    totalElements = 0,
    totalPages = 0,
    pageSizeOptions = [5, 10, 20, 50],
    onPageChange,
    onSizeChange,
    itemLabel = 'items',
    idPrefix = 'pagination',
    disabled = false,
    className = '',
}) {
    const from = totalElements === 0 ? 0 : page * size + 1;
    const to = Math.min((page + 1) * size, totalElements);
    const displayTotalPages = Math.max(totalPages, 1);

    const getPageNumbers = () => {
        if (totalPages <= 1) return [0];
        const maxButtons = 5;
        if (totalPages <= maxButtons) {
            return Array.from({ length: totalPages }, (_, i) => i);
        }
        let start = Math.max(0, page - 2);
        let end = Math.min(totalPages - 1, start + maxButtons - 1);
        if (end - start < maxButtons - 1) {
            start = Math.max(0, end - maxButtons + 1);
        }
        const nums = [];
        for (let i = start; i <= end; i++) {
            nums.push(i);
        }
        return nums;
    };

    const pageNumbers = getPageNumbers();

    return (
        <div
            id={`${idPrefix}-pagination`}
            className={`sms-pagination ${className}`.trim()}
            role="navigation"
            aria-label={`${itemLabel} pagination`}
        >
            <div className="sms-pagination-left">
                <span className="sms-pagination-info" id={`${idPrefix}-pagination-info`}>
                    {totalElements === 0
                        ? `Showing 0 ${itemLabel}`
                        : `Showing ${from}–${to} of ${totalElements.toLocaleString()} ${itemLabel}`}
                </span>

                <div className="sms-page-size-wrap">
                    <label htmlFor={`${idPrefix}-page-size-select`} className="sms-page-size-label">
                        Rows per page:
                    </label>
                    <select
                        id={`${idPrefix}-page-size-select`}
                        className="sms-page-size-select"
                        value={size}
                        disabled={disabled}
                        onChange={(e) => {
                            const newSize = parseInt(e.target.value, 10);
                            if (onSizeChange) {
                                onSizeChange(newSize);
                            }
                        }}
                    >
                        {pageSizeOptions.map((opt) => (
                            <option key={opt} value={opt}>
                                {opt}
                            </option>
                        ))}
                    </select>
                </div>
            </div>

            <div className="sms-pagination-right">
                <span className="sms-page-count">
                    Page {page + 1} of {displayTotalPages}
                </span>

                <div className="sms-pagination-controls">
                    <button
                        id={`${idPrefix}-first-page-btn`}
                        className="sms-page-btn"
                        onClick={() => onPageChange && onPageChange(0)}
                        disabled={disabled || page === 0}
                        title="First page"
                        aria-label="First page"
                    >
                        «
                    </button>

                    <button
                        id={`${idPrefix}-prev-page-btn`}
                        className="sms-page-btn"
                        onClick={() => onPageChange && onPageChange(Math.max(0, page - 1))}
                        disabled={disabled || page === 0}
                        title="Previous page"
                        aria-label="Previous page"
                    >
                        ‹ Prev
                    </button>

                    {totalElements > 0 &&
                        pageNumbers.map((n) => (
                            <button
                                key={n}
                                className={`sms-page-btn${n === page ? ' sms-page-btn--active' : ''}`}
                                onClick={() => onPageChange && onPageChange(n)}
                                disabled={disabled}
                                aria-label={`Page ${n + 1}`}
                                aria-current={n === page ? 'page' : undefined}
                            >
                                {n + 1}
                            </button>
                        ))}

                    {totalPages > 5 && page < totalPages - 3 && (
                        <span className="sms-page-ellipsis">…</span>
                    )}

                    <button
                        id={`${idPrefix}-next-page-btn`}
                        className="sms-page-btn"
                        onClick={() =>
                            onPageChange && onPageChange(Math.min(totalPages - 1, page + 1))
                        }
                        disabled={disabled || page >= totalPages - 1 || totalPages <= 1}
                        title="Next page"
                        aria-label="Next page"
                    >
                        Next ›
                    </button>

                    <button
                        id={`${idPrefix}-last-page-btn`}
                        className="sms-page-btn"
                        onClick={() => onPageChange && onPageChange(totalPages - 1)}
                        disabled={disabled || page >= totalPages - 1 || totalPages <= 1}
                        title="Last page"
                        aria-label="Last page"
                    >
                        »
                    </button>
                </div>
            </div>
        </div>
    );
}
