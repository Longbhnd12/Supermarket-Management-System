/**
 * mockPromotionsData.js
 * Master mock dataset for UC14 (Create Promotion) and UC15 (Manage Promotions List)
 * Exactly mirrors the specifications from SRS 3.5.7 and the UC15 Mockup.
 */

export const MOCK_CATALOG_PRODUCTS = [
    {
        productId: 1,
        productCode: 'SKU-8930124',
        productName: 'Sữa tươi tiệt trùng Vinamilk 100% Không đường 1L',
        shortName: 'Sữa tươi Vinamilk 1L',
        barcode: '8934567890123',
        category: 'Dairy & Milk',
        categoryCode: 'DAIRY',
        sellingPrice: 38500,
        status: 'ACTIVE'
    },
    {
        productId: 2,
        productCode: 'SKU-8930512',
        productName: 'Mì Hảo Hảo Tôm Chua Cay 75g',
        shortName: 'Mì Hảo Hảo Tôm Chua Cay 75g',
        barcode: '8934567890567',
        category: 'Instant Foods',
        categoryCode: 'INSTANT_FOOD',
        sellingPrice: 4500,
        status: 'ACTIVE'
    },
    {
        productId: 3,
        productCode: 'SKU-8937021',
        productName: 'Bánh quy Cosy Marie Kinh Đô 336g',
        shortName: 'Bánh quy Cosy Marie Kinh Đô 336g',
        barcode: '8934567897011',
        category: 'Confectionery',
        categoryCode: 'CONFECTIONERY',
        sellingPrice: 46000,
        status: 'ACTIVE'
    },
    {
        productId: 4,
        productCode: 'SKU-8931102',
        productName: 'Nước ngọt Coca-Cola Sleek 320ml',
        shortName: 'Nước ngọt Coca-Cola Sleek 320ml',
        barcode: '8934567891102',
        category: 'Beverages',
        categoryCode: 'BEVERAGE',
        sellingPrice: 10000,
        status: 'ACTIVE'
    },
    {
        productId: 5,
        productCode: 'SKU-8933091',
        productName: 'Nước mắm Nam Ngư Đệ Nhị 900ml',
        shortName: 'Nước mắm Nam Ngư Đệ Nhị 900ml',
        barcode: '8936034567891',
        category: 'Seasonings & Sauces',
        categoryCode: 'SEASONING',
        sellingPrice: 42000,
        status: 'ACTIVE'
    },
    {
        productId: 6,
        productCode: 'SKU-8930146',
        productName: 'Trứng gà Ba Huân hộp 10 quả (Size L)',
        shortName: 'Trứng gà Ba Huân hộp 10 quả',
        barcode: '8934567890314',
        category: 'Dairy & Milk',
        categoryCode: 'DAIRY',
        sellingPrice: 34000,
        status: 'ACTIVE'
    },
    {
        productId: 7,
        productCode: 'SKU-8938201',
        productName: 'Nước ngọt Pepsi lon 320ml',
        shortName: 'Nước ngọt Pepsi lon 320ml',
        barcode: '8935049500025',
        category: 'Beverages',
        categoryCode: 'BEVERAGE',
        sellingPrice: 10000,
        status: 'ACTIVE'
    }
];

export const MOCK_CATEGORIES = [
    { categoryId: 1, categoryName: 'Dairy & Milk', code: 'DAIRY', viName: 'Sữa & Trứng', itemCount: 12 },
    { categoryId: 2, categoryName: 'Instant Foods', code: 'INSTANT_FOOD', viName: 'Mì ăn liền', itemCount: 8 },
    { categoryId: 3, categoryName: 'Confectionery', code: 'CONFECTIONERY', viName: 'Bánh kẹo', itemCount: 15 },
    { categoryId: 4, categoryName: 'Beverages', code: 'BEVERAGE', viName: 'Nước giải khát', itemCount: 20 },
    { categoryId: 5, categoryName: 'Seasonings & Sauces', code: 'SEASONING', viName: 'Gia vị & Nước sốt', itemCount: 10 }
];

