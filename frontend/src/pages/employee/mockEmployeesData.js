/**
 * mockEmployeesData.js
 * Master mock dataset for UC17 (View Employees: List & Details)
 * Exactly mirrors the 36 store personnel specifications from SRS 3.6.1 & 3.6.2:
 * - Total: 36
 * - In-Service: 32 Active Staff (88.9%)
 * - Inactive / On Leave: 4 Staff (2 Medical • 2 Resigned)
 * - Role Allocation: 18 Cashiers (50%), 8 Inventory Staff (22.2%), 6 Managers (16.7%), 4 Administrators (11.1%)
 */

export const MOCK_EMPLOYEES = [
    {
        employeeId: 1,
        employeeCode: 'EMP-0101',
        fullName: 'Trần Thị Mai',
        initials: 'TM',
        position: 'Cashier',
        department: 'Frontline Retail',
        supermarketDept: 'Front End Operations & Checkout Services',
        phone: '0903 123 456',
        email: 'mai.tran@smsretail.vn',
        status: 'ACTIVE',
        gender: 'FEMALE',
        dateOfBirth: '1998-05-14',
        age: 27,
        citizenId: '079198004521',
        isCccdVerified: true,
        address: '124/8 Nguyễn Thị Minh Khai, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh',
        hireDate: '2023-03-15',
        seniority: '2y 2m',
        seniorityDetail: '2 năm 2 tháng làm việc liên tục',
        storeFacility: 'Store #04 - Dist 1 Flagship',
        supervisor: {
            name: 'Phạm Quốc Tuấn',
            code: 'EMP-0012',
            role: 'Store Operations Manager',
            initials: 'PT'
        },
        // Operational & POS Clearances
        posClearanceLevel: 'Level 1 (Cashier)',
        posClearanceBadge: 'Authorized',
        posClearanceDesc: 'Lanes 01-04 active registry barcode scanner',
        assignedLanes: 'Lanes 01, 02, 03, 04 (Express & Standard)',
        priceOverride: 'Strictly Restricted (Manager Key Required)',
        barcodeScanner: 'Zebra DS2208 Handheld Synced',
        shiftCompliance: 99.4,
        unexcusedAbsences: 0,
        totalShifts: 412,
        auditSecurityState: 'Verified',
        auditSyncStatus: 'Synced UC08',
        lastVerifiedDate: '10/01/2026',
        linkedAccount: {
            username: 'mai.tran',
            role: 'POS Cashier',
            status: 'Active'
        },
        auditLogs: [
            {
                title: 'Initial Employee Record Created',
                timestamp: '15/03/2023 08:30:14',
                operator: 'ADM-01 (System Admin)'
            },
            {
                title: 'Annual Clearance Review Completed',
                timestamp: '10/01/2026 14:15:02',
                operator: 'Phạm Quốc Tuấn (MGR-04)'
            },
            {
                title: 'Status: Active Frontline Shift Clearance',
                timestamp: 'Realtime terminal token valid. Session synced with Store #04 Master Gateway.',
                operator: 'System Gateway'
            }
        ]
    },
    {
        employeeId: 2,
        employeeCode: 'EMP-0102',
        fullName: 'Lê Văn Hùng',
        initials: 'LH',
        position: 'Inventory Staff',
        department: 'Warehouse & Receiving',
        supermarketDept: 'Warehouse Logistics & Inbound Stock Inspection',
        phone: '0918 234 567',
        email: 'hung.le@smsretail.vn',
        status: 'ACTIVE',
        gender: 'MALE',
        dateOfBirth: '1995-08-20',
        age: 30,
        citizenId: '079195003312',
        isCccdVerified: true,
        address: '45/2 Điện Biên Phủ, Phường 15, Quận Bình Thạnh, TP. Hồ Chí Minh',
        hireDate: '2022-01-10',
        seniority: '3y 4m',
        seniorityDetail: '3 năm 4 tháng làm việc liên tục',
        storeFacility: 'Store #04 - Dist 1 Flagship',
        supervisor: {
            name: 'Phạm Quốc Tuấn',
            code: 'EMP-0012',
            role: 'Store Operations Manager',
            initials: 'PT'
        },
        posClearanceLevel: 'Level 2 (Inventory Clerk)',
        posClearanceBadge: 'Authorized',
        posClearanceDesc: 'Inbound PO verification & stock adjustment allowed',
        assignedLanes: 'Stock Receiving Docks Bay 1-3',
        priceOverride: 'Restricted (Read-only purchase prices)',
        barcodeScanner: 'Zebra TC21 Mobile Barcode Scanner Synced',
        shiftCompliance: 98.7,
        unexcusedAbsences: 1,
        totalShifts: 580,
        auditSecurityState: 'Verified',
        auditSyncStatus: 'Synced UC08',
        lastVerifiedDate: '15/01/2026',
        linkedAccount: {
            username: 'hung.le',
            role: 'Inventory Staff',
            status: 'Active'
        },
        auditLogs: [
            {
                title: 'Initial Employee Record Created',
                timestamp: '10/01/2022 09:00:00',
                operator: 'ADM-01 (System Admin)'
            },
            {
                title: 'Assigned Handheld Scanner Zebra TC21',
                timestamp: '12/01/2022 10:30:00',
                operator: 'IT Support'
            }
        ]
    },
    {
        employeeId: 3,
        employeeCode: 'EMP-0103',
        fullName: 'Nguyễn Hoàng Long',
        initials: 'NL',
        position: 'Cashier',
        department: 'Frontline Retail',
        supermarketDept: 'Front End Operations & Checkout Services',
        phone: '0938 345 678',
        email: 'long.nguyen@smsretail.vn',
        status: 'ACTIVE',
        gender: 'MALE',
        dateOfBirth: '1999-11-05',
        age: 26,
        citizenId: '079199008821',
        isCccdVerified: true,
        address: '88 Cách Mạng Tháng 8, Phường 7, Quận 3, TP. Hồ Chí Minh',
        hireDate: '2023-06-01',
        seniority: '1y 11m',
        seniorityDetail: '1 năm 11 tháng làm việc liên tục',
        storeFacility: 'Store #04 - Dist 1 Flagship',
        supervisor: {
            name: 'Phạm Quốc Tuấn',
            code: 'EMP-0012',
            role: 'Store Operations Manager',
            initials: 'PT'
        },
        posClearanceLevel: 'Level 1 (Cashier)',
        posClearanceBadge: 'Authorized',
        posClearanceDesc: 'Lanes 05-08 active registry barcode scanner',
        assignedLanes: 'Lanes 05, 06 (Standard)',
        priceOverride: 'Strictly Restricted (Manager Key Required)',
        barcodeScanner: 'Zebra DS2208 Handheld Synced',
        shiftCompliance: 99.1,
        unexcusedAbsences: 0,
        totalShifts: 320,
        auditSecurityState: 'Verified',
        auditSyncStatus: 'Synced UC08',
        lastVerifiedDate: '05/01/2026',
        linkedAccount: {
            username: 'long.nguyen',
            role: 'POS Cashier',
            status: 'Active'
        },
        auditLogs: [
            {
                title: 'Initial Employee Record Created',
                timestamp: '01/06/2023 08:30:00',
                operator: 'ADM-01 (System Admin)'
            }
        ]
    },
    {
        employeeId: 4,
        employeeCode: 'EMP-0104',
        fullName: 'Phạm Quốc Tuấn',
        initials: 'PT',
        position: 'Manager',
        department: 'Store Operations Supervisor',
        supermarketDept: 'Store Management & Operational Governance',
        phone: '0909 456 789',
        email: 'tuan.pham@smsretail.vn',
        status: 'ACTIVE',
        gender: 'MALE',
        dateOfBirth: '1988-03-12',
        age: 38,
        citizenId: '079188001144',
        isCccdVerified: true,
        address: '12 Võ Thị Sáu, Phường Đa Kao, Quận 1, TP. Hồ Chí Minh',
        hireDate: '2020-08-15',
        seniority: '4y 9m',
        seniorityDetail: '4 năm 9 tháng làm việc liên tục',
        storeFacility: 'Store #04 - Dist 1 Flagship',
        supervisor: {
            name: 'Nguyễn Văn An',
            code: 'EMP-0001',
            role: 'General Store Director',
            initials: 'NA'
        },
        posClearanceLevel: 'Level 3 (Store Operations Manager)',
        posClearanceBadge: 'Authorized',
        posClearanceDesc: 'All lanes override, refund authorization & PO approval',
        assignedLanes: 'Master Store Controller #04',
        priceOverride: 'Authorized (Supervisor PIN & 2FA Key)',
        barcodeScanner: 'Master Terminal Multi-Device Console',
        shiftCompliance: 99.8,
        unexcusedAbsences: 0,
        totalShifts: 1120,
        auditSecurityState: 'Verified',
        auditSyncStatus: 'Synced UC08',
        lastVerifiedDate: '01/01/2026',
        linkedAccount: {
            username: 'tuan.pham',
            role: 'Store Manager',
            status: 'Active'
        },
        auditLogs: [
            {
                title: 'Initial Employee Record Created',
                timestamp: '15/08/2020 08:00:00',
                operator: 'ADM-01 (System Admin)'
            },
            {
                title: 'Promoted to Store Operations Supervisor',
                timestamp: '01/01/2022 09:00:00',
                operator: 'Board of Directors'
            }
        ]
    },
    {
        employeeId: 5,
        employeeCode: 'EMP-0105',
        fullName: 'Đặng Thị Bích Ngọc',
        initials: 'DN',
        position: 'Cashier',
        department: 'Frontline Retail',
        supermarketDept: 'Front End Operations & Checkout Services',
        phone: '0912 567 890',
        email: 'ngoc.dang@smsretail.vn',
        status: 'ACTIVE',
        gender: 'FEMALE',
        dateOfBirth: '2000-09-28',
        age: 25,
        citizenId: '079200005531',
        isCccdVerified: true,
        address: '210 Hai Bà Trưng, Phường Tân Định, Quận 1, TP. Hồ Chí Minh',
        hireDate: '2023-09-15',
        seniority: '1y 8m',
        seniorityDetail: '1 năm 8 tháng làm việc liên tục',
        storeFacility: 'Store #04 - Dist 1 Flagship',
        supervisor: {
            name: 'Phạm Quốc Tuấn',
            code: 'EMP-0012',
            role: 'Store Operations Manager',
            initials: 'PT'
        },
        posClearanceLevel: 'Level 1 (Cashier)',
        posClearanceBadge: 'Authorized',
        posClearanceDesc: 'Lanes 07-08 active registry barcode scanner',
        assignedLanes: 'Lanes 07, 08 (Standard)',
        priceOverride: 'Strictly Restricted (Manager Key Required)',
        barcodeScanner: 'Zebra DS2208 Handheld Synced',
        shiftCompliance: 98.9,
        unexcusedAbsences: 0,
        totalShifts: 290,
        auditSecurityState: 'Verified',
        auditSyncStatus: 'Synced UC08',
        lastVerifiedDate: '12/01/2026',
        linkedAccount: {
            username: 'ngoc.dang',
            role: 'POS Cashier',
            status: 'Active'
        },
        auditLogs: [
            {
                title: 'Initial Employee Record Created',
                timestamp: '15/09/2023 09:15:00',
                operator: 'ADM-01 (System Admin)'
            }
        ]
    },
    {
        employeeId: 6,
        employeeCode: 'EMP-0106',
        fullName: 'Vũ Đức Thắng',
        initials: 'VT',
        position: 'Inventory Staff',
        department: 'Cold Storage Logistics',
        supermarketDept: 'Cold Chain & Frozen Inventory Logistics',
        phone: '0988 678 901',
        email: 'thang.vu@smsretail.vn',
        status: 'INACTIVE',
        inactiveReason: 'Medical Leave (Nghỉ ốm điều trị y tế)',
        gender: 'MALE',
        dateOfBirth: '1994-02-17',
        age: 32,
        citizenId: '079194002288',
        isCccdVerified: true,
        address: '67 Hoàng Hoa Thám, Phường 13, Quận Tân Bình, TP. Hồ Chí Minh',
        hireDate: '2021-04-01',
        seniority: '4y 1m',
        seniorityDetail: '4 năm 1 tháng làm việc',
        storeFacility: 'Store #04 - Dist 1 Flagship',
        supervisor: {
            name: 'Phạm Quốc Tuấn',
            code: 'EMP-0012',
            role: 'Store Operations Manager',
            initials: 'PT'
        },
        posClearanceLevel: 'Level 2 (Inventory Clerk)',
        posClearanceBadge: 'Deactivated',
        posClearanceDesc: 'Credentials locked during medical leave',
        assignedLanes: 'Cold Storage Vaults A-B (Locked)',
        priceOverride: 'Restricted',
        barcodeScanner: 'Zebra TC21 (Returned to Tool Crib)',
        shiftCompliance: 92.3,
        unexcusedAbsences: 0,
        totalShifts: 620,
        auditSecurityState: 'Suspended',
        auditSyncStatus: 'Synced UC08',
        lastVerifiedDate: '20/12/2025',
        linkedAccount: {
            username: 'thang.vu',
            role: 'Inventory Staff',
            status: 'Inactive'
        },
        auditLogs: [
            {
                title: 'Initial Employee Record Created',
                timestamp: '01/04/2021 08:30:00',
                operator: 'ADM-01 (System Admin)'
            },
            {
                title: 'Temporary Leave of Absence Granted',
                timestamp: '05/01/2026 11:20:00',
                operator: 'HR Department'
            }
        ]
    },
    {
        employeeId: 7,
        employeeCode: 'EMP-0107',
        fullName: 'Hoàng Kim Oanh',
        initials: 'KO',
        position: 'Cashier',
        department: 'Frontline Retail',
        supermarketDept: 'Front End Operations & Checkout Services',
        phone: '0908 789 012',
        email: 'oanh.hoang@smsretail.vn',
        status: 'ACTIVE',
        gender: 'FEMALE',
        dateOfBirth: '1997-12-09',
        age: 28,
        citizenId: '079197009944',
        isCccdVerified: true,
        address: '34 Nguyễn Đình Chiểu, Phường 4, Quận 3, TP. Hồ Chí Minh',
        hireDate: '2022-10-10',
        seniority: '2y 7m',
        seniorityDetail: '2 năm 7 tháng làm việc liên tục',
        storeFacility: 'Store #04 - Dist 1 Flagship',
        supervisor: {
            name: 'Phạm Quốc Tuấn',
            code: 'EMP-0012',
            role: 'Store Operations Manager',
            initials: 'PT'
        },
        posClearanceLevel: 'Level 1 (Cashier)',
        posClearanceBadge: 'Authorized',
        posClearanceDesc: 'Lanes 01-04 active registry barcode scanner',
        assignedLanes: 'Lanes 03, 04 (Express)',
        priceOverride: 'Strictly Restricted (Manager Key Required)',
        barcodeScanner: 'Zebra DS2208 Handheld Synced',
        shiftCompliance: 99.6,
        unexcusedAbsences: 0,
        totalShifts: 510,
        auditSecurityState: 'Verified',
        auditSyncStatus: 'Synced UC08',
        lastVerifiedDate: '08/01/2026',
        linkedAccount: {
            username: 'oanh.hoang',
            role: 'POS Cashier',
            status: 'Active'
        },
        auditLogs: [
            {
                title: 'Initial Employee Record Created',
                timestamp: '10/10/2022 09:00:00',
                operator: 'ADM-01 (System Admin)'
            }
        ]
    },
    {
        employeeId: 8,
        employeeCode: 'EMP-0108',
        fullName: 'Đỗ Minh Khởi',
        initials: 'MK',
        position: 'Administrator',
        department: 'Systems & Database Admin',
        supermarketDept: 'IT Infrastructure & Enterprise Security',
        phone: '0977 890 123',
        email: 'khoi.do@smsretail.vn',
        status: 'ACTIVE',
        gender: 'MALE',
        dateOfBirth: '1991-04-25',
        age: 35,
        citizenId: '079191004455',
        isCccdVerified: true,
        address: '50 Pasteur, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
        hireDate: '2020-01-01',
        seniority: '5y 4m',
        seniorityDetail: '5 năm 4 tháng làm việc liên tục',
        storeFacility: 'Store #04 - Dist 1 Flagship',
        supervisor: {
            name: 'Nguyễn Văn An',
            code: 'EMP-0001',
            role: 'General Store Director',
            initials: 'NA'
        },
        posClearanceLevel: 'Level 4 (System Administrator)',
        posClearanceBadge: 'Root Authorized',
        posClearanceDesc: 'Full database, terminal network & credential governance',
        assignedLanes: 'Enterprise Gateway & Server Rack 04',
        priceOverride: 'Root Access (Logged in UC08 Audit Trail)',
        barcodeScanner: 'All Devices Admin Provisioning Allowed',
        shiftCompliance: 100.0,
        unexcusedAbsences: 0,
        totalShifts: 1350,
        auditSecurityState: 'Verified',
        auditSyncStatus: 'Synced UC08',
        lastVerifiedDate: '02/01/2026',
        linkedAccount: {
            username: 'khoi.do',
            role: 'System Administrator',
            status: 'Active'
        },
        auditLogs: [
            {
                title: 'Initial Employee Record Created',
                timestamp: '01/01/2020 08:00:00',
                operator: 'Root Admin'
            }
        ]
    }
];

