SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME() <> 'Supermarket_Management_System_Compact'
    THROW 50001, 'Seed is restricted to the local development database.', 1;
BEGIN TRANSACTION;
DECLARE @now datetime2 = SYSDATETIME();
IF NOT EXISTS (SELECT 1 FROM dbo.role WHERE role_name='CASHIER')
 INSERT dbo.role(role_name,description,status) VALUES('CASHIER',N'Cashier','ACTIVE');
IF NOT EXISTS (SELECT 1 FROM dbo.employee WHERE employee_code='SEED-EMP-001')
 INSERT dbo.employee(employee_code,full_name,phone,email,position,hire_date,status,created_at,updated_at)
 VALUES('SEED-EMP-001',N'Thu ngân mẫu','0900000001','seed.cashier@example.test',N'Thu ngân',CAST(@now AS date),'ACTIVE',@now,@now);
IF NOT EXISTS (SELECT 1 FROM dbo.[user] WHERE username='seed.cashier')
 INSERT dbo.[user](role_id,employee_id,username,email,password_hash,status,failed_login_attempts)
 SELECT r.role_id,e.employee_id,'seed.cashier','seed.cashier@example.test','!disabled-local-seed-account!','ACTIVE',0
 FROM dbo.role r CROSS JOIN dbo.employee e WHERE r.role_name='CASHIER' AND e.employee_code='SEED-EMP-001';
INSERT dbo.category(category_name,description,status,created_at)
 SELECT v.name,N'Local FE seed','ACTIVE',@now FROM (VALUES
 (N'SEED - Đồ uống'),(N'SEED - Thực phẩm'),(N'SEED - Gia dụng')) v(name)
 WHERE NOT EXISTS(SELECT 1 FROM dbo.category c WITH (UPDLOCK,HOLDLOCK) WHERE c.category_name=v.name);
INSERT dbo.supplier(supplier_code,supplier_name,phone,email,status,created_at,updated_at)
 SELECT v.code,v.name,v.phone,v.email,'ACTIVE',@now,@now FROM (VALUES
 ('SEED-SUP-001',N'Nhà cung cấp thực phẩm mẫu','0900000011','seed.food@example.test'),
 ('SEED-SUP-002',N'Nhà cung cấp gia dụng mẫu','0900000012','seed.home@example.test')) v(code,name,phone,email)
 WHERE NOT EXISTS(SELECT 1 FROM dbo.supplier s WHERE s.supplier_code=v.code);
