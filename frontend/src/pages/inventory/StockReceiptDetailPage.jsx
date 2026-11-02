import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import StockReceiptService from '../../services/stockReceiptService';
import './StockReceiptDetailPage.css';

// ─── Constants ─────────────────────────────────────────────────────────────────

const STATUS_LABEL = { DRAFT: 'Draft', CONFIRMED: 'Confirmed', CANCELLED: 'Cancelled' };
const STATUS_CLASS = {
    DRAFT:     'srd-badge srd-badge--draft',
    CONFIRMED: 'srd-badge srd-badge--confirmed',
    CANCELLED: 'srd-badge srd-badge--cancelled',
};

// ─── Helpers ───────────────────────────────────────────────────────────────────

function formatDateTime(iso) {
    if (!iso) return '—';
    try {
        return new Date(iso).toLocaleString('en-GB', {
            day: '2-digit', month: 'short', year: 'numeric',
            hour: '2-digit', minute: '2-digit',
        });
    } catch {
        return iso;
    }
}

function formatDate(iso) {
    if (!iso) return '—';
    try {
        return new Date(iso).toLocaleDateString('en-GB', {
            day: '2-digit', month: 'short', year: 'numeric',
        });
    } catch {
        return iso;
    }
}

function formatCurrency(amount) {
    if (amount == null) return '—';
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency', currency: 'VND', minimumFractionDigits: 0,
    }).format(amount);
}