// Helper to generate the remaining 28 employees to reach exactly 36 employees matching the mock stats
function generateRemainingEmployees() {
    const list = [...MOCK_EMPLOYEES];
    const positionsPlan = [
        // Cashiers: 13 more (so total 5 initial + 13 = 18 Cashiers)
        // 1 more inactive cashier (Resigned)
        { pos: 'Cashier', dept: 'Frontline Retail', sDept: 'Front End Operations & Checkout Services', count: 12, status: 'ACTIVE' },
        { pos: 'Cashier', dept: 'Frontline Retail', sDept: 'Front End Operations & Checkout Services', count: 1, status: 'INACTIVE', reason: 'Resigned (Đã nghỉ việc theo nguyện vọng)' },
        // Inventory Staff: 6 more (so total 2 initial + 6 = 8 Inventory Staff)
        { pos: 'Inventory Staff', dept: 'Warehouse & Receiving', sDept: 'Warehouse Logistics & Inbound Stock Inspection', count: 6, status: 'ACTIVE' },
        // Managers: 5 more (so total 1 initial + 5 = 6 Managers)
        // 1 more inactive manager (Resigned)
        { pos: 'Manager', dept: 'Shift Supervisor', sDept: 'Store Management & Operational Governance', count: 4, status: 'ACTIVE' },
        { pos: 'Manager', dept: 'Night Operations Supervisor', sDept: 'Store Management & Operational Governance', count: 1, status: 'INACTIVE', reason: 'Resigned (Chuyển công tác hệ thống)' },
        // Administrators: 3 more (so total 1 initial + 3 = 4 Administrators)
        { pos: 'Administrator', dept: 'Systems & Security', sDept: 'IT Infrastructure & Enterprise Security', count: 3, status: 'ACTIVE' }
    ];

    const sampleFirstNames = ['Văn', 'Thị', 'Đức', 'Thanh', 'Minh', 'Ngọc', 'Hữu', 'Phương', 'Quang', 'Anh'];
    const sampleLastNames = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Huỳnh', 'Phan', 'Vũ', 'Đặng', 'Bùi'];
    const sampleGivenNames = ['Hải', 'Thảo', 'Dũng', 'Trang', 'Tú', 'Linh', 'Bình', 'Hương', 'Nam', 'Yến'];

    let empNum = 109;
    let autoId = 9;

    for (const group of positionsPlan) {
        for (let i = 0; i < group.count; i++) {
            const lastName = sampleLastNames[(autoId * 3) % sampleLastNames.length];
            const midName = sampleFirstNames[(autoId * 5) % sampleFirstNames.length];
            const givenName = sampleGivenNames[(autoId * 7) % sampleGivenNames.length];
            const fullName = `${lastName} ${midName} ${givenName}`;
            const initials = `${lastName[0]}${givenName[0]}`;
            const code = `EMP-0${empNum}`;
            const isFemale = midName === 'Thị' || midName === 'Ngọc' || givenName === 'Thảo' || givenName === 'Trang';
            const gender = isFemale ? 'FEMALE' : 'MALE';
            const age = 22 + (autoId % 18);
            const birthYear = 2026 - age;
            const cleanUser = `${givenName.toLowerCase()}.${lastName.toLowerCase()}${autoId}`;

            list.push({
                employeeId: autoId,
                employeeCode: code,
                fullName,
                initials,
                position: group.pos,
                department: group.dept,
                supermarketDept: group.sDept,
                phone: `09${(autoId * 17) % 90 + 10} ${100 + autoId} ${200 + autoId}`,
                email: `${cleanUser}@smsretail.vn`,
                status: group.status,
                inactiveReason: group.reason || null,
                gender,
                dateOfBirth: `${birthYear}-0${(autoId % 9) + 1}-15`,
                age,
                citizenId: `079${birthYear % 100}00${1000 + autoId}`,
                isCccdVerified: true,
                address: `${10 + autoId * 3} Nguyễn Trãi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh`,
                hireDate: `202${(autoId % 4) + 1}-0${(autoId % 9) + 1}-01`,
                seniority: `${(autoId % 4) + 1}y ${(autoId % 11) + 1}m`,
                seniorityDetail: `${(autoId % 4) + 1} năm ${(autoId % 11) + 1} tháng làm việc`,
                storeFacility: 'Store #04 - Dist 1 Flagship',
                supervisor: {
                    name: 'Phạm Quốc Tuấn',
                    code: 'EMP-0012',
                    role: 'Store Operations Manager',
                    initials: 'PT'
                },
                posClearanceLevel: group.pos === 'Cashier' ? 'Level 1 (Cashier)' : group.pos === 'Inventory Staff' ? 'Level 2 (Inventory Clerk)' : group.pos === 'Manager' ? 'Level 3 (Manager)' : 'Level 4 (Admin)',
                posClearanceBadge: group.status === 'ACTIVE' ? 'Authorized' : 'Deactivated',
                posClearanceDesc: `${group.pos} system terminal clearance`,
                assignedLanes: group.pos === 'Cashier' ? `Lane 0${(autoId % 8) + 1}` : 'Central Operations Hub',
                priceOverride: group.pos === 'Administrator' || group.pos === 'Manager' ? 'Authorized' : 'Strictly Restricted',
                barcodeScanner: 'Zebra Scanner Synced',
                shiftCompliance: 97.0 + ((autoId * 7) % 30) / 10,
                unexcusedAbsences: group.status === 'INACTIVE' ? 2 : 0,
                totalShifts: 150 + autoId * 25,
                auditSecurityState: group.status === 'ACTIVE' ? 'Verified' : 'Suspended',
                auditSyncStatus: 'Synced UC08',
                lastVerifiedDate: '10/01/2026',
                linkedAccount: {
                    username: cleanUser,
                    role: group.pos,
                    status: group.status === 'ACTIVE' ? 'Active' : 'Inactive'
                },
                auditLogs: [
                    {
                        title: 'Initial Employee Record Created',
                        timestamp: `01/0${(autoId % 9) + 1}/2023 08:30:00`,
                        operator: 'ADM-01 (System Admin)'
                    }
                ]
            });

            empNum++;
            autoId++;
        }
    }
    return list;
}