INSERT dbo.product(category_id,product_code,barcode,product_name,unit,selling_price,stock_quantity,minimum_stock_level,track_expiry,status)
 SELECT c.category_id,v.code,v.barcode,v.name,v.unit,v.price,v.stock,5,v.expiry,v.status
 FROM (VALUES
 ('SEED-P001','9900000000011',N'Sữa tươi mẫu 1L',N'Hộp',32000,100.000,1,'ACTIVE',N'SEED - Đồ uống'),
 ('SEED-P002','9900000000028',N'Nước suối mẫu 500ml',N'Chai',6000,200.000,0,'ACTIVE',N'SEED - Đồ uống'),
 ('SEED-P003','9900000000035',N'Cà phê mẫu 250g',N'Gói',65000,30.000,1,'ACTIVE',N'SEED - Đồ uống'),
 ('SEED-P004','9900000000042',N'Gạo thơm mẫu',N'Kg',22000,125.500,0,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P005','9900000000059',N'Mì ăn liền mẫu',N'Gói',4500,300.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P006','9900000000066',N'Trứng gà mẫu 10 quả',N'Hộp',35000,3.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P007','9900000000073',N'Bánh quy mẫu - hết hàng',N'Gói',18000,0.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P008','9900000000080',N'Dầu ăn mẫu 1L',N'Chai',48000,40.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P009','9900000000097',N'Nước rửa chén mẫu',N'Chai',28000,50.000,0,'ACTIVE',N'SEED - Gia dụng'),
 ('SEED-P010','9900000000103',N'Khăn giấy mẫu',N'Gói',12000,80.000,0,'ACTIVE',N'SEED - Gia dụng'),
 ('SEED-P011','9900000000110',N'Xà phòng mẫu - ngừng bán',N'Bánh',15000,10.000,0,'INACTIVE',N'SEED - Gia dụng'),
 ('SEED-P012','9900000000127',N'Đường trắng mẫu 1kg',N'Gói',24000,60.000,0,'ACTIVE',N'SEED - Thực phẩm')
,
 ('SEED-P013','9900000000134',N'Trà xanh mẫu 500ml',N'Chai',10000,120.000,1,'ACTIVE',N'SEED - Đồ uống'),
 ('SEED-P014','9900000000141',N'Nước cam mẫu 1L',N'Hộp',38000,45.000,1,'ACTIVE',N'SEED - Đồ uống'),
 ('SEED-P015','9900000000158',N'Sữa đậu nành mẫu',N'Hộp',8500,90.000,1,'ACTIVE',N'SEED - Đồ uống'),
 ('SEED-P016','9900000000165',N'Nước ngọt mẫu 330ml',N'Lon',11000,180.000,1,'ACTIVE',N'SEED - Đồ uống'),
 ('SEED-P017','9900000000172',N'Trà túi lọc mẫu',N'Hộp',42000,25.000,1,'ACTIVE',N'SEED - Đồ uống'),
 ('SEED-P018','9900000000189',N'Cacao mẫu 200g',N'Gói',55000,18.000,1,'ACTIVE',N'SEED - Đồ uống'),
 ('SEED-P019','9900000000196',N'Nước khoáng mẫu 1.5L',N'Chai',12000,2.000,1,'ACTIVE',N'SEED - Đồ uống'),
 ('SEED-P020','9900000000202',N'Nước dừa mẫu 330ml',N'Hộp',17000,0.000,1,'ACTIVE',N'SEED - Đồ uống'),
 ('SEED-P021','9900000000219',N'Muối iốt mẫu 500g',N'Gói',7000,70.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P022','9900000000226',N'Nước mắm mẫu 500ml',N'Chai',45000,55.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P023','9900000000233',N'Nước tương mẫu 250ml',N'Chai',19000,42.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P024','9900000000240',N'Bột mì mẫu 1kg',N'Gói',26000,35.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P025','9900000000257',N'Yến mạch mẫu 500g',N'Gói',68000,22.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P026','9900000000264',N'Bánh mì mẫu',N'Ổ',5000,40.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P027','9900000000271',N'Phô mai mẫu 8 miếng',N'Hộp',39000,16.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P028','9900000000288',N'Sữa chua mẫu 4 hộp',N'Lốc',28000,24.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P029','9900000000295',N'Táo đỏ mẫu',N'Kg',59000,18.750,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P030','9900000000301',N'Chuối mẫu',N'Kg',25000,32.500,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P031','9900000000318',N'Cà rốt mẫu',N'Kg',21000,14.250,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P032','9900000000325',N'Khoai tây mẫu',N'Kg',27000,0.500,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P033','9900000000332',N'Hạt điều mẫu 250g',N'Hộp',125000,12.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P034','9900000000349',N'Bánh gạo mẫu',N'Gói',23000,0.000,1,'ACTIVE',N'SEED - Thực phẩm'),
 ('SEED-P035','9900000000356',N'Bột giặt mẫu 3kg',N'Túi',115000,30.000,0,'ACTIVE',N'SEED - Gia dụng'),
 ('SEED-P036','9900000000363',N'Nước lau sàn mẫu 1L',N'Chai',36000,28.000,0,'ACTIVE',N'SEED - Gia dụng'),
 ('SEED-P037','9900000000370',N'Túi rác mẫu',N'Cuộn',18000,65.000,0,'ACTIVE',N'SEED - Gia dụng'),
 ('SEED-P038','9900000000387',N'Miếng rửa chén mẫu',N'Gói',9000,95.000,0,'ACTIVE',N'SEED - Gia dụng'),
 ('SEED-P039','9900000000394',N'Bàn chải đánh răng mẫu',N'Cái',22000,38.000,0,'ACTIVE',N'SEED - Gia dụng'),
 ('SEED-P040','9900000000400',N'Kem đánh răng mẫu 180g',N'Tuýp',45000,4.000,0,'ACTIVE',N'SEED - Gia dụng'),
 ('SEED-P041','9900000000417',N'Dầu gội mẫu 650ml',N'Chai',149000,20.000,0,'ACTIVE',N'SEED - Gia dụng'),
 ('SEED-P042','9900000000424',N'Hộp đựng thực phẩm mẫu 1L',N'Hộp',35000,15.000,0,'ACTIVE',N'SEED - Gia dụng')
 ) v(code,barcode,name,unit,price,stock,expiry,status,category)
 JOIN dbo.category c ON c.category_name=v.category
 WHERE NOT EXISTS(SELECT 1 FROM dbo.product p WHERE p.product_code=v.code);
COMMIT;
SELECT 'products' entity,COUNT(*) seed_count FROM dbo.product WHERE product_code LIKE 'SEED-%'
UNION ALL SELECT 'categories',COUNT(*) FROM dbo.category WHERE category_name LIKE N'SEED - %'
UNION ALL SELECT 'suppliers',COUNT(*) FROM dbo.supplier WHERE supplier_code LIKE 'SEED-%'
UNION ALL SELECT 'cashiers',COUNT(*) FROM dbo.[user] WHERE username='seed.cashier';