function formatNumber(n) {
    if (n == null) return '—';
    return new Intl.NumberFormat('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 3 }).format(n);
}

function StatusBadge({ status }) {
    return (
        <span className={STATUS_CLASS[status] || 'srd-badge srd-badge--draft'}>
            {STATUS_LABEL[status] || status}
        </span>
    );
}

// ─── Print / PDF helpers ────────────────────────────────────────────────────────

function buildPrintHtml(receipt) {
    const rows = (receipt.details || []).map((d, i) => `
        <tr>
            <td>${i + 1}</td>
            <td>${escHtml(d.productCode)}</td>
            <td>${escHtml(d.productName)}</td>
            <td>${formatNumber(d.receivedQuantity)}</td>
            <td>${formatNumber(d.remainingQuantity)}</td>
            <td>${escHtml(formatCurrency(d.unitCost))}</td>
            <td>${escHtml(d.batchNumber || '—')}</td>
            <td>${escHtml(formatDate(d.expirationDate))}</td>
            <td>${escHtml(formatCurrency(d.subtotal))}</td>
        </tr>`).join('');

    return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Stock Receipt — ${escHtml(receipt.receiptNumber)}</title>
<style>
  body { font-family: Arial, sans-serif; font-size: 12px; color: #111; margin: 0; padding: 24px; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  .subtitle { color: #666; font-size: 11px; margin: 0 0 16px; }
  .meta-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px 24px; margin-bottom: 20px; }
  .meta-item label { display: block; font-size: 10px; text-transform: uppercase; color: #888; margin-bottom: 2px; }
  .meta-item span { font-weight: 600; }
  table { width: 100%; border-collapse: collapse; margin-top: 8px; }
  th { background: #f5f5f5; text-align: left; padding: 6px 8px; font-size: 10px; text-transform: uppercase; border-bottom: 2px solid #ddd; }
  td { padding: 6px 8px; border-bottom: 1px solid #eee; }
  .total-row { font-weight: 700; background: #f9f9f9; }
  .badge { padding: 2px 8px; border-radius: 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; }
  .badge-confirmed { background: #ecfdf5; color: #065f46; border: 1px solid #6ee7b7; }
  .badge-draft { background: #fffbeb; color: #92400e; border: 1px solid #fde68a; }
  .badge-cancelled { background: #fef2f2; color: #991b1b; border: 1px solid #fca5a5; }
  @media print { body { padding: 0; } }
</style>
</head>
<body>
  <h1>Stock Receipt</h1>
  <p class="subtitle">SMS — Supermarket Management System</p>
  <div class="meta-grid">
    <div class="meta-item"><label>Receipt Number</label><span>${escHtml(receipt.receiptNumber)}</span></div>
    <div class="meta-item"><label>Status</label>
      <span class="badge badge-${(receipt.status || '').toLowerCase()}">${escHtml(STATUS_LABEL[receipt.status] || receipt.status)}</span>
    </div>
    <div class="meta-item"><label>Total Cost</label><span>${escHtml(formatCurrency(receipt.totalCost))}</span></div>
    <div class="meta-item"><label>Supplier</label><span>${escHtml(receipt.supplierName)}</span></div>
    <div class="meta-item"><label>PO Reference</label><span>${escHtml(receipt.poNumber || '—')}</span></div>
    <div class="meta-item"><label>Created By</label><span>${escHtml(receipt.createdByUsername)}</span></div>
    <div class="meta-item"><label>Created At</label><span>${escHtml(formatDateTime(receipt.createdAt))}</span></div>
    <div class="meta-item"><label>Received At</label><span>${escHtml(formatDateTime(receipt.receivedAt))}</span></div>
  </div>
  <table>
    <thead>
      <tr>
        <th>#</th><th>SKU</th><th>Product Name</th>
        <th>Qty Received</th><th>Qty Remaining</th><th>Unit Cost</th>
        <th>Batch No.</th><th>Expiry Date</th><th>Subtotal</th>
      </tr>
    </thead>
    <tbody>
      ${rows}
      <tr class="total-row">
        <td colspan="8" style="text-align:right">Total</td>
        <td>${escHtml(formatCurrency(receipt.totalCost))}</td>
      </tr>
    </tbody>
  </table>
  <p style="margin-top:24px;font-size:10px;color:#999">
    Printed on ${new Date().toLocaleString('en-GB')}
  </p>
</body>
</html>`;
}

function escHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function handlePrint(receipt) {
    const html = buildPrintHtml(receipt);
    const win = window.open('', '_blank', 'width=900,height=700');
    if (!win) { alert('Please allow pop-ups to print.'); return; }
    win.document.write(html);
    win.document.close();
    win.focus();
    win.print();
    // close after print dialog closes (not all browsers support this)
    win.onafterprint = () => win.close();
}

async function handleExportPdf(receipt, setPdfLoading) {
    // Use jsPDF + html2canvas loaded via CDN check, or fall back to print-as-PDF
    // We implement a dedicated PDF via the browser's print-to-PDF in a dedicated iframe
    // with a filename hint via the title, plus a Data URI approach for jsPDF when available.

    // Strategy: build the print HTML, inject it into a hidden iframe, trigger print.
    // The browser's "Save as PDF" delivers a named file because the title is set.
    setPdfLoading(true);
    try {
        const html = buildPrintHtml(receipt);
        const blob = new Blob([html], { type: 'text/html' });
        const url = URL.createObjectURL(blob);

        const iframe = document.createElement('iframe');
        iframe.style.cssText = 'position:fixed;top:-10000px;left:-10000px;width:0;height:0;border:none;';
        iframe.src = url;
        document.body.appendChild(iframe);

        iframe.onload = () => {
            // Set the iframe document title so the PDF file is named correctly
            try {
                iframe.contentDocument.title = `StockReceipt-${receipt.receiptNumber}`;
            } catch (_) { /* cross-origin guard */ }
            iframe.contentWindow.focus();
            iframe.contentWindow.print();

            // Cleanup after a delay to allow the print dialog to open
            setTimeout(() => {
                document.body.removeChild(iframe);
                URL.revokeObjectURL(url);
                setPdfLoading(false);
            }, 3000);
        };
    } catch (err) {
        console.error('PDF export failed:', err);
        setPdfLoading(false);
    }
}

// ─── Main Component ─────────────────────────────────────────────────────────────

export default function StockReceiptDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();

    const [receipt, setReceipt] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError]     = useState(null);
    const [notFound, setNotFound] = useState(false);
    const [pdfLoading, setPdfLoading] = useState(false);

    const fetchReceipt = useCallback(() => {
        // Validate the ID is numeric
        const numId = parseInt(id, 10);
        if (!id || isNaN(numId) || numId <= 0) {
            setNotFound(true);
            setLoading(false);
            return;
        }

        setLoading(true);
        setError(null);
        setNotFound(false);

        StockReceiptService.getById(numId)
            .then(res => {
                const data = res.data?.data;
                if (!data) {
                    setNotFound(true);
                } else {
                    setReceipt(data);
                }
            })
            .catch(err => {
                const status = err.response?.status;
                if (status === 404) {
                    setNotFound(true);
                } else {
                    setError(err.response?.data?.message || 'Failed to load receipt details. Please try again.');
                }
            })
            .finally(() => setLoading(false));
    }, [id]);

    useEffect(() => {
        fetchReceipt();
    }, [fetchReceipt]);

    // ── Loading ──
    if (loading) {
        return (
            <div className="srd-page">
                <div className="srd-loading" role="status" aria-live="polite">
                    <div className="srd-spinner" aria-hidden="true" />
                    <span className="srd-loading__text">Loading receipt details…</span>
                </div>
            </div>
        );
    }

    // ── Not Found ──
    if (notFound) {
        return (
            <div className="srd-page">
                <div className="srd-state srd-state--notfound">
                    <svg width="52" height="52" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" strokeWidth="1.2" aria-hidden="true">
                        <circle cx="12" cy="12" r="9"/>
                        <path d="M12 8v4m0 4h.01" strokeLinecap="round"/>
                    </svg>
                    <p className="srd-state__title">Receipt Not Found</p>
                    <p className="srd-state__msg">
                        Stock receipt #{id} does not exist or has been removed.
                    </p>
                    <button className="srd-btn srd-btn--secondary" onClick={() => navigate('/inventory/stock-receipts')}>
                        ← Back to Stock Receipt List
                    </button>
                </div>
            </div>
        );
    }

    // ── API Error ──
    if (error) {
        return (
            <div className="srd-page">
                <div className="srd-state srd-state--error" role="alert">
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                        <circle cx="12" cy="12" r="9"/>
                        <line x1="12" y1="8" x2="12" y2="12"/>
                        <circle cx="12" cy="16" r="0.5" fill="currentColor"/>
                    </svg>
                    <p className="srd-state__title">Something went wrong</p>
                    <p className="srd-state__msg">{error}</p>
                    <button className="srd-btn srd-btn--secondary" onClick={fetchReceipt}>
                        Try again
                    </button>
                </div>
            </div>
        );
    }

    // ── Detail View ──
    const details = receipt.details || [];

    return (
        <div className="srd-page">
            {/* ── Header ── */}
            <div className="srd-header">
                <div className="srd-header-left">
                    <nav className="srd-breadcrumb" aria-label="Breadcrumb">
                        <span className="srd-breadcrumb__item">Inventory</span>
                        <span className="srd-breadcrumb__sep">›</span>
                        <button
                            className="srd-breadcrumb__link"
                            onClick={() => navigate('/inventory/stock-receipts')}
                        >
                            Stock Receipts
                        </button>
                        <span className="srd-breadcrumb__sep">›</span>
                        <span className="srd-breadcrumb__item srd-breadcrumb__item--active">
                            {receipt.receiptNumber}
                        </span>
                    </nav>
                    <div className="srd-title-row">
                        <h1 className="srd-title">{receipt.receiptNumber}</h1>
                        <StatusBadge status={receipt.status} />
                    </div>
                    <p className="srd-subtitle">Stock Receipt Details</p>
                </div>
                <div className="srd-header-actions">
                    <button
                        className="srd-btn srd-btn--ghost"
                        onClick={() => navigate('/inventory/stock-receipts')}
                    >
                        ← Back
                    </button>
                    <button
                        className="srd-btn srd-btn--secondary"
                        onClick={() => handlePrint(receipt)}
                        title="Open print dialog"
                    >
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none"
                             stroke="currentColor" strokeWidth="2" aria-hidden="true">
                            <path d="M6 9V2h12v7"/>
                            <path d="M6 18H4a2 2 0 01-2-2v-5a2 2 0 012-2h16a2 2 0 012 2v5a2 2 0 01-2 2h-2"/>
                            <rect x="6" y="14" width="12" height="8"/>
                        </svg>
                        Print
                    </button>
                    <button
                        className="srd-btn srd-btn--primary"
                        onClick={() => handleExportPdf(receipt, setPdfLoading)}
                        disabled={pdfLoading}
                        title="Export as PDF"
                    >
                        {pdfLoading ? (
                            <>
                                <span className="srd-spinner srd-spinner--sm" aria-hidden="true" />
                                Exporting…
                            </>
                        ) : (
                            <>
                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none"
                                     stroke="currentColor" strokeWidth="2" aria-hidden="true">
                                    <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
                                    <polyline points="7 10 12 15 17 10"/>
                                    <line x1="12" y1="15" x2="12" y2="3"/>
                                </svg>
                                Export PDF
                            </>
                        )}
                    </button>
                </div>
            </div>

            {/* ── Summary Card ── */}
            <div className="srd-card">
                <div className="srd-card__header">
                    <h2 className="srd-card__title">Receipt Summary</h2>
                </div>
                <div className="srd-meta-grid">
                    <div className="srd-meta-item">
                        <span className="srd-meta-label">Receipt Number</span>
                        <span className="srd-meta-value srd-meta-value--mono">{receipt.receiptNumber}</span>
                    </div>
                    <div className="srd-meta-item">
                        <span className="srd-meta-label">Status</span>
                        <StatusBadge status={receipt.status} />
                    </div>
                    <div className="srd-meta-item">
                        <span className="srd-meta-label">Total Cost</span>
                        <span className="srd-meta-value srd-meta-value--cost">{formatCurrency(receipt.totalCost)}</span>
                    </div>
                    <div className="srd-meta-item">
                        <span className="srd-meta-label">Supplier</span>
                        <span className="srd-meta-value">{receipt.supplierName}</span>
                    </div>
                    <div className="srd-meta-item">
                        <span className="srd-meta-label">PO Reference</span>
                        <span className="srd-meta-value">
                            {receipt.poNumber
                                ? <span className="srd-meta-value--mono">{receipt.poNumber}</span>
                                : <span className="srd-null">—</span>}
                        </span>
                    </div>
                    <div className="srd-meta-item">
                        <span className="srd-meta-label">Created By</span>
                        <span className="srd-meta-value">{receipt.createdByUsername}</span>
                    </div>
                    <div className="srd-meta-item">
                        <span className="srd-meta-label">Created At</span>
                        <span className="srd-meta-value">{formatDateTime(receipt.createdAt)}</span>
                    </div>
                    <div className="srd-meta-item">
                        <span className="srd-meta-label">Received At</span>
                        <span className="srd-meta-value">
                            {receipt.receivedAt
                                ? formatDateTime(receipt.receivedAt)
                                : <span className="srd-null">Not yet received</span>}
                        </span>
                    </div>
                </div>
            </div>

            {/* ── Product Details Card ── */}
            <div className="srd-card">
                <div className="srd-card__header">
                    <h2 className="srd-card__title">
                        Product Details
                        <span className="srd-card__count">{details.length} item{details.length !== 1 ? 's' : ''}</span>
                    </h2>
                </div>

                {details.length === 0 ? (
                    <div className="srd-state srd-state--empty">
                        <svg width="40" height="40" viewBox="0 0 24 24" fill="none"
                             stroke="currentColor" strokeWidth="1.2" aria-hidden="true">
                            <rect x="3" y="3" width="18" height="18" rx="2"/>
                            <path d="M3 9h18M9 21V9"/>
                        </svg>
                        <p className="srd-state__title">No product lines</p>
                        <p className="srd-state__msg">This receipt has no detail items.</p>
                    </div>
                ) : (
                    <div className="srd-table-wrap">
                        <table className="srd-table" aria-label="Receipt product details">
                            <thead>
                                <tr>
                                    <th scope="col">#</th>
                                    <th scope="col">SKU</th>
                                    <th scope="col">Product Name</th>
                                    <th scope="col" className="srd-th-num">Qty Received</th>
                                    <th scope="col" className="srd-th-num">Qty Remaining</th>
                                    <th scope="col" className="srd-th-num">Unit Cost</th>
                                    <th scope="col">Batch No.</th>
                                    <th scope="col">Expiry Date</th>
                                    <th scope="col" className="srd-th-num">Subtotal</th>
                                </tr>
                            </thead>
                            <tbody>
                                {details.map((d, idx) => (
                                    <tr key={d.receiptDetailId} className="srd-tr">
                                        <td className="srd-td srd-td--seq">{idx + 1}</td>
                                        <td className="srd-td srd-td--sku">
                                            <span className="srd-sku-chip">{d.productCode}</span>
                                        </td>
                                        <td className="srd-td srd-td--name">{d.productName}</td>
                                        <td className="srd-td srd-td--num">{formatNumber(d.receivedQuantity)}</td>
                                        <td className="srd-td srd-td--num">
                                            {d.remainingQuantity != null
                                                ? formatNumber(d.remainingQuantity)
                                                : <span className="srd-null">—</span>}
                                        </td>
                                        <td className="srd-td srd-td--num">{formatCurrency(d.unitCost)}</td>
                                        <td className="srd-td">
                                            {d.batchNumber
                                                ? <span className="srd-batch">{d.batchNumber}</span>
                                                : <span className="srd-null">—</span>}
                                        </td>
                                        <td className="srd-td srd-td--date">
                                            {d.expirationDate
                                                ? formatDate(d.expirationDate)
                                                : <span className="srd-null">—</span>}
                                        </td>
                                        <td className="srd-td srd-td--num srd-td--subtotal">
                                            {formatCurrency(d.subtotal)}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                            <tfoot>
                                <tr className="srd-tfoot-row">
                                    <td colSpan="8" className="srd-tfoot-label">Total</td>
                                    <td className="srd-tfoot-total">{formatCurrency(receipt.totalCost)}</td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
}
