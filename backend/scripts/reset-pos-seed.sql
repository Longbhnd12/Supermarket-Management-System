SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME() <> 'Supermarket_Management_System_Compact' THROW 50001,'Local database only',1;
BEGIN TRANSACTION;
SELECT product_id INTO #p FROM dbo.product WHERE product_code LIKE 'SEED-%';
SELECT user_id INTO #u FROM dbo.[user] WHERE username LIKE 'seed.%';
SELECT sale_id INTO #s FROM dbo.sale WHERE cashier_id IN(SELECT user_id FROM #u)
 OR sale_id IN(SELECT sale_id FROM dbo.sale_item WHERE product_id IN(SELECT product_id FROM #p));
-- Abort on mixed receipts/orders rather than delete unrelated business data.
IF EXISTS(SELECT 1 FROM dbo.stock_receipt_detail d JOIN dbo.stock_receipt r ON r.stock_receipt_id=d.stock_receipt_id WHERE d.product_id IN(SELECT product_id FROM #p) AND r.receipt_number NOT LIKE 'SEED-%')
 THROW 50002,'Non-seed receipt references seed product; review required',1;
IF EXISTS(SELECT 1 FROM dbo.purchase_order_detail WHERE product_id IN(SELECT product_id FROM #p))
 THROW 50003,'Purchase order references seed product; review required',1;
IF EXISTS(SELECT 1 FROM dbo.sale_item WHERE sale_id IN(SELECT sale_id FROM #s) AND product_id NOT IN(SELECT product_id FROM #p))
 THROW 50004,'Mixed sale contains non-seed products; review required',1;
DELETE dbo.stock_movement WHERE product_id IN(SELECT product_id FROM #p) OR (reference_type='SALE_ITEM' AND reference_id IN(SELECT sale_item_id FROM dbo.sale_item WHERE sale_id IN(SELECT sale_id FROM #s)));
DELETE dbo.payment WHERE sale_id IN(SELECT sale_id FROM #s);
DELETE dbo.sale_item WHERE sale_id IN(SELECT sale_id FROM #s);
DELETE dbo.system_log WHERE user_id IN(SELECT user_id FROM #u) OR (entity_type='SALE' AND entity_id IN(SELECT sale_id FROM #s));
DELETE dbo.sale WHERE sale_id IN(SELECT sale_id FROM #s);
DELETE dbo.stock_check WHERE product_id IN(SELECT product_id FROM #p);
DELETE dbo.stock_receipt_detail WHERE product_id IN(SELECT product_id FROM #p);
DELETE dbo.stock_receipt WHERE receipt_number LIKE 'SEED-%';
DELETE dbo.promotion WHERE product_id IN(SELECT product_id FROM #p) OR promotion_name LIKE N'SEED-UC27-%';
DELETE dbo.product WHERE product_id IN(SELECT product_id FROM #p);
DELETE dbo.notification WHERE user_id IN(SELECT user_id FROM #u);
UPDATE dbo.system_setting SET updated_by=NULL WHERE updated_by IN(SELECT user_id FROM #u);
DELETE dbo.[user] WHERE user_id IN(SELECT user_id FROM #u);
DELETE dbo.employee WHERE employee_code LIKE 'SEED-%';
DELETE dbo.supplier WHERE supplier_code LIKE 'SEED-%';
DELETE dbo.category WHERE category_name LIKE N'SEED - %';
-- Role is shared reference data; retain it.
DECLARE @now datetime2=SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time';
DECLARE @today date=CAST(@now AS date);
IF NOT EXISTS(SELECT 1 FROM dbo.role WHERE role_name='CASHIER') INSERT dbo.role(role_name,status) VALUES('CASHIER','ACTIVE');
INSERT dbo.category(category_name,description,status,created_at) VALUES(N'SEED - POS mới',N'UC24/25/26/28/29/30','ACTIVE',@now);
DECLARE @cat bigint=SCOPE_IDENTITY();
INSERT dbo.employee(employee_code,full_name,phone,email,position,hire_date,status,created_at,updated_at)
 VALUES('SEED-EMP-POS',N'Nguyễn An - Thu ngân test','0900000099','seed.pos@example.test',N'Thu ngân',@today,'ACTIVE',@now,@now);
DECLARE @emp bigint=SCOPE_IDENTITY();
INSERT dbo.[user](role_id,employee_id,username,email,password_hash,status,failed_login_attempts)
 SELECT role_id,@emp,'seed.pos','seed.pos@example.test','!disabled-local-seed-account!','ACTIVE',0 FROM dbo.role WHERE role_name='CASHIER';
DECLARE @actor bigint=SCOPE_IDENTITY();
INSERT dbo.supplier(supplier_code,supplier_name,phone,email,status,created_at,updated_at)
 VALUES('SEED-SUP-POS',N'Nhà cung cấp test POS','0900000098','seed.supplier@example.test','ACTIVE',@now,@now);
DECLARE @sup bigint=SCOPE_IDENTITY();
DECLARE @products TABLE(code varchar(50),barcode varchar(100),name nvarchar(200),unit nvarchar(30),price decimal(15,2),expiry bit,status varchar(20));
INSERT @products VALUES
 ('SEED-POS-01','9900000002015',N'Sữa tươi FEFO',N'Hộp',32000,1,'ACTIVE'),
 ('SEED-POS-02','9900000002022',N'Nước suối',N'Chai',6000,0,'ACTIVE'),
 ('SEED-POS-03','9900000002039',N'Táo đỏ cân kg',N'Kg',59000,1,'ACTIVE'),
 ('SEED-POS-04','9900000002046',N'Gạo thơm cân kg',N'Kg',22000,0,'ACTIVE'),
 ('SEED-POS-05','9900000002053',N'Bánh quy',N'Gói',18000,1,'ACTIVE'),
 ('SEED-POS-06','9900000002060',N'Cà phê',N'Gói',65000,1,'ACTIVE'),
 ('SEED-POS-07','9900000002077',N'Khăn giấy',N'Gói',12000,0,'ACTIVE'),
 ('SEED-POS-08','9900000002084',N'Dầu ăn - tồn thấp',N'Chai',48000,1,'ACTIVE'),
 ('SEED-POS-09','9900000002091',N'Mì gói - hết hàng',N'Gói',4500,1,'ACTIVE'),
 ('SEED-POS-10','9900000002107',N'Sữa chua - chỉ còn lô hết hạn',N'Hộp',8000,1,'ACTIVE'),
 ('SEED-POS-11','9900000002114',N'Trà - lô chưa xác nhận',N'Hộp',42000,1,'ACTIVE'),
 ('SEED-POS-12','9900000002121',N'Xà phòng - ngừng bán',N'Bánh',15000,0,'INACTIVE'),
 ('SEED-POS-13','9900000002138',N'Sữa lỗi FEFO - hết hạn hoặc thiếu hạn',N'Hộp',32000,1,'ACTIVE'),
 ('SEED-POS-14','9900000002145',N'Bánh hạn hôm nay',N'Gói',10000,1,'ACTIVE'),
 ('SEED-POS-15','9900000002152',N'Trà FEFO cùng hạn - ưu tiên ngày nhập',N'Hộp',25000,1,'ACTIVE');
INSERT dbo.product(category_id,product_code,barcode,product_name,unit,selling_price,stock_quantity,minimum_stock_level,track_expiry,status)
 SELECT @cat,code,barcode,name,unit,price,0,5,expiry,status FROM @products;
DECLARE @lots TABLE(code varchar(50),suffix varchar(20),qty decimal(12,3),days int,status varchar(20),age int);
INSERT @lots VALUES
 ('SEED-POS-01','LATE',497,120,'CONFIRMED',10),('SEED-POS-01','EARLY',3,30,'CONFIRMED',2),

 ('SEED-POS-02','MAIN',1000,NULL,'CONFIRMED',5),
 ('SEED-POS-03','LATE',100.750,60,'CONFIRMED',10),('SEED-POS-03','EARLY',1.250,20,'CONFIRMED',2),
 ('SEED-POS-04','UNDATED',500,NULL,'CONFIRMED',10),('SEED-POS-04','DATED',2,90,'CONFIRMED',2),
 ('SEED-POS-05','MAIN',60,120,'CONFIRMED',5),('SEED-POS-06','MAIN',30,180,'CONFIRMED',5),
 ('SEED-POS-07','MAIN',80,NULL,'CONFIRMED',5),('SEED-POS-08','MAIN',2,90,'CONFIRMED',5),
 ('SEED-POS-10','EXPIRED',10,-1,'CONFIRMED',5),('SEED-POS-11','DRAFT',20,60,'DRAFT',2),
 ('SEED-POS-12','MAIN',10,NULL,'CONFIRMED',5),
 ('SEED-POS-13','EXPIRED',4,-1,'CONFIRMED',15),('SEED-POS-13','UNDATED',5,NULL,'CONFIRMED',5),
 ('SEED-POS-14','TODAY',10,0,'CONFIRMED',1),
 ('SEED-POS-15','NEWER',7,60,'CONFIRMED',2),('SEED-POS-15','OLDER',3,60,'CONFIRMED',10);
INSERT dbo.stock_receipt(receipt_number,supplier_id,created_by,total_cost,status,received_at,created_at)
SELECT l.code+'-'+l.suffix,@sup,@actor,l.qty*p.price*0.6,l.status,CASE WHEN l.status='CONFIRMED' THEN DATEADD(day,-l.age,@now) END,DATEADD(day,-l.age,@now)
FROM @lots l JOIN @products p ON p.code=l.code;
INSERT dbo.stock_receipt_detail(stock_receipt_id,product_id,quantity,remaining_quantity,unit_cost,batch_number,expiration_date,subtotal)
SELECT r.stock_receipt_id,p.product_id,l.qty,l.qty,p.selling_price*0.6,r.receipt_number,DATEADD(day,l.days,@today),l.qty*p.selling_price*0.6
FROM @lots l JOIN dbo.product p ON p.product_code=l.code JOIN dbo.stock_receipt r ON r.receipt_number=l.code+'-'+l.suffix;
UPDATE p SET stock_quantity=x.qty FROM dbo.product p JOIN
 (SELECT d.product_id,SUM(d.remaining_quantity) qty FROM dbo.stock_receipt_detail d JOIN dbo.stock_receipt r ON r.stock_receipt_id=d.stock_receipt_id WHERE r.receipt_number LIKE 'SEED-POS-%' AND r.status='CONFIRMED' GROUP BY d.product_id) x ON x.product_id=p.product_id;
INSERT dbo.stock_movement(product_id,stock_receipt_item_id,movement_type,quantity_change,reference_type,reference_id,reason,created_by,created_at)
SELECT d.product_id,d.stock_receipt_item_id,'RECEIPT',d.quantity,'STOCK_RECEIPT',r.stock_receipt_id,N'Seed POS mới',@actor,r.received_at FROM dbo.stock_receipt_detail d JOIN dbo.stock_receipt r ON r.stock_receipt_id=d.stock_receipt_id WHERE r.receipt_number LIKE 'SEED-POS-%' AND r.status='CONFIRMED';
COMMIT;
SELECT product_code,barcode,stock_quantity FROM dbo.product WHERE product_code LIKE 'SEED-POS-%' ORDER BY product_code;
SELECT COUNT(*) removed_old_sales FROM #s;


