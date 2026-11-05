import { useCallback, useEffect, useState } from 'react';
import {
    Settings,
    Search,
    RefreshCw,
    Edit3,
    CheckCircle2,
    AlertCircle,
    X,
    Sliders,
    Layers,
    Clock
} from 'lucide-react';
import systemSettingService from '../../services/systemSettingService';
import './SystemSettingsPage.css';

export default function SystemSettingsPage() {
    const [settings, setSettings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedSetting, setSelectedSetting] = useState(null);
    const [editValue, setEditValue] = useState('');
    const [saving, setSaving] = useState(false);
    const [alert, setAlert] = useState(null);

    const showAlert = useCallback((type, message) => {
        setAlert({ type, message });
        setTimeout(() => setAlert(null), 4000);
    }, []);

    const loadSettings = useCallback(async () => {
        setLoading(true);
        try {
            const res = await systemSettingService.getAllSettings();
            if (res?.success) {
                setSettings(res.data || []);
            }
        } catch (err) {
            console.error('Failed to load settings:', err);
            showAlert('error', 'Could not load system settings from server.');
        } finally {
            setLoading(false);
        }
    }, [showAlert]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void loadSettings();
        }, 0);
        return () => window.clearTimeout(timer);
    }, [loadSettings]);

    const handleOpenEdit = (item) => {
        setSelectedSetting(item);
        setEditValue(item.settingValue || '');
    };

    const handleCloseEdit = () => {
        setSelectedSetting(null);
        setEditValue('');
    };

    const handleSave = async (e) => {
        e.preventDefault();
        if (!selectedSetting) return;

        setSaving(true);
        try {
            const res = await systemSettingService.updateSetting(selectedSetting.settingId, {
                settingValue: editValue
            });

            if (res?.success) {
                showAlert('success', `Updated setting '${selectedSetting.settingKey}' successfully!`);
                handleCloseEdit();
                loadSettings();
            }
        } catch (err) {
            const msg = err.response?.data?.message || 'Failed to update setting.';
            showAlert('error', msg);
        } finally {
            setSaving(false);
        }
    };

    const filteredSettings = settings.filter((s) => {
        const term = searchTerm.toLowerCase();
        return (
            (s.settingKey && s.settingKey.toLowerCase().includes(term)) ||
            (s.settingValue && s.settingValue.toLowerCase().includes(term)) ||
            (s.dataType && s.dataType.toLowerCase().includes(term))
        );
    });

    const formatDate = (dateStr) => {
        if (!dateStr) return 'N/A';
        try {
            return new Date(dateStr).toLocaleString('vi-VN');
        } catch {
            return dateStr;
        }
    };

    return (
        <div className="settings-container">
            {/* Header */}
            <div className="settings-header">
                <div className="settings-header-title">
                    <h1>System Configuration</h1>
                    <p>Manage application-wide system parameters, policies, and operational constants.</p>
                </div>
                <div className="settings-actions">
                    <button className="btn-outline" onClick={loadSettings} disabled={loading}>
                        <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
                        Refresh
                    </button>
                </div>
            </div>

            {/* Alert Banner */}
            {alert && (
                <div className={`alert-banner ${alert.type}`}>
                    {alert.type === 'success' ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
                    <span>{alert.message}</span>
                </div>
            )}

            {/* Quick Stats */}
            <div className="settings-stats">
                <div className="stat-card">
                    <div className="stat-icon">
                        <Sliders size={22} />
                    </div>
                    <div className="stat-info">
                        <p className="stat-value">{settings.length}</p>
                        <p className="stat-label">Total Parameters</p>
                    </div>
                </div>

                <div className="stat-card">
                    <div className="stat-icon">
                        <Layers size={22} />
                    </div>
                    <div className="stat-info">
                        <p className="stat-value">
                            {new Set(settings.map((s) => s.dataType)).size}
                        </p>
                        <p className="stat-label">Data Types</p>
                    </div>
                </div>

                <div className="stat-card">
                    <div className="stat-icon">
                        <Clock size={22} />
                    </div>
                    <div className="stat-info">
                        <p className="stat-value">Active</p>
                        <p className="stat-label">System Status</p>
                    </div>
                </div>
            </div>

            {/* Table Card */}
            <div className="settings-card">
                <div className="settings-toolbar">
                    <div className="search-box">
                        <Search size={16} color="#69748c" />
                        <input
                            type="text"
                            placeholder="Search setting by key, value, or type..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                    </div>
                    <span style={{ fontSize: '13px', color: '#69748c' }}>
                        Showing {filteredSettings.length} of {settings.length} items
                    </span>
                </div>

                <div className="settings-table-wrapper">
                    {loading ? (
                        <div style={{ padding: '40px', textAlign: 'center', color: '#69748c' }}>
                            <RefreshCw size={24} style={{ animation: 'spin 1s linear infinite' }} />
                            <p style={{ marginTop: '10px' }}>Loading configuration parameters...</p>
                        </div>
                    ) : filteredSettings.length === 0 ? (
                        <div style={{ padding: '40px', textAlign: 'center', color: '#69748c' }}>
                            <Settings size={36} color="#cbd5e1" />
                            <p style={{ marginTop: '10px' }}>No settings found matching your criteria.</p>
                        </div>
                    ) : (
                        <table className="settings-table">
                            <thead>
                                <tr>
                                    <th>Setting Key</th>
                                    <th>Setting Value</th>
                                    <th>Data Type</th>
                                    <th>Last Updated</th>
                                    <th>Updated By</th>
                                    <th style={{ textAlign: 'right' }}>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredSettings.map((item) => (
                                    <tr key={item.settingId}>
                                        <td>
                                            <span className="key-code">{item.settingKey}</span>
                                        </td>
                                        <td>
                                            <span className="val-text">{item.settingValue}</span>
                                        </td>
                                        <td>
                                            <span
                                                className={`type-badge ${(item.dataType || 'string').toLowerCase()}`}
                                            >
                                                {item.dataType || 'STRING'}
                                            </span>
                                        </td>
                                        <td style={{ color: '#69748c' }}>
                                            {formatDate(item.updatedAt)}
                                        </td>
                                        <td style={{ color: '#69748c' }}>
                                            {item.updatedByUsername || 'System'}
                                        </td>
                                        <td style={{ textAlign: 'right' }}>
                                            <button
                                                className="action-btn"
                                                onClick={() => handleOpenEdit(item)}
                                                title="Edit Parameter"
                                            >
                                                <Edit3 size={15} /> Edit
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </div>
            </div>

            {/* Edit Modal */}
            {selectedSetting && (
                <div className="modal-overlay" onClick={handleCloseEdit}>
                    <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header">
                            <h3>Edit Setting Value</h3>
                            <button className="close-btn" onClick={handleCloseEdit}>
                                <X size={20} />
                            </button>
                        </div>

                        <form onSubmit={handleSave}>
                            <div className="modal-body">
                                <div className="form-group">
                                    <label>Setting Key</label>
                                    <input
                                        type="text"
                                        className="form-control"
                                        value={selectedSetting.settingKey}
                                        disabled
                                        style={{ background: '#f8fafc', color: '#64748b' }}
                                    />
                                </div>

                                <div className="form-group">
                                    <label>Data Type</label>
                                    <input
                                        type="text"
                                        className="form-control"
                                        value={selectedSetting.dataType || 'STRING'}
                                        disabled
                                        style={{ background: '#f8fafc', color: '#64748b' }}
                                    />
                                </div>

                                <div className="form-group">
                                    <label>Setting Value *</label>
                                    {selectedSetting.dataType?.toUpperCase() === 'BOOLEAN' ? (
                                        <select
                                            className="form-control"
                                            value={editValue}
                                            onChange={(e) => setEditValue(e.target.value)}
                                            required
                                        >
                                            <option value="true">true</option>
                                            <option value="false">false</option>
                                        </select>
                                    ) : (
                                        <input
                                            type="text"
                                            className="form-control"
                                            value={editValue}
                                            onChange={(e) => setEditValue(e.target.value)}
                                            placeholder="Enter new configuration value"
                                            required
                                            autoFocus
                                        />
                                    )}
                                    <p className="form-hint">
                                        Type expected: <strong>{selectedSetting.dataType || 'STRING'}</strong>. Maximum 500 characters.
                                    </p>
                                </div>
                            </div>

                            <div className="modal-footer">
                                <button
                                    type="button"
                                    className="btn-outline"
                                    onClick={handleCloseEdit}
                                    disabled={saving}
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    className="btn-primary"
                                    disabled={saving}
                                >
                                    {saving ? 'Saving...' : 'Save Changes'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
