import { useEffect, useState, useCallback } from 'react';
import {
    ClipboardList,
    RefreshCw,
    Eye,
    X,
    ChevronLeft,
    ChevronRight,
    Activity,
    UserCheck,
    Layers
} from 'lucide-react';
import systemLogService from '../../services/systemLogService';
import './SystemLogsPage.css';

export default function SystemLogsPage() {
    const [logs, setLogs] = useState([]);
    const [pagination, setPagination] = useState({
        page: 0,
        size: 15,
        totalElements: 0,
        totalPages: 0
    });
    const [loading, setLoading] = useState(true);
    const [alert, setAlert] = useState(null);

    // Filters
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedAction, setSelectedAction] = useState('');
    const [selectedEntity, setSelectedEntity] = useState('');
    const [fromDate, setFromDate] = useState('');
    const [toDate, setToDate] = useState('');

    // Filter Options
    const [actionOptions, setActionOptions] = useState([]);
    const [entityOptions, setEntityOptions] = useState([]);

    // Detail Modal
    const [detailLog, setDetailLog] = useState(null);
    const [loadingDetail, setLoadingDetail] = useState(false);

    const loadFilterOptions = async () => {
        try {
            const [actionsRes, entitiesRes] = await Promise.all([
                systemLogService.getActions(),
                systemLogService.getEntityTypes()
            ]);
            if (actionsRes?.success) setActionOptions(actionsRes.data || []);
            if (entitiesRes?.success) setEntityOptions(entitiesRes.data || []);
        } catch (err) {
            console.error('Failed to load filter options:', err);
            setAlert({ type: 'error', message: 'Could not load audit-log filter options.' });
        }
    };

    const loadLogs = useCallback(async (pageIndex = 0) => {
        setLoading(true);
        try {
            const params = {
                page: pageIndex,
                size: pagination.size,
                sortBy: 'createdAt',
                sortDirection: 'DESC'
            };

            if (fromDate && toDate && fromDate > toDate) {
                setLoading(false);
                setAlert({ type: 'error', message: 'Khoảng ngày không hợp lệ: Ngày bắt đầu không được lớn hơn ngày kết thúc.' });
                return;
            }

            if (searchTerm.trim()) params.search = searchTerm.trim();
            if (selectedAction) params.action = selectedAction;
            if (selectedEntity) params.entityType = selectedEntity;
            if (fromDate) params.fromDate = `${fromDate}T00:00:00`;
            if (toDate) params.toDate = `${toDate}T23:59:59`;

            const res = await systemLogService.getLogs(params);
            setAlert(null);
            if (res?.success && res.data) {
                setLogs(res.data.content || []);
                setPagination({
                    page: res.data.page,
                    size: res.data.size,
                    totalElements: res.data.totalElements,
                    totalPages: res.data.totalPages
                });
            }
        } catch (err) {
            console.error('Failed to load logs:', err);
            setLogs([]);
            setAlert({
                type: 'error',
                message: err.response?.data?.message || 'Could not load audit logs.'
            });
        } finally {
            setLoading(false);
        }
    }, [pagination.size, searchTerm, selectedAction, selectedEntity, fromDate, toDate]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void loadFilterOptions();
        }, 0);
        return () => window.clearTimeout(timer);
    }, []);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void loadLogs(0);
        }, 0);
        return () => window.clearTimeout(timer);
    }, [loadLogs]);

    const handleResetFilters = () => {
        setSearchTerm('');
        setSelectedAction('');
        setSelectedEntity('');
        setFromDate('');
        setToDate('');
    };

    const handleViewDetail = async (logId) => {
        setLoadingDetail(true);
        try {
            const response = await systemLogService.getLogById(logId);
            if (response?.success) {
                setDetailLog(response.data);
            } else {
                setAlert({ type: 'error', message: 'Could not load audit-log details.' });
            }
        } catch (err) {
            setAlert({
                type: 'error',
                message: err.response?.data?.message || 'Could not load audit-log details.'
            });
        } finally {
            setLoadingDetail(false);
        }
    };

    const getActionClass = (action) => {
        if (!action) return 'other';
        const act = action.toUpperCase();
        if (act.includes('CREATE') || act.includes('INSERT') || act.includes('ADD')) return 'create';
        if (act.includes('UPDATE') || act.includes('EDIT') || act.includes('MODIFY')) return 'update';
        if (act.includes('DELETE') || act.includes('REMOVE')) return 'delete';
        if (act.includes('LOGIN')) return 'login';
        if (act.includes('LOGOUT')) return 'logout';
        return 'other';
    };

    const formatDate = (dateStr) => {
        if (!dateStr) return 'N/A';
        try {
            return new Date(dateStr).toLocaleString('vi-VN');
        } catch {
            return dateStr;
        }
    };

    return (
        <div className="logs-container">
            {/* Header */}
            <div className="logs-header">
                <div className="logs-header-title">
                    <h1>System Logs & Audit Trail</h1>
                    <p>Track all system operations, security activities, and data modifications.</p>
                </div>
                <div className="settings-actions">
                    <button className="btn-outline" onClick={() => loadLogs(pagination.page)} disabled={loading}>
                        <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
                        Refresh
                    </button>
                </div>
            </div>

            {alert && (
                <div
                    role="alert"
                    style={{
                        padding: '12px 16px',
                        borderRadius: '8px',
                        color: alert.type === 'error' ? '#b91c1c' : '#166534',
                        background: alert.type === 'error' ? '#fef2f2' : '#f0fdf4',
                        border: `1px solid ${alert.type === 'error' ? '#fecaca' : '#bbf7d0'}`
                    }}
                >
                    {alert.message}
                </div>
            )}

            {/* Quick Metrics */}
            <div className="settings-stats">
                <div className="stat-card">
                    <div className="stat-icon" style={{ background: '#ecfdf5', color: '#059669' }}>
                        <Activity size={22} />
                    </div>
                    <div className="stat-info">
                        <p className="stat-value">{pagination.totalElements}</p>
                        <p className="stat-label">Total Logged Events</p>
                    </div>
                </div>

                <div className="stat-card">
                    <div className="stat-icon" style={{ background: '#fef3c7', color: '#d97706' }}>
                        <Layers size={22} />
                    </div>
                    <div className="stat-info">
                        <p className="stat-value">{actionOptions.length || 'All'}</p>
                        <p className="stat-label">Action Types</p>
                    </div>
                </div>

                <div className="stat-card">
                    <div className="stat-icon">
                        <UserCheck size={22} />
                    </div>
                    <div className="stat-info">
                        <p className="stat-value">Audit Active</p>
                        <p className="stat-label">Logging Engine</p>
                    </div>
                </div>
            </div>

            {/* Filters Bar */}
            <div className="logs-filter-card">
                <div className="filter-item" style={{ gridColumn: 'span 1' }}>
                    <label>Keyword Search</label>
                    <div style={{ position: 'relative' }}>
                        <input
                            type="text"
                            className="filter-input"
                            placeholder="Action, description, IP..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                    </div>
                </div>

                <div className="filter-item">
                    <label>Action</label>
                    <select
                        className="filter-select"
                        value={selectedAction}
                        onChange={(e) => setSelectedAction(e.target.value)}
                    >
                        <option value="">All Actions</option>
                        {actionOptions.map((act) => (
                            <option key={act} value={act}>{act}</option>
                        ))}
                    </select>
                </div>

                <div className="filter-item">
                    <label>Target Entity</label>
                    <select
                        className="filter-select"
                        value={selectedEntity}
                        onChange={(e) => setSelectedEntity(e.target.value)}
                    >
                        <option value="">All Entities</option>
                        {entityOptions.map((ent) => (
                            <option key={ent} value={ent}>{ent}</option>
                        ))}
                    </select>
                </div>

                <div className="filter-item">
                    <label>From Date</label>
                    <input
                        type="date"
                        className="filter-input"
                        value={fromDate}
                        onChange={(e) => setFromDate(e.target.value)}
                    />
                </div>

                <div className="filter-item">
                    <label>To Date</label>
                    <input
                        type="date"
                        className="filter-input"
                        value={toDate}
                        onChange={(e) => setToDate(e.target.value)}
                    />
                </div>

                <div className="filter-actions">
                    <button className="btn-outline" onClick={handleResetFilters} title="Reset filters">
                        Clear
                    </button>
                </div>
            </div>

            {/* Table Card */}
            <div className="settings-card">
                <div className="settings-table-wrapper">
                    {loading ? (
                        <div style={{ padding: '40px', textAlign: 'center', color: '#69748c' }}>
                            <RefreshCw size={24} style={{ animation: 'spin 1s linear infinite' }} />
                            <p style={{ marginTop: '10px' }}>Loading audit logs...</p>
                        </div>
                    ) : logs.length === 0 ? (
                        <div style={{ padding: '40px', textAlign: 'center', color: '#69748c' }}>
                            <ClipboardList size={36} color="#cbd5e1" />
                            <p style={{ marginTop: '10px' }}>No audit logs found matching your filter criteria.</p>
                        </div>
                    ) : (
                        <table className="settings-table">
                            <thead>
                                <tr>
                                    <th>Timestamp</th>
                                    <th>User</th>
                                    <th>Action</th>
                                    <th>Entity</th>
                                    <th>Entity ID</th>
                                    <th>Description</th>
                                    <th>IP Address</th>
                                    <th style={{ textAlign: 'right' }}>View</th>
                                </tr>
                            </thead>
                            <tbody>
                                {logs.map((log) => (
                                    <tr key={log.logId}>
                                        <td style={{ whiteSpace: 'nowrap', color: '#475569', fontSize: '13px' }}>
                                            {formatDate(log.createdAt)}
                                        </td>
                                        <td>
                                            <strong>{log.username || 'System / Anonymous'}</strong>
                                        </td>
                                        <td>
                                            <span className={`action-tag ${getActionClass(log.action)}`}>
                                                {log.action}
                                            </span>
                                        </td>
                                        <td>
                                            {log.entityType ? (
                                                <span className="entity-pill">{log.entityType}</span>
                                            ) : '-'}
                                        </td>
                                        <td style={{ color: '#64748b' }}>
                                            {log.entityId != null ? `#${log.entityId}` : '-'}
                                        </td>
                                        <td>
                                            <div
                                                style={{
                                                    maxWidth: '280px',
                                                    whiteSpace: 'nowrap',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis'
                                                }}
                                                title={log.description}
                                            >
                                                {log.description || '-'}
                                            </div>
                                        </td>
                                        <td style={{ fontFamily: 'Consolas', fontSize: '12px', color: '#64748b' }}>
                                            {log.ipAddress || '-'}
                                        </td>
                                        <td style={{ textAlign: 'right' }}>
                                            <button
                                                className="action-btn"
                                                onClick={() => handleViewDetail(log.logId)}
                                                disabled={loadingDetail}
                                                title="View Details"
                                            >
                                                <Eye size={15} /> {loadingDetail ? 'Loading...' : 'Detail'}
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </div>

                {/* Pagination */}
                {pagination.totalPages > 1 && (
                    <div className="pagination-bar">
                        <span className="pagination-info">
                            Page {pagination.page + 1} of {pagination.totalPages} ({pagination.totalElements} records)
                        </span>
                        <div className="pagination-controls">
                            <button
                                className="page-btn"
                                onClick={() => loadLogs(pagination.page - 1)}
                                disabled={pagination.page === 0}
                            >
                                <ChevronLeft size={16} />
                            </button>

                            {Array.from({ length: Math.min(5, pagination.totalPages) }, (_, i) => {
                                let pageNum = pagination.page - 2 + i;
                                if (pageNum < 0) pageNum = i;
                                if (pageNum >= pagination.totalPages) return null;
                                return (
                                    <button
                                        key={pageNum}
                                        className={`page-btn ${pagination.page === pageNum ? 'active' : ''}`}
                                        onClick={() => loadLogs(pageNum)}
                                    >
                                        {pageNum + 1}
                                    </button>
                                );
                            })}

                            <button
                                className="page-btn"
                                onClick={() => loadLogs(pagination.page + 1)}
                                disabled={pagination.page >= pagination.totalPages - 1}
                            >
                                <ChevronRight size={16} />
                            </button>
                        </div>
                    </div>
                )}
            </div>

            {/* Detail Modal */}
            {detailLog && (
                <div className="modal-overlay" onClick={() => setDetailLog(null)}>
                    <div className="modal-content" style={{ width: '600px' }} onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header">
                            <h3>Audit Log Details (#{detailLog.logId})</h3>
                            <button className="close-btn" onClick={() => setDetailLog(null)}>
                                <X size={20} />
                            </button>
                        </div>

                        <div className="modal-body">
                            <div className="log-detail-grid">
                                <div className="log-detail-label">Timestamp:</div>
                                <div className="log-detail-val">{formatDate(detailLog.createdAt)}</div>

                                <div className="log-detail-label">User:</div>
                                <div className="log-detail-val">
                                    {detailLog.username ? `${detailLog.username} (ID: ${detailLog.userId})` : 'System'}
                                </div>

                                <div className="log-detail-label">Action:</div>
                                <div className="log-detail-val">
                                    <span className={`action-tag ${getActionClass(detailLog.action)}`}>
                                        {detailLog.action}
                                    </span>
                                </div>

                                <div className="log-detail-label">Entity Type:</div>
                                <div className="log-detail-val">{detailLog.entityType || 'N/A'}</div>

                                <div className="log-detail-label">Entity ID:</div>
                                <div className="log-detail-val">{detailLog.entityId != null ? detailLog.entityId : 'N/A'}</div>

                                <div className="log-detail-label">IP Address:</div>
                                <div className="log-detail-val">{detailLog.ipAddress || 'N/A'}</div>

                                <div className="log-detail-label" style={{ gridColumn: 'span 2', marginTop: '6px' }}>
                                    Description / Payload:
                                </div>
                                <div style={{ gridColumn: 'span 2' }}>
                                    <div className="description-box">
                                        {detailLog.description || 'No additional description recorded.'}
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="modal-footer">
                            <button className="btn-primary" onClick={() => setDetailLog(null)}>
                                Close
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
