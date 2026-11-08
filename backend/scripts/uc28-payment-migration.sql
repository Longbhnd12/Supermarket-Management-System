-- Additive and rerunnable; preserves existing payment history.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.payment', 'txn_ref') IS NULL
    ALTER TABLE dbo.payment ADD txn_ref varchar(100) NULL;
IF COL_LENGTH('dbo.payment', 'idempotency_key') IS NULL
    ALTER TABLE dbo.payment ADD idempotency_key varchar(100) NULL;
IF COL_LENGTH('dbo.payment', 'bank_code') IS NULL
    ALTER TABLE dbo.payment ADD bank_code varchar(20) NULL;
IF COL_LENGTH('dbo.payment', 'created_at') IS NULL
    ALTER TABLE dbo.payment ADD created_at datetime2 NULL;
IF COL_LENGTH('dbo.payment', 'expires_at') IS NULL
    ALTER TABLE dbo.payment ADD expires_at datetime2 NULL;
IF COL_LENGTH('dbo.payment', 'reconciliation_required') IS NULL
    ALTER TABLE dbo.payment ADD reconciliation_required bit NOT NULL CONSTRAINT DF_payment_reconciliation DEFAULT 0;
IF COL_LENGTH('dbo.payment', 'payment_url') IS NULL
    ALTER TABLE dbo.payment ADD payment_url nvarchar(max) NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.payment') AND name = 'UX_payment_txn_ref')
    EXEC('CREATE UNIQUE INDEX UX_payment_txn_ref ON dbo.payment(txn_ref) WHERE txn_ref IS NOT NULL');
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.payment') AND name = 'UX_payment_idempotency')
    EXEC('CREATE UNIQUE INDEX UX_payment_idempotency ON dbo.payment(sale_id, idempotency_key) WHERE idempotency_key IS NOT NULL');
COMMIT TRANSACTION;
