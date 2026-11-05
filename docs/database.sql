-- Supermarket Management System - SQL Server schema (19 tables)
-- Generated from the supplied DBML; safe for a NEW database.
-- Run in SSMS as a complete script. Existing database is not deleted.
IF DB_ID(N'Supermarket_Management_System_Compact') IS NULL
BEGIN
    CREATE DATABASE [Supermarket_Management_System_Compact];
END;
GO
USE [Supermarket_Management_System_Compact];
GO

-- role
CREATE TABLE [dbo].[role] (
    [role_id] BIGINT IDENTITY(1,1) NOT NULL,
    [role_name] VARCHAR(50) NOT NULL,
    [permissions] NVARCHAR(MAX) NULL,
    [description] NVARCHAR(255) NULL,
    [status] VARCHAR(20) NOT NULL CONSTRAINT [DF_role_status] DEFAULT ('ACTIVE'),
    CONSTRAINT [PK_role] PRIMARY KEY ([role_id])
);
GO

-- employee
CREATE TABLE [dbo].[employee] (
    [employee_id] BIGINT IDENTITY(1,1) NOT NULL,
    [employee_code] VARCHAR(30) NOT NULL,
    [full_name] NVARCHAR(150) NOT NULL,
    [date_of_birth] DATE NULL,
    [gender] VARCHAR(20) NULL,
    [phone] VARCHAR(20) NOT NULL,
    [email] VARCHAR(150) NOT NULL,
    [address] NVARCHAR(255) NULL,
    [position] NVARCHAR(100) NOT NULL,
    [hire_date] DATE NOT NULL,
    [status] VARCHAR(20) NOT NULL CONSTRAINT [DF_employee_status] DEFAULT ('ACTIVE'),
    [created_at] DATETIME2 NOT NULL,
    [updated_at] DATETIME2 NOT NULL,
    CONSTRAINT [PK_employee] PRIMARY KEY ([employee_id])
);
GO

-- user
CREATE TABLE [dbo].[user] (
    [user_id] BIGINT IDENTITY(1,1) NOT NULL,
    [role_id] BIGINT NOT NULL,
    [employee_id] BIGINT NULL,
    [supplier_id] BIGINT NULL,
    [username] VARCHAR(50) NOT NULL,
    [email] VARCHAR(150) NOT NULL,
    [password_hash] VARCHAR(255) NOT NULL,
    [status] VARCHAR(20) NOT NULL CONSTRAINT [DF_user_status] DEFAULT ('ACTIVE'),
    [failed_login_attempts] INT NOT NULL CONSTRAINT [DF_user_failed_login_attempts] DEFAULT (0),
    [locked_until] DATETIME2 NULL,
    [reset_code] VARCHAR(10) NULL,
    [reset_code_expires_at] DATETIME2 NULL,
    [last_login_at] DATETIME2 NULL,
    CONSTRAINT [PK_user] PRIMARY KEY ([user_id])
);
GO

-- system_setting
CREATE TABLE [dbo].[system_setting] (
    [setting_id] BIGINT IDENTITY(1,1) NOT NULL,
    [setting_key] VARCHAR(100) NOT NULL,
    [setting_value] NVARCHAR(500) NOT NULL,
    [data_type] VARCHAR(30) NOT NULL,
    [updated_by] BIGINT NULL,
    [updated_at] DATETIME2 NOT NULL,
    CONSTRAINT [PK_system_setting] PRIMARY KEY ([setting_id])
);
GO

-- system_log
CREATE TABLE [dbo].[system_log] (
    [log_id] BIGINT IDENTITY(1,1) NOT NULL,
    [user_id] BIGINT NULL,
    [action] VARCHAR(100) NOT NULL,
    [entity_type] VARCHAR(100) NULL,
    [entity_id] BIGINT NULL,
    [description] NVARCHAR(MAX) NULL,
    [ip_address] VARCHAR(45) NULL,
    [created_at] DATETIME2 NOT NULL,
    CONSTRAINT [PK_system_log] PRIMARY KEY ([log_id])
);
GO