export const MOCK_PROMOTIONS = [
    {
        promotionId: 1,
        code: 'PROMO-2025-01',
        promotionName: 'Khuyến mãi Giờ Vàng: Cuối tuần Mua sắm Rộn ràng',
        promotionType: 'PERCENTAGE',
        discountValue: 15,
        scopeSummary: 'Scope: 3 Products (Vinamilk, Hảo Hảo, Cosy)',
        scopeType: 'PRODUCTS',
        productCount: 3,
        startAt: '2025-06-01T08:00:00',
        endAt: '2025-06-07T22:00:00',
        validPeriodText: '01/06/2025 08:00 - 07/06/2025 22:00',
        timelineBadge: 'Active: 6d remaining',
        status: 'ACTIVE',
        redemptions: 142,
        isDeletable: false,
        createdById: 1,
        createdByUsername: 'admin'
    },
    {
        promotionId: 2,
        code: 'PROMO-2025-02',
        promotionName: 'Lễ hội Nông sản Việt: Gạo ST25 & Đậu đỏ Giảm giá',
        promotionType: 'PERCENTAGE',
        discountValue: 20,
        scopeSummary: 'Scope: Category: Gạo & Nông sản khô',
        scopeType: 'CATEGORY',
        categoryName: 'Gạo & Nông sản khô',
        startAt: '2025-05-15T00:00:00',
        endAt: '2025-05-31T23:59:59',
        validPeriodText: '15/05/2025 00:00 - 31/05/2025 23:59',
        timelineBadge: 'Active: 12d remaining',
        status: 'ACTIVE',
        redemptions: 85,
        isDeletable: false,
        createdById: 1,
        createdByUsername: 'admin'
    },
    {
        promotionId: 3,
        code: 'PROMO-2025-03',
        promotionName: 'Combo Giải Khát Mùa Hè - Nước ngọt & Nước khoáng',
        promotionType: 'FIXED_AMOUNT',
        discountValue: 5000,
        scopeSummary: 'Scope: 5 Specific Products',
        scopeType: 'PRODUCTS',
        productCount: 5,
        startAt: '2025-06-01T00:00:00',
        endAt: '2025-06-30T23:59:59',
        validPeriodText: '01/06/2025 00:00 - 30/06/2025 23:59',
        timelineBadge: 'Active: 29d remaining',
        status: 'ACTIVE',
        redemptions: 64,
        isDeletable: false,
        createdById: 1,
        createdByUsername: 'admin'
    },
    {
        promotionId: 4,
        code: 'PROMO-2025-04',
        promotionName: 'Tri Ân Khách Hàng: Giảm trực tiếp Đơn hàng Sữa...',
        promotionType: 'FIXED_AMOUNT',
        discountValue: 10000,
        scopeSummary: 'Scope: 2 Specific Products (TH True Milk, Dalatmilk)',
        scopeType: 'PRODUCTS',
        productCount: 2,
        startAt: '2025-06-10T08:00:00',
        endAt: '2025-06-15T22:00:00',
        validPeriodText: '10/06/2025 08:00 - 15/06/2025 22:00',
        timelineBadge: 'Upcoming (Starts in 3d)',
        status: 'ACTIVE',
        isDraft: true,
        redemptions: 0,
        isDeletable: true, // Unused draft: CAN BE DELETED per SRS 3.5.7!
        createdById: 1,
        createdByUsername: 'admin'
    },
    {
        promotionId: 5,
        code: 'PROMO-2025-05',
        promotionName: 'Flash Sale Trưa Vàng - Bánh kẹo & Snack',
        promotionType: 'PERCENTAGE',
        discountValue: 8,
        scopeSummary: 'Scope: Category: Bánh kẹo & Đồ ăn vặt',
        scopeType: 'CATEGORY',
        categoryName: 'Bánh kẹo & Đồ ăn vặt',
        startAt: '2025-06-01T11:00:00',
        endAt: '2025-06-15T14:00:00',
        validPeriodText: '01/06/2025 11:00 - 15/06/2025 14:00',
        timelineBadge: 'Active: 13d remaining',
        status: 'ACTIVE',
        redemptions: 18,
        isDeletable: false,
        createdById: 1,
        createdByUsername: 'admin'
    },
    {
        promotionId: 6,
        code: 'PROMO-2025-06',
        promotionName: 'Xả kho Tồn Quý 1 - Hóa mỹ phẩm & Tẩy rửa',
        promotionType: 'PERCENTAGE',
        discountValue: 12,
        scopeSummary: 'Scope: Category: Hóa mỹ phẩm',
        scopeType: 'CATEGORY',
        categoryName: 'Hóa mỹ phẩm',
        startAt: '2025-03-01T00:00:00',
        endAt: '2025-03-31T23:59:59',
        validPeriodText: '01/03/2025 00:00 - 31/03/2025 23:59',
        timelineBadge: 'Expired: Concluded',
        status: 'INACTIVE',
        redemptions: 320,
        isDeletable: false,
        createdById: 1,
        createdByUsername: 'admin'
    },
    {
        promotionId: 7,
        code: 'PROMO-2025-07',
        promotionName: 'Khuyến mãi Tết Dương Lịch 2025',
        promotionType: 'FIXED_AMOUNT',
        discountValue: 12000,
        scopeSummary: 'Scope: Storewide All Categories',
        scopeType: 'CATEGORY',
        categoryName: 'Storewide All Categories',
        startAt: '2025-01-01T00:00:00',
        endAt: '2025-01-05T23:59:59',
        validPeriodText: '01/01/2025 00:00 - 05/01/2025 23:59',
        timelineBadge: 'Expired: Concluded',
        status: 'INACTIVE',
        redemptions: 540,
        isDeletable: false,
        createdById: 1,
        createdByUsername: 'admin'
    }
];

