SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME()<>'Supermarket_Management_System_Compact' THROW 50001,'Local database only',1;
BEGIN TRANSACTION;
DECLARE @now datetime2=SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time';
DECLARE @actor bigint=(SELECT user_id FROM dbo.[user] WHERE username='seed.pos');
DECLARE @supplier bigint=(SELECT supplier_id FROM dbo.supplier WHERE supplier_code='SEED-SUP-POS');
IF @actor IS NULL OR @supplier IS NULL THROW 50002,'Run POS base seed first',1;
INSERT dbo.category(category_name,description,status,created_at)
SELECT v.name,N'UC27 isolated fixtures','ACTIVE',@now FROM (VALUES(N'SEED - UC27 danh mục giảm 10%'),(N'SEED - UC27 khuyến mãi sản phẩm')) v(name)
WHERE NOT EXISTS(SELECT 1 FROM dbo.category c WITH(UPDLOCK,HOLDLOCK) WHERE c.category_name=v.name);
DECLARE @category bigint=(SELECT category_id FROM dbo.category WHERE category_name=N'SEED - UC27 danh mục giảm 10%');
DECLARE @control bigint=(SELECT category_id FROM dbo.category WHERE category_name=N'SEED - UC27 khuyến mãi sản phẩm');
INSERT dbo.product(category_id,product_code,barcode,product_name,unit,selling_price,stock_quantity,minimum_stock_level,track_expiry,status)
SELECT v.cat,v.code,v.barcode,v.name,N'Hộp',v.price,0,5,1,'ACTIVE' FROM (VALUES
 (@category,'SEED-UC27-01','9900000003012',N'UC27 - Giảm danh mục 10%',100000.00),
 (@control,'SEED-UC27-02','9900000003029',N'UC27 - Giảm cố định 20000 mỗi dòng',100000.00),
 (@category,'SEED-UC27-03','9900000003036',N'UC27 - Chọn tốt nhất 10% hoặc 20000',100000.00),
 (@control,'SEED-UC27-04','9900000003043',N'UC27 - Giảm tối đa bằng tiền dòng',12000.00),
 (@control,'SEED-UC27-05','9900000003050',N'UC27 - Bỏ qua KM hết hạn chưa tới ngừng',50000.00),
 (@control,'SEED-UC27-06','9900000003067',N'UC27 - Làm tròn giảm 12.5%',12345.67),
 (@control,'SEED-UC27-07','9900000003074',N'UC27 - Hai KM bằng nhau',10000.00)
) v(cat,code,barcode,name,price)
WHERE NOT EXISTS(SELECT 1 FROM dbo.product p WITH(UPDLOCK,HOLDLOCK) WHERE p.product_code=v.code);
IF NOT EXISTS(SELECT 1 FROM dbo.stock_receipt WHERE receipt_number='SEED-UC27-RECEIPT')
 INSERT dbo.stock_receipt(receipt_number,supplier_id,created_by,total_cost,status,received_at,created_at)
 VALUES('SEED-UC27-RECEIPT',@supplier,@actor,0,'CONFIRMED',@now,@now);
DECLARE @receipt bigint=(SELECT stock_receipt_id FROM dbo.stock_receipt WHERE receipt_number='SEED-UC27-RECEIPT');
DECLARE @added TABLE(id bigint,product_id bigint,qty decimal(12,3));
INSERT dbo.stock_receipt_detail(stock_receipt_id,product_id,quantity,remaining_quantity,unit_cost,batch_number,expiration_date,subtotal)
OUTPUT inserted.stock_receipt_item_id,inserted.product_id,inserted.quantity INTO @added
SELECT @receipt,p.product_id,100,100,ROUND(p.selling_price*0.6,2),p.product_code+'-LOT',DATEADD(day,180,CAST(@now AS date)),ROUND(p.selling_price*0.6,2)*100
FROM dbo.product p WHERE p.product_code LIKE 'SEED-UC27-%' AND NOT EXISTS(SELECT 1 FROM dbo.stock_receipt_detail d WITH(UPDLOCK,HOLDLOCK) WHERE d.batch_number=p.product_code+'-LOT');
UPDATE p SET stock_quantity=p.stock_quantity+a.qty FROM dbo.product p JOIN @added a ON a.product_id=p.product_id;
INSERT dbo.stock_movement(product_id,stock_receipt_item_id,movement_type,quantity_change,reference_type,reference_id,reason,created_by,created_at)
SELECT product_id,id,'RECEIPT',qty,'STOCK_RECEIPT',@receipt,N'UC27 seed receipt',@actor,@now FROM @added;
UPDATE dbo.stock_receipt SET total_cost=(SELECT SUM(subtotal) FROM dbo.stock_receipt_detail WHERE stock_receipt_id=@receipt) WHERE stock_receipt_id=@receipt;
DECLARE @promos TABLE(name nvarchar(150),kind varchar(30),value decimal(15,2),code varchar(50),cat bigint,start_day int,end_day int,status varchar(20));
INSERT @promos VALUES
 (N'SEED-UC27-CATEGORY-10','PERCENTAGE',10,NULL,@category,-1,90,'ACTIVE'),
 (N'SEED-UC27-FIXED-20K','FIXED_AMOUNT',20000,'SEED-UC27-02',NULL,-1,90,'ACTIVE'),
 (N'SEED-UC27-BEST-20K','FIXED_AMOUNT',20000,'SEED-UC27-03',NULL,-1,90,'ACTIVE'),
 (N'SEED-UC27-CAP-20K','FIXED_AMOUNT',20000,'SEED-UC27-04',NULL,-1,90,'ACTIVE'),
 (N'SEED-UC27-EXPIRED','PERCENTAGE',50,'SEED-UC27-05',NULL,-10,-1,'ACTIVE'),
 (N'SEED-UC27-FUTURE','PERCENTAGE',50,'SEED-UC27-05',NULL,30,90,'ACTIVE'),
 (N'SEED-UC27-INACTIVE','PERCENTAGE',50,'SEED-UC27-05',NULL,-1,90,'INACTIVE'),
 (N'SEED-UC27-ROUND-12.5','PERCENTAGE',12.5,'SEED-UC27-06',NULL,-1,90,'ACTIVE'),
 (N'SEED-UC27-TIE-FIXED','FIXED_AMOUNT',1000,'SEED-UC27-07',NULL,-1,90,'ACTIVE'),
 (N'SEED-UC27-TIE-PERCENT','PERCENTAGE',10,'SEED-UC27-07',NULL,-1,90,'ACTIVE');
INSERT dbo.promotion(promotion_name,promotion_type,discount_value,product_id,category_id,start_at,end_at,status,created_by)
SELECT v.name,v.kind,v.value,p.product_id,v.cat,DATEADD(day,v.start_day,@now),DATEADD(day,v.end_day,@now),v.status,@actor
FROM @promos v LEFT JOIN dbo.product p ON p.product_code=v.code
WHERE NOT EXISTS(SELECT 1 FROM dbo.promotion x WITH(UPDLOCK,HOLDLOCK) WHERE x.promotion_name=v.name);
COMMIT;
SELECT p.product_code,p.selling_price,p.stock_quantity FROM dbo.product p WHERE product_code LIKE 'SEED-UC27-%' ORDER BY product_code;
