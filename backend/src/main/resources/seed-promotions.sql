-- Seed Promotions for UC14 & POS Testing
-- Ensure User admin exists, Product SKU-8930124 and SKU-8930512 exist.

DECLARE @adminId BIGINT = (SELECT TOP 1 user_id FROM dbo.[user] WHERE username = 'admin');
DECLARE @productId1 BIGINT = (SELECT TOP 1 product_id FROM dbo.product WHERE product_code = 'SKU-8930124');
DECLARE @productId2 BIGINT = (SELECT TOP 1 product_id FROM dbo.product WHERE product_code = 'SKU-8930512');
DECLARE @categoryId BIGINT = (SELECT TOP 1 category_id FROM dbo.category WHERE category_name = N'Sữa & Trứng');

IF @adminId IS NOT NULL AND @productId1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.promotion WHERE promotion_name = N'Khuyến mãi Giờ Vàng: Cuối tuần Mua sắm Rộn ràng')
BEGIN
    INSERT INTO dbo.promotion (promotion_name, promotion_type, discount_value, category_id, product_id, start_at, end_at, status, created_by)
    VALUES (N'Khuyến mãi Giờ Vàng: Cuối tuần Mua sắm Rộn ràng', 'PERCENTAGE', 15.00, NULL, @productId1, DATEADD(day, -1, GETDATE()), DATEADD(day, 7, GETDATE()), 'ACTIVE', @adminId);
END;

IF @adminId IS NOT NULL AND @productId2 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.promotion WHERE promotion_name = N'Giảm giá Đặc biệt Mì Hảo Hảo Tôm Chua Cay')
BEGIN
    INSERT INTO dbo.promotion (promotion_name, promotion_type, discount_value, category_id, product_id, start_at, end_at, status, created_by)
    VALUES (N'Giảm giá Đặc biệt Mì Hảo Hảo Tôm Chua Cay', 'FIXED_AMOUNT', 500.00, NULL, @productId2, DATEADD(day, -2, GETDATE()), DATEADD(day, 5, GETDATE()), 'ACTIVE', @adminId);
END;

IF @adminId IS NOT NULL AND @categoryId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.promotion WHERE promotion_name = N'Ưu đãi Danh mục Sữa & Trứng')
BEGIN
    INSERT INTO dbo.promotion (promotion_name, promotion_type, discount_value, category_id, product_id, start_at, end_at, status, created_by)
    VALUES (N'Ưu đãi Danh mục Sữa & Trứng', 'PERCENTAGE', 10.00, @categoryId, NULL, DATEADD(day, -3, GETDATE()), DATEADD(day, 10, GETDATE()), 'ACTIVE', @adminId);
END;

