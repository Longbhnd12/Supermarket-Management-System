import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
    ArrowLeft, Printer, Shield, ShieldCheck, UserCheck, UserX,
    Building2, Phone, Mail, MapPin, Calendar, Clock, CreditCard,
    Award, Lock, CheckCircle2, AlertCircle, Copy, Check,
    Sparkles, RefreshCw, Edit3, Power, Terminal, FileText,
    Key, Users, AlertTriangle, X
} from 'lucide-react';
import { useAuth } from '../../context/authContext';
import EmployeeService from '../../services/employeeService';
import { ALL_MOCK_EMPLOYEES, enrichEmployee } from './mockEmployeesData';
import './EmployeeDetailPage.css';

export default function EmployeeDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { currentUser, hasRole } = useAuth();

    const [employee, setEmployee] = useState(null);
    const [loading, setLoading] = useState(true);
    const [toastMessage, setToastMessage] = useState(null);
    const [copiedField, setCopiedField] = useState(null);

    // Modal dialog states
    const [isDeactivateOpen, setIsDeactivateOpen] = useState(false);
    const [isActivateOpen, setIsActivateOpen] = useState(false);
    const [deactivateReason, setDeactivateReason] = useState('Nghỉ việc theo nguyện vọng');
    const [modalProcessing, setModalProcessing] = useState(false);

    const showToast = (msg) => {
        setToastMessage(msg);
        setTimeout(() => setToastMessage(null), 3500);
    };

    const handleCopy = (text, fieldName) => {
        if (!text) return;
        navigator.clipboard.writeText(text);
        setCopiedField(fieldName);
        showToast(`Đã sao chép ${fieldName}: ${text}`);
        setTimeout(() => setCopiedField(null), 2000);
    };

    // Load employee details
    const fetchEmployee = async () => {
        setLoading(true);
        try {
            // Find in mock dataset first or call backend API
            let rawData = null;
            try {
                // If id is numeric, fetch by ID
                if (!isNaN(id)) {
                    const res = await EmployeeService.getById(id);
                    rawData = res.data?.data || res.data;
                }
            } catch (apiErr) {
                console.warn('Backend employee fetch error, trying mock dataset:', apiErr);
            }

            if (!rawData) {
                rawData = ALL_MOCK_EMPLOYEES.find(
                    (m) => String(m.employeeId) === String(id) || m.employeeCode === id
                ) || ALL_MOCK_EMPLOYEES[0];
            }

            setEmployee(enrichEmployee(rawData));
        } catch (err) {
            console.error('Failed to load employee details:', err);
            const fallback = ALL_MOCK_EMPLOYEES[0];
            setEmployee(enrichEmployee(fallback));
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchEmployee();
    }, [id]);

    // Simulator Switcher
    const handleSimulateSwitch = (targetCode) => {
        navigate(`/employees/${targetCode}`);
        const found = ALL_MOCK_EMPLOYEES.find((m) => m.employeeCode === targetCode);
        if (found) {
            setEmployee(enrichEmployee(found));
            showToast(`Chuyển sang xem hồ sơ mẫu: ${found.fullName} (${found.employeeCode}).`);
        }
    };

    // Status Toggle Handler
    const handleConfirmDeactivate = async () => {
        if (!employee) return;
        setModalProcessing(true);
        try {
            if (employee.employeeId) {
                await EmployeeService.updateStatus(employee.employeeId, 'INACTIVE');
            }
            setEmployee((prev) => ({
                ...prev,
                status: 'INACTIVE',
                posClearanceBadge: 'Deactivated',
                auditSecurityState: 'Suspended'
            }));
            showToast(`Đã tạm dừng hoạt động nhân viên ${employee.fullName} (${employee.employeeCode}).`);
        } catch (err) {
            console.warn('Deactivate fallback to local state:', err);
            setEmployee((prev) => ({
                ...prev,
                status: 'INACTIVE',
                posClearanceBadge: 'Deactivated',
                auditSecurityState: 'Suspended'
            }));
            showToast(`Đã tạm dừng hoạt động nhân viên ${employee.fullName} (Mô phỏng).`);
        } finally {
            setModalProcessing(false);
            setIsDeactivateOpen(false);
        }
    };

    const handleConfirmActivate = async () => {
        if (!employee) return;
        setModalProcessing(true);
        try {
            if (employee.employeeId) {
                await EmployeeService.updateStatus(employee.employeeId, 'ACTIVE');
            }
            setEmployee((prev) => ({
                ...prev,
                status: 'ACTIVE',
                posClearanceBadge: 'Authorized',
                auditSecurityState: 'Verified'
            }));
            showToast(`Đã kích hoạt lại quyền hoạt động cho nhân viên ${employee.fullName}.`);
        } catch (err) {
            console.warn('Activate fallback to local state:', err);
            setEmployee((prev) => ({
                ...prev,
                status: 'ACTIVE',
                posClearanceBadge: 'Authorized',
                auditSecurityState: 'Verified'
            }));
            showToast(`Đã kích hoạt lại quyền hoạt động cho ${employee.fullName} (Mô phỏng).`);
        } finally {
            setModalProcessing(false);
            setIsActivateOpen(false);
        }
    };

    if (loading) {
        return (
            <div className="employee-detail-loading-container">
                <RefreshCw size={28} className="spinning" />
                <span>Đang tải thông tin chi tiết hồ sơ nhân sự...</span>
            </div>
        );
    }

    if (!employee) {
        return (
            <div className="employee-detail-empty-container">
                <AlertCircle size={40} className="empty-icon" />
                <h2>Không tìm thấy nhân viên</h2>
                <p>Hồ sơ nhân viên yêu cầu không tồn tại trong hệ thống.</p>
                <Link to="/employees" className="action-btn primary">
                    <ArrowLeft size={16} />
                    Quay lại danh sách nhân viên
                </Link>
            </div>
        );
    }

    const isActive = employee.status === 'ACTIVE';

    return (
        <div className="employee-detail-page-container">
            {/* Top Toast Banner */}
            {toastMessage && (
                <div className="employee-toast-banner">
                    <CheckCircle2 size={16} />
                    <span>{toastMessage}</span>
                </div>
            )}

            {/* TOP STATE SIMULATOR BAR */}
            <div className="screen-simulator-bar">
                <div className="sim-title-group">
                    <span className="sim-badge-tag">
                        <Sparkles size={13} />
                        SRS 3.6.2 STATE SIMULATOR:
                    </span>
                </div>
                <div className="sim-pills-row">
                    <button
                        type="button"
                        className={`sim-pill ${employee.employeeCode === 'EMP-0101' ? 'active' : ''}`}
                        onClick={() => handleSimulateSwitch('EMP-0101')}
                    >
                        ● Active Staff (EMP-0101 Mai)
                    </button>
                    <button
                        type="button"
                        className={`sim-pill ${employee.employeeCode === 'EMP-0106' ? 'active' : ''}`}
                        onClick={() => handleSimulateSwitch('EMP-0106')}
                    >
                        ● Inactive Staff (EMP-0106 Thắng)
                    </button>
                    <button
                        type="button"
                        className="sim-pill test-modal-btn danger"
                        onClick={() => setIsDeactivateOpen(true)}
                    >
                        Test Deactivate Dialog
                    </button>
                    <button
                        type="button"
                        className="sim-pill test-modal-btn success"
                        onClick={() => setIsActivateOpen(true)}
                    >
                        Test Activate Dialog
                    </button>
                    <button
                        type="button"
                        className="sim-pill test-toast-btn"
                        onClick={() =>
                            showToast('SRS 3.6.2: Personnel clearance synchronized with master security gateway.')
                        }
                    >
                        Trigger Success Toast
                    </button>
                </div>
            </div>

            {/* Breadcrumb & Main Header */}
            <div className="employee-detail-header">
                <div className="header-breadcrumbs">
                    <Link to="/">Home</Link>
                    <span className="crumb-sep">/</span>
                    <span>Operations Hub</span>
                    <span className="crumb-sep">/</span>
                    <span>Directory</span>
                    <span className="crumb-sep">/</span>
                    <Link to="/employees">Employees</Link>
                    <span className="crumb-sep">/</span>
                    <span className="crumb-current">
                        {employee.employeeCode} ({employee.fullName})
                    </span>
                    <span className="crumb-sep">/</span>
                    <span className="crumb-page-tag">Employee Details (UC17)</span>
                </div>

                <div className="header-title-bar">
                    <div className="title-left">
                        <div className="title-heading-row">
                            <h1 className="page-title">Employee Details</h1>
                            <span className="role-access-pill">
                                <ShieldCheck size={14} />
                                Role Access: Administrator & Manager Only (UC17)
                            </span>
                        </div>
                    </div>

                    <div className="header-actions">
                        <Link to="/employees" className="action-btn secondary">
                            <ArrowLeft size={15} />
                            Back to Employee List
                        </Link>
                        <button
                            type="button"
                            className="action-btn icon-only-btn"
                            onClick={() => window.print()}
                            title="In hồ sơ nhân viên"
                        >
                            <Printer size={16} />
                        </button>
                    </div>
                </div>
            </div>

            {/* HERO PROFILE CARD */}
            <div className="employee-hero-card">
                <div className="hero-left-col">
                    {/* Large Initials Avatar with Verified Check Badge */}
                    <div className="hero-avatar-wrap">
                        <div className="hero-avatar-box">
                            {employee.initials || 'NV'}
                        </div>
                        {isActive && (
                            <div className="hero-avatar-check-badge" title="Đã xác thực danh tính">
                                <Check size={12} strokeWidth={3} />
                            </div>
                        )}
                    </div>

                    {/* Main Name & Bio Info */}
                    <div className="hero-info-group">
                        <div className="hero-name-row">
                            <h2 className="hero-full-name">{employee.fullName}</h2>
                            <span className="hero-code-badge font-mono">{employee.employeeCode}</span>
                            {isActive ? (
                                <span className="hero-status-pill active">
                                    <span className="dot green"></span>
                                    Active (Đang làm việc)
                                </span>
                            ) : (
                                <span className="hero-status-pill inactive">
                                    <span className="dot gray"></span>
                                    Inactive ({employee.inactiveReason || 'Tạm dừng'})
                                </span>
                            )}
                        </div>

                        <div className="hero-tags-meta-row">
                            <span className="meta-tag">
                                <Users size={13} />
                                {employee.position} ({employee.department})
                            </span>
                            <span className="meta-sep">•</span>
                            <span className="meta-tag">
                                <Building2 size={13} />
                                {employee.storeFacility}
                            </span>
                            <span className="meta-sep">•</span>
                            <span className="meta-tag">
                                <Clock size={13} />
                                {employee.seniorityDetail}
                            </span>
                        </div>

                        <div className="hero-contact-row">
                            <div className="hero-contact-item">
                                <Phone size={13} />
                                <span className="font-mono">{employee.phone}</span>
                                <button
                                    type="button"
                                    className="copy-btn"
                                    onClick={() => handleCopy(employee.phone, 'Số điện thoại')}
                                    title="Sao chép số điện thoại"
                                >
                                    {copiedField === 'Số điện thoại' ? <Check size={12} /> : <Copy size={12} />}
                                </button>
                            </div>

                            <span className="meta-sep">•</span>

                            <div className="hero-contact-item">
                                <Mail size={13} />
                                <span className="font-mono">{employee.email}</span>
                                <button
                                    type="button"
                                    className="copy-btn"
                                    onClick={() => handleCopy(employee.email, 'Email')}
                                    title="Sao chép email"
                                >
                                    {copiedField === 'Email' ? <Check size={12} /> : <Copy size={12} />}
                                </button>
                            </div>

                            <span className="meta-sep">•</span>

                            <span className="hero-gender-age">
                                {employee.gender === 'FEMALE' ? 'Nữ' : 'Nam'} • {employee.age} tuổi
                            </span>
                        </div>
                    </div>
                </div>

                {/* Hero Right Actions */}
                <div className="hero-right-actions">
                    <button
                        type="button"
                        className="hero-action-btn secondary"
                        onClick={() => navigate('/employees')}
                    >
                        <ArrowLeft size={14} />
                        Back
                    </button>
                    <button
                        type="button"
                        className="hero-action-btn primary"
                        onClick={() => {
                            showToast(`Chuyển đến chỉnh sửa thông tin của ${employee.fullName} (UC18)...`);
                        }}
                    >
                        <Edit3 size={14} />
                        Edit Employee
                    </button>
                    {isActive ? (
                        <button
                            type="button"
                            className="hero-action-btn danger"
                            onClick={() => setIsDeactivateOpen(true)}
                        >
                            <Power size={14} />
                            Deactivate Employee
                        </button>
                    ) : (
                        <button
                            type="button"
                            className="hero-action-btn success"
                            onClick={() => setIsActivateOpen(true)}
                        >
                            <CheckCircle2 size={14} />
                            Activate Employee
                        </button>
                    )}
                </div>
            </div>

            {/* 4 SUB-HERO METRIC CARDS */}
            <div className="subhero-metrics-grid">
                {/* Metric 1: POS Clearance Level */}
                <div className="subhero-metric-card">
                    <div className="subhero-header">
                        <span className="subhero-label">POS CLEARANCE LEVEL</span>
                        <Shield size={16} className="subhero-icon blue" />
                    </div>
                    <div className="subhero-val-row">
                        <span className="subhero-val">{employee.posClearanceLevel}</span>
                        <span className={`subhero-badge ${isActive ? 'green' : 'gray'}`}>
                            {employee.posClearanceBadge}
                        </span>
                    </div>
                    <p className="subhero-desc">{employee.posClearanceDesc}</p>
                </div>

                {/* Metric 2: Retail Seniority */}
                <div className="subhero-metric-card">
                    <div className="subhero-header">
                        <span className="subhero-label">RETAIL SENIORITY</span>
                        <Award size={16} className="subhero-icon amber" />
                    </div>
                    <div className="subhero-val-row">
                        <span className="subhero-val">{employee.seniority}</span>
                        <span className="subhero-meta-date">Since {employee.hireDate}</span>
                    </div>
                    <p className="subhero-desc">Continuous contract • Regular staff</p>
                </div>

                {/* Metric 3: Shift Compliance */}
                <div className="subhero-metric-card">
                    <div className="subhero-header">
                        <span className="subhero-label">SHIFT COMPLIANCE</span>
                        <Clock size={16} className="subhero-icon blue" />
                    </div>
                    <div className="subhero-val-row">
                        <span className="subhero-val">{employee.shiftCompliance}%</span>
                        <span className="subhero-badge blue">On Time</span>
                    </div>
                    <p className="subhero-desc">
                        {employee.unexcusedAbsences} unexcused absences • {employee.totalShifts} total shifts
                    </p>
                </div>

                {/* Metric 4: Audit Security State */}
                <div className="subhero-metric-card">
                    <div className="subhero-header">
                        <span className="subhero-label">AUDIT SECURITY STATE</span>
                        <ShieldCheck size={16} className="subhero-icon purple" />
                    </div>
                    <div className="subhero-val-row">
                        <span className="subhero-val">{employee.auditSecurityState}</span>
                        <span className="subhero-badge dark">{employee.auditSyncStatus}</span>
                    </div>
                    <p className="subhero-desc">Last verified {employee.lastVerifiedDate} by Manager</p>
                </div>
            </div>

            {/* 2-COLUMN MAIN CONTENT GRID */}
            <div className="employee-content-2col-grid">
                {/* LEFT COLUMN: Personal Info & Employment Placement */}
                <div className="content-left-col">
                    {/* SECTION 1: Personal Information */}
                    <div className="dossier-card">
                        <div className="dossier-card-header">
                            <div className="header-title-wrap">
                                <Users size={16} className="section-icon blue" />
                                <h3>1. Personal Information</h3>
                            </div>
                            <span className="section-badge-code">SRS 3.6.2 COMPLIANCE</span>
                        </div>

                        <div className="dossier-fields-grid">
                            <div className="dossier-field-col">
                                <label className="field-caption">FULL LEGAL NAME</label>
                                <span className="field-value strong">{employee.fullName}</span>
                            </div>

                            <div className="dossier-field-col">
                                <label className="field-caption">GENDER</label>
                                <span className="field-value">
                                    {employee.gender === 'FEMALE' ? 'Nữ (Female)' : 'Nam (Male)'}
                                </span>
                            </div>

                            <div className="dossier-field-col">
                                <label className="field-caption">DATE OF BIRTH</label>
                                <span className="field-value font-mono">
                                    {employee.dateOfBirth} ({employee.age} tuổi)
                                </span>
                            </div>

                            <div className="dossier-field-col">
                                <label className="field-caption">CITIZEN ID / CCCD</label>
                                <div className="val-with-badge">
                                    <span className="field-value font-mono">{employee.citizenId}</span>
                                    {employee.isCccdVerified && (
                                        <span className="micro-badge green">Verified</span>
                                    )}
                                </div>
                            </div>

                            <div className="dossier-field-col">
                                <label className="field-caption">PRIMARY PHONE NUMBER</label>
                                <div className="val-with-badge">
                                    <span className="field-value font-mono">{employee.phone}</span>
                                    <span className="micro-badge green">SMS Alerts Active</span>
                                </div>
                            </div>

                            <div className="dossier-field-col">
                                <label className="field-caption">CORPORATE EMAIL ADDRESS</label>
                                <span className="field-value font-mono link-text">{employee.email}</span>
                            </div>

                            <div className="dossier-field-col full-width">
                                <label className="field-caption">REGISTERED RESIDENTIAL ADDRESS</label>
                                <div className="address-display-box">
                                    <MapPin size={14} className="map-icon" />
                                    <span>{employee.address}</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* SECTION 2: Employment & Organizational Placement */}
                    <div className="dossier-card">
                        <div className="dossier-card-header">
                            <div className="header-title-wrap">
                                <Building2 size={16} className="section-icon blue" />
                                <h3>2. Employment & Organizational Placement</h3>
                            </div>
                            <span className="section-badge-code">ORG-REF #04</span>
                        </div>

                        <div className="dossier-fields-grid">
                            <div className="dossier-field-col">
                                <label className="field-caption">EMPLOYEE CODE (IMMUTABLE KEY)</label>
                                <div className="val-with-badge">
                                    <span className="field-value font-mono primary-code">
                                        {employee.employeeCode}
                                    </span>
                                    <span className="micro-badge gray">Primary Key</span>
                                </div>
                            </div>

                            <div className="dossier-field-col">
                                <label className="field-caption">EMPLOYMENT STATUS</label>
                                <span className={`field-value status-text ${isActive ? 'green' : 'gray'}`}>
                                    ● {isActive ? 'Active (Đang làm việc - Hoạt động)' : 'Inactive (Tạm dừng)'}
                                </span>
                            </div>

                            <div className="dossier-field-col">
                                <label className="field-caption">POSITION / OPERATIONAL ROLE</label>
                                <span className="field-value strong">
                                    {employee.position} ({employee.department})
                                </span>
                            </div>

                            <div className="dossier-field-col">
                                <label className="field-caption">ASSIGNED STORE FACILITY</label>
                                <span className="field-value">
                                    🏪 {employee.storeFacility}
                                </span>
                            </div>

                            <div className="dossier-field-col">
                                <label className="field-caption">HIRE DATE</label>
                                <span className="field-value font-mono">
                                    {employee.hireDate} ({employee.seniorityDetail})
                                </span>
                            </div>

                            <div className="dossier-field-col">
                                <label className="field-caption">SUPERMARKET DEPARTMENT</label>
                                <span className="field-value">
                                    {employee.supermarketDept}
                                </span>
                            </div>

                            {/* Direct Supervisory Line */}
                            <div className="dossier-field-col full-width supervisor-field">
                                <label className="field-caption">DIRECT SUPERVISORY LINE</label>
                                <div className="supervisor-card">
                                    <div className="supervisor-avatar">
                                        {employee.supervisor?.initials || 'PT'}
                                    </div>
                                    <div className="supervisor-info">
                                        <strong className="sup-name">
                                            {employee.supervisor?.name} ({employee.supervisor?.code})
                                        </strong>
                                        <span className="sup-role">
                                            {employee.supervisor?.role} • {employee.storeFacility}
                                        </span>
                                    </div>
                                    <CheckCircle2 size={16} className="sup-verified-icon" />
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                {/* RIGHT COLUMN: System & Terminal Auth & Audit Trail */}
                <div className="content-right-col">
                    {/* SECTION 3: System & Terminal Auth */}
                    <div className="dossier-card">
                        <div className="dossier-card-header">
                            <div className="header-title-wrap">
                                <Lock size={16} className="section-icon blue" />
                                <h3>3. System & Terminal Auth</h3>
                            </div>
                            <Lock size={14} className="lock-sub-icon" />
                        </div>

                        <div className="terminal-auth-list">
                            {/* Linked Account */}
                            <div className="terminal-row">
                                <div className="term-info">
                                    <span className="term-label">LINKED SMS ACCOUNT</span>
                                    <span className="term-val font-mono">
                                        {employee.linkedAccount?.username} • {employee.linkedAccount?.role}
                                    </span>
                                </div>
                                <span className={`term-badge ${isActive ? 'green' : 'gray'}`}>
                                    {employee.linkedAccount?.status}
                                </span>
                            </div>

                            {/* Terminal Clearance Level */}
                            <div className="terminal-row">
                                <div className="term-info">
                                    <span className="term-label">Terminal Clearance Level</span>
                                    <span className="term-val">{employee.posClearanceLevel}</span>
                                </div>
                                <span className="term-badge blue">POS Standard</span>
                            </div>

                            {/* Assigned Register Lanes */}
                            <div className="terminal-row">
                                <div className="term-info">
                                    <span className="term-label">Assigned Register Lanes</span>
                                    <span className="term-val">{employee.assignedLanes}</span>
                                </div>
                                <CreditCard size={15} className="term-icon" />
                            </div>

                            {/* Price Override & Refunds */}
                            <div className="terminal-row">
                                <div className="term-info">
                                    <span className="term-label">Price Override & Refunds</span>
                                    <span className="term-val">{employee.priceOverride}</span>
                                </div>
                                <span className="term-badge red">Restricted</span>
                            </div>

                            {/* Barcode Scanner Provision */}
                            <div className="terminal-row">
                                <div className="term-info">
                                    <span className="term-label">Barcode Scanner Provision</span>
                                    <span className="term-val">{employee.barcodeScanner}</span>
                                </div>
                                <Terminal size={15} className="term-icon green" />
                            </div>

                            {/* Credential Policy Callout */}
                            <div className="credential-policy-box">
                                <Key size={16} className="policy-icon" />
                                <div className="policy-text">
                                    <strong>Credential Policy</strong>
                                    <p>
                                        Password expiration: 90 days cycle. 2FA hardware key required for supervisor overrides per Retail Security Standard v4.
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* SECTION 4: Audit & Record Integrity (UC08) */}
                    <div className="dossier-card">
                        <div className="dossier-card-header">
                            <div className="header-title-wrap">
                                <FileText size={16} className="section-icon blue" />
                                <h3>4. Audit & Record Integrity</h3>
                            </div>
                            <span className="section-badge-code">UC08 Audit</span>
                        </div>

                        <div className="audit-timeline-list">
                            {employee.auditLogs?.map((log, idx) => (
                                <div key={idx} className="timeline-item">
                                    <div className="timeline-marker">
                                        <div className="marker-dot"></div>
                                        {idx < employee.auditLogs.length - 1 && <div className="marker-line"></div>}
                                    </div>
                                    <div className="timeline-content">
                                        <strong className="log-title">{log.title}</strong>
                                        <span className="log-time">{log.timestamp}</span>
                                        {log.operator && (
                                            <span className="log-operator">By {log.operator}</span>
                                        )}
                                    </div>
                                </div>
                            ))}

                            {/* Regulatory SRS Notice Callout */}
                            <div className="srs-notice-box">
                                <ShieldCheck size={16} className="notice-icon" />
                                <div className="notice-text">
                                    <strong>Regulatory SRS Notice:</strong>
                                    <p>
                                        In compliance with SRS 3.6, all employee modifications, status shifts, or register reassignment events are permanently ledger-tracked in UC08 Audit Logs with non-repudiation timestamps.
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {/* STICKY BOTTOM ACTION BAR */}
            <div className="employee-detail-bottom-bar">
                <div className="bottom-left-indicator">
                    <span className="dot blue"></span>
                    <span>
                        Viewing Read-Only Detailed Dossier for{' '}
                        <strong>
                            {employee.employeeCode} ({employee.fullName})
                        </strong>
                    </span>
                </div>

                <div className="bottom-right-btns">
                    <Link to="/employees" className="action-btn secondary">
                        <ArrowLeft size={15} />
                        Back to Employee List
                    </Link>
                    <button
                        type="button"
                        className="action-btn primary large-btn"
                        onClick={() => {
                            showToast(`Chuyển đến chỉnh sửa thông tin của ${employee.fullName} (UC18)...`);
                        }}
                    >
                        <Edit3 size={15} />
                        Edit Employee Information (UC18)
                    </button>
                </div>
            </div>

            {/* DEACTIVATION CONFIRMATION MODAL */}
            {isDeactivateOpen && (
                <div className="modal-backdrop" onClick={() => setIsDeactivateOpen(false)}>
                    <div className="modal-container" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header">
                            <div className="modal-title-with-icon">
                                <AlertTriangle size={20} className="modal-warn-icon red" />
                                <h3>Xác nhận tạm dừng nhân viên</h3>
                            </div>
                            <button
                                type="button"
                                className="modal-close-btn"
                                onClick={() => setIsDeactivateOpen(false)}
                            >
                                <X size={16} />
                            </button>
                        </div>

                        <div className="modal-body">
                            <p className="modal-lead-text">
                                Bạn có chắc chắn muốn chuyển trạng thái của nhân viên{' '}
                                <strong>{employee.fullName}</strong> ({employee.employeeCode}) sang{' '}
                                <strong className="text-danger">Tạm dừng (Inactive)</strong>?
                            </p>

                            <div className="modal-consequence-box">
                                <strong>Hệ quả nghiệp vụ theo SRS 3.6 & UC17:</strong>
                                <ul>
                                    <li>Khóa phiên đăng nhập POS Cashier và thiết bị quét mã vạch cầm tay.</li>
                                    <li>Tạm ngưng quyền phê duyệt đơn hàng hoặc kiểm đếm kho.</li>
                                    <li>Lịch sử làm việc và bản ghi ca trước đây vẫn được lưu trữ vĩnh viễn trong UC08.</li>
                                </ul>
                            </div>

                            <div className="modal-input-group">
                                <label>Lý do tạm dừng / nghỉ việc:</label>
                                <select
                                    value={deactivateReason}
                                    onChange={(e) => setDeactivateReason(e.target.value)}
                                >
                                    <option value="Nghỉ việc theo nguyện vọng">Nghỉ việc theo nguyện vọng cá nhân</option>
                                    <option value="Nghỉ ốm điều trị y tế dài hạn">Nghỉ ốm điều trị y tế dài hạn (Medical Leave)</option>
                                    <option value="Tạm đình chỉ công tác phục vụ xác minh">Tạm đình chỉ công tác</option>
                                    <option value="Chuyển công tác chi nhánh khác">Chuyển công tác chi nhánh khác</option>
                                </select>
                            </div>
                        </div>

                        <div className="modal-footer">
                            <button
                                type="button"
                                className="action-btn secondary"
                                onClick={() => setIsDeactivateOpen(false)}
                            >
                                Hủy bỏ
                            </button>
                            <button
                                type="button"
                                className="action-btn danger"
                                onClick={handleConfirmDeactivate}
                                disabled={modalProcessing}
                            >
                                {modalProcessing ? 'Đang xử lý...' : 'Xác nhận tạm dừng (Deactivate)'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ACTIVATION CONFIRMATION MODAL */}
            {isActivateOpen && (
                <div className="modal-backdrop" onClick={() => setIsActivateOpen(false)}>
                    <div className="modal-container" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header">
                            <div className="modal-title-with-icon">
                                <CheckCircle2 size={20} className="modal-warn-icon green" />
                                <h3>Kích hoạt lại nhân viên</h3>
                            </div>
                            <button
                                type="button"
                                className="modal-close-btn"
                                onClick={() => setIsActivateOpen(false)}
                            >
                                <X size={16} />
                            </button>
                        </div>

                        <div className="modal-body">
                            <p className="modal-lead-text">
                                Bạn muốn mở lại quyền làm việc cho nhân viên{' '}
                                <strong>{employee.fullName}</strong> ({employee.employeeCode})?
                            </p>

                            <div className="modal-consequence-box green">
                                <strong>Quyền hạn được khôi phục:</strong>
                                <ul>
                                    <li>Mở lại tài khoản đăng nhập máy POS thu ngân tại Store #04.</li>
                                    <li>Được phân ca trực và gán làn thu ngân tiêu chuẩn.</li>
                                    <li>Sự kiện kích hoạt sẽ được ghi vết bảo mật vào UC08 Audit Logs.</li>
                                </ul>
                            </div>
                        </div>

                        <div className="modal-footer">
                            <button
                                type="button"
                                className="action-btn secondary"
                                onClick={() => setIsActivateOpen(false)}
                            >
                                Đóng
                            </button>
                            <button
                                type="button"
                                className="action-btn success"
                                onClick={handleConfirmActivate}
                                disabled={modalProcessing}
                            >
                                {modalProcessing ? 'Đang xử lý...' : 'Xác nhận kích hoạt (Activate)'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