export const MOCK_ACTIVE_PROMOTIONS = [
    {
        promotionId: 1,
        code: 'PROMO-2025-01',
        promotionName: 'Khuyến mãi Giờ Vàng: Cuối tuần Mua sắm Rộn ràng',
        shortTitle: 'Khuyến mãi Giờ Vàng: Cuối tuần...',
        promotionType: 'PERCENTAGE',
        discountValue: 15,
        discountBadge: '15% OFF',
        scopeType: 'PRODUCTS',
        scopeLabel: 'Specific Products (3 SKUs)',
        scopeSub: 'Direct Barcode Match',
        items: [
            { name: 'Sữa tươi Vinamilk 100% 1L', sku: 'SKU-8930124' },
            { name: 'Mì Hảo Hảo Tôm Chua C...', sku: 'SKU-8930512' },
            { name: 'Bánh Cosy Marie 336g', sku: 'SKU-8937021' }
        ],
        validUntil: '07/06/2025 • 22:00',
        remainingDays: '6 days remaining',
        badgeRight: '8 POS Lanes'
    },
    {
        promotionId: 2,
        code: 'PROMO-2025-02',
        promotionName: 'Lễ hội Nông sản Việt: Gạo ST25 & Đậu đỏ Giảm giá',
        shortTitle: 'Lễ hội Nông sản Việt: Gạo ST25 ...',
        promotionType: 'PERCENTAGE',
        discountValue: 20,
        discountBadge: '20% OFF',
        scopeType: 'CATEGORY',
        scopeLabel: 'Entire Department Category',
        scopeSub: 'Category Auto-Scan',
        categoryName: 'Gạo & Nông sản khô',
        categoryCode: 'CAT-005',
        categoryDesc: 'Applies systematically to all ST25 bags, Jasmine rice packages, yellow beans, green beans, and organic pulses in Aisle 03.',
        validUntil: '15/06/2025 • 22:00',
        remainingDays: '12 days remaining',
        badgeRight: 'POS Verified'
    },
    {
        promotionId: 3,
        code: 'PROMO-2025-03',
        promotionName: 'Combo Giải Khát Mùa Hè - Nước ngọt & Nước khoáng',
        shortTitle: 'Combo Giải Khát Mùa Hè - Nước ng...',
        promotionType: 'FIXED_AMOUNT',
        discountValue: 5000,
        discountBadge: '5,000 đ',
        scopeType: 'PRODUCTS',
        scopeLabel: 'Specific Products (5 SKUs)',
        scopeSub: 'Fixed Rebate',
        items: [
            { name: 'Nước ngọt Coca-Cola Sl...', sku: 'SKU-8930981' },
            { name: 'Nước khoáng Lavie 500ml', sku: 'SKU-8930441' },
            { name: 'Trà xanh C2 hương chan...', sku: 'SKU-8930810' }
        ],
        validUntil: '30/06/2025 • 22:00',
        remainingDays: '29 days remaining',
        badgeRight: 'Active Status'
    },
    {
        promotionId: 5,
        code: 'PROMO-2025-05',
        promotionName: 'Flash Sale Trưa Vàng - Bánh kẹo & Snack',
        shortTitle: 'Flash Sale Trưa Vàng - Bánh kẹo ...',
        promotionType: 'PERCENTAGE',
        discountValue: 8,
        discountBadge: '8% OFF',
        scopeType: 'CATEGORY',
        scopeLabel: 'Entire Department Category',
        scopeSub: 'Category Auto-Scan',
        categoryName: 'Bánh kẹo & Đồ ăn vặt',
        categoryCode: 'CAT-006',
        categoryDesc: 'Automatic 8% deduction applied during 11:00 - 14:00 daily scan windows on all confectioneries.',
        validUntil: '14/06/2025 • 14:00',
        remainingDays: '13 days remaining',
        badgeRight: '8 POS Lanes'
    },
    {
        promotionId: 4,
        code: 'PROMO-2025-04',
        promotionName: 'Tri Ân Khách Hàng: Giảm trực tiếp Đơn hàng Sữa...',
        shortTitle: 'Tri Ân Khách Hàng: Giảm trực...',
        promotionType: 'FIXED_AMOUNT',
        discountValue: 10000,
        discountBadge: '10,000 đ',
        scopeType: 'PRODUCTS',
        scopeLabel: 'Specific Products (2 SKUs)',
        scopeSub: 'Fixed Rebate',
        items: [
            { name: 'Sữa tươi tiệt trùng TH Tr...', sku: 'SKU-8930777' },
            { name: 'Sữa hạt thành trùng Dal...', sku: 'SKU-8930888' }
        ],
        validUntil: '10/06/2025 • 22:00',
        remainingDays: '9 days remaining',
        badgeRight: 'Active Status'
    }
];