-- notification
CREATE TABLE [dbo].[notification] (
    [notification_id] BIGINT IDENTITY(1,1) NOT NULL,
    [user_id] BIGINT NULL,
    [target_role] VARCHAR(50) NULL,
    [type] VARCHAR(50) NOT NULL,
    [title] NVARCHAR(200) NOT NULL,
    [message] NVARCHAR(MAX) NOT NULL,
    [is_read] BIT NOT NULL CONSTRAINT [DF_notification_is_read] DEFAULT (0),
    [created_at] DATETIME2 NOT NULL,
    CONSTRAINT [PK_notification] PRIMARY KEY ([notification_id])
);
GO

-- category
CREATE TABLE [dbo].[category] (
    [category_id] BIGINT IDENTITY(1,1) NOT NULL,
    [category_name] NVARCHAR(100) NOT NULL,
    [description] NVARCHAR(255) NULL,
    [status] VARCHAR(20) NOT NULL CONSTRAINT [DF_category_status] DEFAULT ('ACTIVE'),
    [created_at] DATETIME2 NOT NULL,
    CONSTRAINT [PK_category] PRIMARY KEY ([category_id])
);
GO

-- product
CREATE TABLE [dbo].[product] (
    [product_id] BIGINT IDENTITY(1,1) NOT NULL,
    [category_id] BIGINT NOT NULL,
    [product_code] VARCHAR(50) NOT NULL,
    [barcode] VARCHAR(100) NULL,
    [product_name] NVARCHAR(200) NOT NULL,
    [unit] NVARCHAR(30) NOT NULL,
    [selling_price] DECIMAL(15,2) NOT NULL,
    [stock_quantity] DECIMAL(12,3) NOT NULL CONSTRAINT [DF_product_stock_quantity] DEFAULT (0),
    [minimum_stock_level] DECIMAL(12,3) NOT NULL CONSTRAINT [DF_product_minimum_stock_level] DEFAULT (0),
    [track_expiry] BIT NOT NULL CONSTRAINT [DF_product_track_expiry] DEFAULT (0),
    [status] VARCHAR(20) NOT NULL CONSTRAINT [DF_product_status] DEFAULT ('ACTIVE'),
    CONSTRAINT [PK_product] PRIMARY KEY ([product_id])
);
GO

-- promotion
CREATE TABLE [dbo].[promotion] (
    [promotion_id] BIGINT IDENTITY(1,1) NOT NULL,
    [promotion_name] NVARCHAR(150) NOT NULL,
    [promotion_type] VARCHAR(30) NOT NULL,
    [discount_value] DECIMAL(15,2) NOT NULL,
    [category_id] BIGINT NULL,
    [product_id] BIGINT NULL,
    [start_at] DATETIME2 NOT NULL,
    [end_at] DATETIME2 NOT NULL,
    [status] VARCHAR(20) NOT NULL CONSTRAINT [DF_promotion_status] DEFAULT ('ACTIVE'),
    [created_by] BIGINT NOT NULL,
    CONSTRAINT [PK_promotion] PRIMARY KEY ([promotion_id])
);
GO

-- sale
CREATE TABLE [dbo].[sale] (
    [sale_id] BIGINT IDENTITY(1,1) NOT NULL,
    [sale_number] VARCHAR(50) NOT NULL,
    [invoice_number] VARCHAR(50) NULL,
    [cashier_id] BIGINT NOT NULL,
    [subtotal] DECIMAL(15,2) NOT NULL,
    [discount_amount] DECIMAL(15,2) NOT NULL CONSTRAINT [DF_sale_discount_amount] DEFAULT (0),
    [total_amount] DECIMAL(15,2) NOT NULL,
    [status] VARCHAR(20) NOT NULL CONSTRAINT [DF_sale_status] DEFAULT ('DRAFT'),
    [created_at] DATETIME2 NOT NULL,
    [completed_at] DATETIME2 NULL,
    CONSTRAINT [PK_sale] PRIMARY KEY ([sale_id])
);
GO