export const ALL_MOCK_EMPLOYEES = generateRemainingEmployees();

/**
 * Enriches raw employee from backend API with UI metadata
 */
export function enrichEmployee(raw) {
    if (!raw) return null;
    const found = ALL_MOCK_EMPLOYEES.find(
        (m) => String(m.employeeId) === String(raw.employeeId) || m.employeeCode === raw.employeeCode
    );

    // Compute initials from full name
    const parts = (raw.fullName || '').trim().split(' ');
    const initials = parts.length >= 2
        ? `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase()
        : (raw.fullName || 'NV').slice(0, 2).toUpperCase();

    // Default seniority
    let seniority = '1y 6m';
    let seniorityDetail = '1 năm 6 tháng làm việc';
    if (raw.hireDate) {
        try {
            const hire = new Date(raw.hireDate);
            const now = new Date();
            const diffMonths = (now.getFullYear() - hire.getFullYear()) * 12 + (now.getMonth() - hire.getMonth());
            const yrs = Math.floor(diffMonths / 12);
            const mos = diffMonths % 12;
            seniority = `${yrs}y ${mos}m`;
            seniorityDetail = `${yrs} năm ${mos} tháng làm việc liên tục`;
        } catch (ignored) {}
    }

    return {
        ...found,
        employeeId: raw.employeeId,
        employeeCode: raw.employeeCode || found?.employeeCode || 'EMP-0101',
        fullName: raw.fullName || found?.fullName || 'Nhân viên siêu thị',
        initials: found?.initials || initials,
        position: raw.position || found?.position || 'Cashier',
        department: found?.department || (raw.position === 'Cashier' ? 'Frontline Retail' : raw.position === 'Inventory Staff' ? 'Warehouse & Receiving' : raw.position === 'Manager' ? 'Store Operations Supervisor' : 'Systems & IT'),
        supermarketDept: found?.supermarketDept || 'Front End Operations & Checkout Services',
        phone: raw.phone || found?.phone || '0903 123 456',
        email: raw.email || found?.email || 'employee@smsretail.vn',
        status: raw.status || found?.status || 'ACTIVE',
        gender: raw.gender || found?.gender || 'FEMALE',
        dateOfBirth: raw.dateOfBirth || found?.dateOfBirth || '1998-05-14',
        age: found?.age || 27,
        citizenId: found?.citizenId || '079198004521',
        isCccdVerified: found?.isCccdVerified ?? true,
        address: raw.address || found?.address || '124/8 Nguyễn Thị Minh Khai, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh',
        hireDate: raw.hireDate || found?.hireDate || '2023-03-15',
        seniority: found?.seniority || seniority,
        seniorityDetail: found?.seniorityDetail || seniorityDetail,
        storeFacility: found?.storeFacility || 'Store #04 - Dist 1 Flagship',
        supervisor: found?.supervisor || {
            name: 'Phạm Quốc Tuấn',
            code: 'EMP-0012',
            role: 'Store Operations Manager',
            initials: 'PT'
        },
        posClearanceLevel: found?.posClearanceLevel || (raw.position === 'Cashier' ? 'Level 1 (Cashier)' : raw.position === 'Inventory Staff' ? 'Level 2 (Inventory Clerk)' : raw.position === 'Manager' ? 'Level 3 (Manager)' : 'Level 4 (Admin)'),
        posClearanceBadge: found?.posClearanceBadge || (raw.status === 'ACTIVE' ? 'Authorized' : 'Deactivated'),
        posClearanceDesc: found?.posClearanceDesc || 'Lanes 01-04 active registry barcode scanner',
        assignedLanes: found?.assignedLanes || 'Lanes 01, 02, 03, 04 (Express & Standard)',
        priceOverride: found?.priceOverride || (raw.position === 'Manager' || raw.position === 'Administrator' ? 'Authorized' : 'Strictly Restricted (Manager Key Required)'),
        barcodeScanner: found?.barcodeScanner || 'Zebra DS2208 Handheld Synced',
        shiftCompliance: found?.shiftCompliance || 99.4,
        unexcusedAbsences: found?.unexcusedAbsences || 0,
        totalShifts: found?.totalShifts || 412,
        auditSecurityState: found?.auditSecurityState || 'Verified',
        auditSyncStatus: found?.auditSyncStatus || 'Synced UC08',
        lastVerifiedDate: found?.lastVerifiedDate || '10/01/2026',
        linkedAccount: found?.linkedAccount || {
            username: (raw.email || '').split('@')[0] || 'employee.user',
            role: raw.position || 'Staff',
            status: raw.status === 'ACTIVE' ? 'Active' : 'Inactive'
        },
        auditLogs: found?.auditLogs || [
            {
                title: 'Initial Employee Record Created',
                timestamp: '15/03/2023 08:30:14',
                operator: 'ADM-01 (System Admin)'
            },
            {
                title: 'Annual Clearance Review Completed',
                timestamp: '10/01/2026 14:15:02',
                operator: 'Phạm Quốc Tuấn (MGR-04)'
            }
        ]
    };
}

