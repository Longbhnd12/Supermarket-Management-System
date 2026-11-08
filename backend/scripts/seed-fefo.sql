SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME() <> 'Supermarket_Management_System_Compact' THROW 50001, 'Local database only', 1;
BEGIN TRANSACTION;
DECLARE @now datetime2 = SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time';
DECLARE @today date = CAST(@now AS date);
DECLARE @actor bigint=(SELECT user_id FROM dbo.[user] WHERE username='seed.cashier');
DECLARE @supplier bigint=(SELECT supplier_id FROM dbo.supplier WHERE supplier_code='SEED-SUP-001');
DECLARE @category bigint=(SELECT category_id FROM dbo.category WHERE category_name=N'SEED - Thực phẩm');
IF @actor IS NULL OR @supplier IS NULL OR @category IS NULL THROW 50002, 'Run seed-local.ps1 first', 1;
INSERT dbo.product(category_id,product_code,barcode,product_name,unit,selling_price,stock_quantity,minimum_stock_level,track_expiry,status)
SELECT @category,v.code,v.barcode,v.name,v.unit,v.price,0,2,v.expiry,'ACTIVE'
FROM (VALUES
 ('SEED-FEFO-01','9900000001018',N'Sữa mẫu FEFO nhiều lô',N'Hộp',32000,1),
 ('SEED-FEFO-02','9900000001025',N'Táo mẫu FEFO cân kg',N'Kg',59000,1),
 ('SEED-FEFO-03','9900000001032',N'Gạo mẫu FEFO không hạn',N'Kg',22000,0)
) v(code,barcode,name,unit,price,expiry)
WHERE NOT EXISTS(SELECT 1 FROM dbo.product p WITH(UPDLOCK,HOLDLOCK) WHERE p.product_code=v.code);
DECLARE @lots TABLE(code varchar(50),batch varchar(50),qty decimal(12,3),cost decimal(15,2),days int,received_days int,status varchar(20));
-- Deliberately insert later expiry first to distinguish FEFO from identity/FIFO order.
INSERT @lots VALUES
 ('SEED-FEFO-01','SEED-FEFO-MILK-LATE',10,20000,60,-10,'CONFIRMED'),
 ('SEED-FEFO-01','SEED-FEFO-MILK-EARLY',3,20000,7,-2,'CONFIRMED'),
 ('SEED-FEFO-01','SEED-FEFO-MILK-TODAY',2,20000,0,-1,'CONFIRMED'),
 ('SEED-FEFO-01','SEED-FEFO-MILK-EXPIRED',4,20000,-1,-20,'CONFIRMED'),
 ('SEED-FEFO-01','SEED-FEFO-MILK-UNDATED',5,20000,NULL,-5,'CONFIRMED'),
 ('SEED-FEFO-01','SEED-FEFO-MILK-DRAFT',20,20000,1,-1,'DRAFT'),
 ('SEED-FEFO-02','SEED-FEFO-APPLE-LATE',5.750,35000,30,-10,'CONFIRMED'),
 ('SEED-FEFO-02','SEED-FEFO-APPLE-EARLY',1.250,35000,5,-2,'CONFIRMED'),
 ('SEED-FEFO-03','SEED-FEFO-RICE-UNDATED',10,15000,NULL,-10,'CONFIRMED'),
 ('SEED-FEFO-03','SEED-FEFO-RICE-DATED',2,15000,45,-1,'CONFIRMED');
INSERT dbo.stock_receipt(receipt_number,supplier_id,created_by,total_cost,status,received_at,created_at)
SELECT l.batch,@supplier,@actor,l.qty*l.cost,l.status,CASE WHEN l.status='CONFIRMED' THEN DATEADD(day,l.received_days,@now) END,DATEADD(day,l.received_days,@now)
FROM @lots l WHERE NOT EXISTS(SELECT 1 FROM dbo.stock_receipt r WITH(UPDLOCK,HOLDLOCK) WHERE r.receipt_number=l.batch);
DECLARE @added TABLE(product_id bigint,qty decimal(12,3),receipt_id bigint,batch_id bigint);
INSERT dbo.stock_receipt_detail(stock_receipt_id,product_id,quantity,remaining_quantity,unit_cost,batch_number,expiration_date,subtotal)
OUTPUT inserted.product_id,inserted.quantity,inserted.stock_receipt_id,inserted.stock_receipt_item_id INTO @added
SELECT r.stock_receipt_id,p.product_id,l.qty,l.qty,l.cost,l.batch,DATEADD(day,l.days,@today),l.qty*l.cost
FROM @lots l JOIN dbo.product p ON p.product_code=l.code JOIN dbo.stock_receipt r ON r.receipt_number=l.batch
WHERE NOT EXISTS(SELECT 1 FROM dbo.stock_receipt_detail d WITH(UPDLOCK,HOLDLOCK) WHERE d.batch_number=l.batch);
-- Only newly inserted CONFIRMED lots increase physical stock; reruns preserve consumption.
UPDATE p SET stock_quantity=p.stock_quantity+a.qty FROM dbo.product p JOIN
 (SELECT a.product_id,SUM(a.qty) qty FROM @added a JOIN dbo.stock_receipt r ON r.stock_receipt_id=a.receipt_id WHERE r.status='CONFIRMED' GROUP BY a.product_id) a ON a.product_id=p.product_id;
INSERT dbo.stock_movement(product_id,stock_receipt_item_id,movement_type,quantity_change,reference_type,reference_id,reason,created_by,created_at)
SELECT a.product_id,a.batch_id,'RECEIPT',a.qty,'STOCK_RECEIPT',a.receipt_id,N'Local FEFO seed receipt',@actor,r.received_at
FROM @added a JOIN dbo.stock_receipt r ON r.stock_receipt_id=a.receipt_id WHERE r.status='CONFIRMED';
COMMIT;
SELECT p.product_code,d.batch_number,r.status,d.quantity,d.remaining_quantity,d.expiration_date,p.stock_quantity
FROM dbo.stock_receipt_detail d JOIN dbo.stock_receipt r ON r.stock_receipt_id=d.stock_receipt_id
JOIN dbo.product p ON p.product_id=d.product_id WHERE p.product_code LIKE 'SEED-FEFO-%'
ORDER BY p.product_code,CASE WHEN d.expiration_date IS NULL THEN 1 ELSE 0 END,d.expiration_date,r.received_at,d.stock_receipt_item_id;