-- sale_item
CREATE TABLE [dbo].[sale_item] (
    [sale_item_id] BIGINT IDENTITY(1,1) NOT NULL,
    [sale_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [promotion_id] BIGINT NULL,
    [quantity] DECIMAL(12,3) NOT NULL,
    [unit_price] DECIMAL(15,2) NOT NULL,
    [discount_amount] DECIMAL(15,2) NOT NULL CONSTRAINT [DF_sale_item_discount_amount] DEFAULT (0),
    [subtotal] DECIMAL(15,2) NOT NULL,
    CONSTRAINT [PK_sale_item] PRIMARY KEY ([sale_item_id])
);
GO

-- payment
CREATE TABLE [dbo].[payment] (
    [payment_id] BIGINT IDENTITY(1,1) NOT NULL,
    [sale_id] BIGINT NOT NULL,
    [payment_method] VARCHAR(30) NOT NULL,
    [amount] DECIMAL(15,2) NOT NULL,
    [received_amount] DECIMAL(15,2) NULL,
    [change_amount] DECIMAL(15,2) NULL,
    [payment_status] VARCHAR(30) NOT NULL CONSTRAINT [DF_payment_payment_status] DEFAULT ('PENDING'),
    [gateway_transaction_id] VARCHAR(150) NULL,
    [failure_reason] NVARCHAR(255) NULL,
    [paid_at] DATETIME2 NULL,
    [txn_ref] NVARCHAR(200) NULL,
    [idempotency_key] NVARCHAR(200) NULL,
    [bank_code] NVARCHAR(20) NULL,
    [created_at] DATETIME2 NULL,
    [expires_at] DATETIME2 NULL,
    [reconciliation_required] BIT NOT NULL CONSTRAINT [DF_payment_reconciliation_required] DEFAULT ((0)),
    [payment_url] NVARCHAR(MAX) NULL,
    CONSTRAINT [PK_payment] PRIMARY KEY ([payment_id])
);
GO

-- supplier
CREATE TABLE [dbo].[supplier] (
    [supplier_id] BIGINT IDENTITY(1,1) NOT NULL,
    [supplier_code] VARCHAR(50) NOT NULL,
    [supplier_name] NVARCHAR(200) NOT NULL,
    [contact_person] NVARCHAR(100) NULL,
    [phone] VARCHAR(20) NOT NULL,
    [email] VARCHAR(150) NULL,
    [address] NVARCHAR(255) NULL,
    [tax_code] VARCHAR(20) NULL,
    [status] VARCHAR(20) NOT NULL CONSTRAINT [DF_supplier_status] DEFAULT ('ACTIVE'),
    [created_at] DATETIME2 NOT NULL,
    [updated_at] DATETIME2 NOT NULL,
    CONSTRAINT [PK_supplier] PRIMARY KEY ([supplier_id])
);
GO

-- purchase_order
CREATE TABLE [dbo].[purchase_order] (
    [order_id] BIGINT IDENTITY(1,1) NOT NULL,
    [po_number] VARCHAR(30) NOT NULL,
    [supplier_id] BIGINT NOT NULL,
    [requested_by] BIGINT NOT NULL,
    [approved_by] BIGINT NULL,
    [total_amount] DECIMAL(15,2) NOT NULL CONSTRAINT [DF_purchase_order_total_amount] DEFAULT (0),
    [expected_delivery_date] DATE NOT NULL,
    [proposed_delivery_date] DATE NULL,
    [status] VARCHAR(30) NOT NULL,
    [note] NVARCHAR(255) NULL,
    [response_reason] NVARCHAR(255) NULL,
    [created_at] DATETIME2 NOT NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [PK_purchase_order] PRIMARY KEY ([order_id])
);
GO

-- purchase_order_detail
CREATE TABLE [dbo].[purchase_order_detail] (
    [order_detail_id] BIGINT IDENTITY(1,1) NOT NULL,
    [order_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [requested_quantity] DECIMAL(12,3) NOT NULL,
    [ordered_quantity] DECIMAL(12,3) NULL,
    [proposed_quantity] DECIMAL(12,3) NULL,
    [received_quantity] DECIMAL(12,3) NOT NULL CONSTRAINT [DF_purchase_order_detail_received_quantity] DEFAULT (0),
    [unit_price] DECIMAL(15,2) NOT NULL,
    [proposed_unit_price] DECIMAL(15,2) NULL,
    CONSTRAINT [PK_purchase_order_detail] PRIMARY KEY ([order_detail_id])
);
GO

-- stock_receipt
CREATE TABLE [dbo].[stock_receipt] (
    [stock_receipt_id] BIGINT IDENTITY(1,1) NOT NULL,
    [receipt_number] VARCHAR(50) NOT NULL,
    [supplier_id] BIGINT NOT NULL,
    [purchase_order_id] BIGINT NULL,
    [created_by] BIGINT NOT NULL,
    [total_cost] DECIMAL(15,2) NOT NULL,
    [status] VARCHAR(20) NOT NULL CONSTRAINT [DF_stock_receipt_status] DEFAULT ('DRAFT'),
    [received_at] DATETIME2 NULL,
    [created_at] DATETIME2 NOT NULL,
    CONSTRAINT [PK_stock_receipt] PRIMARY KEY ([stock_receipt_id])
);
GO

-- stock_receipt_detail
CREATE TABLE [dbo].[stock_receipt_detail] (
    [stock_receipt_item_id] BIGINT IDENTITY(1,1) NOT NULL,
    [stock_receipt_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [quantity] DECIMAL(12,3) NOT NULL,
    [remaining_quantity] DECIMAL(12,3) NOT NULL,
    [unit_cost] DECIMAL(15,2) NOT NULL,
    [batch_number] VARCHAR(50) NULL,
    [expiration_date] DATE NULL,
    [subtotal] DECIMAL(15,2) NOT NULL,
    CONSTRAINT [PK_stock_receipt_detail] PRIMARY KEY ([stock_receipt_item_id])
);
GO

-- stock_check
CREATE TABLE [dbo].[stock_check] (
    [stock_check_id] BIGINT IDENTITY(1,1) NOT NULL,
    [check_number] VARCHAR(50) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [checked_by] BIGINT NOT NULL,
    [system_quantity] DECIMAL(12,3) NOT NULL,
    [actual_quantity] DECIMAL(12,3) NOT NULL,
    [difference] DECIMAL(12,3) NOT NULL,
    [reason] NVARCHAR(255) NULL,
    [status] VARCHAR(20) NOT NULL CONSTRAINT [DF_stock_check_status] DEFAULT ('DRAFT'),
    [checked_at] DATETIME2 NULL,
    CONSTRAINT [PK_stock_check] PRIMARY KEY ([stock_check_id])
);
GO

-- stock_movement
CREATE TABLE [dbo].[stock_movement] (
    [movement_id] BIGINT IDENTITY(1,1) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [stock_receipt_item_id] BIGINT NULL,
    [movement_type] VARCHAR(30) NOT NULL,
    [quantity_change] DECIMAL(12,3) NOT NULL,
    [reference_type] VARCHAR(30) NOT NULL,
    [reference_id] BIGINT NOT NULL,
    [reason] NVARCHAR(255) NULL,
    [created_by] BIGINT NOT NULL,
    [created_at] DATETIME2 NOT NULL,
    CONSTRAINT [PK_stock_movement] PRIMARY KEY ([movement_id])
);
GO

-- Unique constraints and filtered unique indexes for nullable columns
ALTER TABLE [dbo].[role] ADD CONSTRAINT [UQ_role_role_name] UNIQUE ([role_name]);
ALTER TABLE [dbo].[employee] ADD CONSTRAINT [UQ_employee_employee_code] UNIQUE ([employee_code]);
ALTER TABLE [dbo].[employee] ADD CONSTRAINT [UQ_employee_email] UNIQUE ([email]);
CREATE UNIQUE INDEX [UX_user_employee_id] ON [dbo].[user] ([employee_id]) WHERE [employee_id] IS NOT NULL;
CREATE UNIQUE INDEX [UX_user_supplier_id] ON [dbo].[user] ([supplier_id]) WHERE [supplier_id] IS NOT NULL;
ALTER TABLE [dbo].[user] ADD CONSTRAINT [UQ_user_username] UNIQUE ([username]);
ALTER TABLE [dbo].[user] ADD CONSTRAINT [UQ_user_email] UNIQUE ([email]);
ALTER TABLE [dbo].[system_setting] ADD CONSTRAINT [UQ_system_setting_setting_key] UNIQUE ([setting_key]);
ALTER TABLE [dbo].[category] ADD CONSTRAINT [UQ_category_category_name] UNIQUE ([category_name]);
ALTER TABLE [dbo].[product] ADD CONSTRAINT [UQ_product_product_code] UNIQUE ([product_code]);
CREATE UNIQUE INDEX [UX_product_barcode] ON [dbo].[product] ([barcode]) WHERE [barcode] IS NOT NULL;
ALTER TABLE [dbo].[sale] ADD CONSTRAINT [UQ_sale_sale_number] UNIQUE ([sale_number]);
CREATE UNIQUE INDEX [UX_sale_invoice_number] ON [dbo].[sale] ([invoice_number]) WHERE [invoice_number] IS NOT NULL;
ALTER TABLE [dbo].[supplier] ADD CONSTRAINT [UQ_supplier_supplier_code] UNIQUE ([supplier_code]);
CREATE UNIQUE INDEX [UX_supplier_tax_code] ON [dbo].[supplier] ([tax_code]) WHERE [tax_code] IS NOT NULL;
ALTER TABLE [dbo].[purchase_order] ADD CONSTRAINT [UQ_purchase_order_po_number] UNIQUE ([po_number]);
ALTER TABLE [dbo].[stock_receipt] ADD CONSTRAINT [UQ_stock_receipt_receipt_number] UNIQUE ([receipt_number]);
GO

-- Foreign keys
ALTER TABLE [dbo].[user] WITH CHECK ADD CONSTRAINT [FK_user_role_id_role] FOREIGN KEY ([role_id]) REFERENCES [dbo].[role] ([role_id]);
ALTER TABLE [dbo].[user] WITH CHECK ADD CONSTRAINT [FK_user_employee_id_employee] FOREIGN KEY ([employee_id]) REFERENCES [dbo].[employee] ([employee_id]);
ALTER TABLE [dbo].[user] WITH CHECK ADD CONSTRAINT [FK_user_supplier_id_supplier] FOREIGN KEY ([supplier_id]) REFERENCES [dbo].[supplier] ([supplier_id]);
ALTER TABLE [dbo].[system_setting] WITH CHECK ADD CONSTRAINT [FK_system_setting_updated_by_user] FOREIGN KEY ([updated_by]) REFERENCES [dbo].[user] ([user_id]);
ALTER TABLE [dbo].[system_log] WITH CHECK ADD CONSTRAINT [FK_system_log_user_id_user] FOREIGN KEY ([user_id]) REFERENCES [dbo].[user] ([user_id]);
ALTER TABLE [dbo].[notification] WITH CHECK ADD CONSTRAINT [FK_notification_user_id_user] FOREIGN KEY ([user_id]) REFERENCES [dbo].[user] ([user_id]);
ALTER TABLE [dbo].[product] WITH CHECK ADD CONSTRAINT [FK_product_category_id_category] FOREIGN KEY ([category_id]) REFERENCES [dbo].[category] ([category_id]);
ALTER TABLE [dbo].[promotion] WITH CHECK ADD CONSTRAINT [FK_promotion_category_id_category] FOREIGN KEY ([category_id]) REFERENCES [dbo].[category] ([category_id]);
ALTER TABLE [dbo].[promotion] WITH CHECK ADD CONSTRAINT [FK_promotion_product_id_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[product] ([product_id]);
ALTER TABLE [dbo].[promotion] WITH CHECK ADD CONSTRAINT [FK_promotion_created_by_user] FOREIGN KEY ([created_by]) REFERENCES [dbo].[user] ([user_id]);
ALTER TABLE [dbo].[sale] WITH CHECK ADD CONSTRAINT [FK_sale_cashier_id_user] FOREIGN KEY ([cashier_id]) REFERENCES [dbo].[user] ([user_id]);
ALTER TABLE [dbo].[sale_item] WITH CHECK ADD CONSTRAINT [FK_sale_item_sale_id_sale] FOREIGN KEY ([sale_id]) REFERENCES [dbo].[sale] ([sale_id]) ON DELETE CASCADE;
ALTER TABLE [dbo].[sale_item] WITH CHECK ADD CONSTRAINT [FK_sale_item_product_id_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[product] ([product_id]);
ALTER TABLE [dbo].[sale_item] WITH CHECK ADD CONSTRAINT [FK_sale_item_promotion_id_promotion] FOREIGN KEY ([promotion_id]) REFERENCES [dbo].[promotion] ([promotion_id]);
ALTER TABLE [dbo].[payment] WITH CHECK ADD CONSTRAINT [FK_payment_sale_id_sale] FOREIGN KEY ([sale_id]) REFERENCES [dbo].[sale] ([sale_id]);
ALTER TABLE [dbo].[purchase_order] WITH CHECK ADD CONSTRAINT [FK_purchase_order_supplier_id_supplier] FOREIGN KEY ([supplier_id]) REFERENCES [dbo].[supplier] ([supplier_id]);
ALTER TABLE [dbo].[purchase_order] WITH CHECK ADD CONSTRAINT [FK_purchase_order_requested_by_user] FOREIGN KEY ([requested_by]) REFERENCES [dbo].[user] ([user_id]);
ALTER TABLE [dbo].[purchase_order] WITH CHECK ADD CONSTRAINT [FK_purchase_order_approved_by_user] FOREIGN KEY ([approved_by]) REFERENCES [dbo].[user] ([user_id]);
ALTER TABLE [dbo].[purchase_order_detail] WITH CHECK ADD CONSTRAINT [FK_purchase_order_detail_order_id_purchase_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[purchase_order] ([order_id]) ON DELETE CASCADE;
ALTER TABLE [dbo].[purchase_order_detail] WITH CHECK ADD CONSTRAINT [FK_purchase_order_detail_product_id_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[product] ([product_id]);
ALTER TABLE [dbo].[stock_receipt] WITH CHECK ADD CONSTRAINT [FK_stock_receipt_supplier_id_supplier] FOREIGN KEY ([supplier_id]) REFERENCES [dbo].[supplier] ([supplier_id]);
ALTER TABLE [dbo].[stock_receipt] WITH CHECK ADD CONSTRAINT [FK_stock_receipt_purchase_order_id_purchase_order] FOREIGN KEY ([purchase_order_id]) REFERENCES [dbo].[purchase_order] ([order_id]);
ALTER TABLE [dbo].[stock_receipt] WITH CHECK ADD CONSTRAINT [FK_stock_receipt_created_by_user] FOREIGN KEY ([created_by]) REFERENCES [dbo].[user] ([user_id]);
ALTER TABLE [dbo].[stock_receipt_detail] WITH CHECK ADD CONSTRAINT [FK_stock_receipt_detail_stock_receipt_id_stock_receipt] FOREIGN KEY ([stock_receipt_id]) REFERENCES [dbo].[stock_receipt] ([stock_receipt_id]) ON DELETE CASCADE;
ALTER TABLE [dbo].[stock_receipt_detail] WITH CHECK ADD CONSTRAINT [FK_stock_receipt_detail_product_id_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[product] ([product_id]);
ALTER TABLE [dbo].[stock_check] WITH CHECK ADD CONSTRAINT [FK_stock_check_product_id_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[product] ([product_id]);
ALTER TABLE [dbo].[stock_check] WITH CHECK ADD CONSTRAINT [FK_stock_check_checked_by_user] FOREIGN KEY ([checked_by]) REFERENCES [dbo].[user] ([user_id]);
ALTER TABLE [dbo].[stock_movement] WITH CHECK ADD CONSTRAINT [FK_stock_movement_product_id_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[product] ([product_id]);
ALTER TABLE [dbo].[stock_movement] WITH CHECK ADD CONSTRAINT [FK_stock_movement_stock_receipt_item_id_stock_receipt_detail] FOREIGN KEY ([stock_receipt_item_id]) REFERENCES [dbo].[stock_receipt_detail] ([stock_receipt_item_id]);
ALTER TABLE [dbo].[stock_movement] WITH CHECK ADD CONSTRAINT [FK_stock_movement_created_by_user] FOREIGN KEY ([created_by]) REFERENCES [dbo].[user] ([user_id]);
GO

PRINT N'Supermarket schema created successfully (19 tables).';
