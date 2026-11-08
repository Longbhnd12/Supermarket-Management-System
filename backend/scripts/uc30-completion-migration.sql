SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.sale','invoice_snapshot') IS NULL
 ALTER TABLE dbo.sale ADD invoice_snapshot nvarchar(max) NULL;
IF COL_LENGTH('dbo.sale','completion_requested') IS NULL
 ALTER TABLE dbo.sale ADD completion_requested bit NOT NULL CONSTRAINT DF_sale_completion_requested DEFAULT 0;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.sale') AND name='UX_sale_invoice_uc30')
 CREATE UNIQUE INDEX UX_sale_invoice_uc30 ON dbo.sale(invoice_number) WHERE invoice_number IS NOT NULL;
COMMIT;
