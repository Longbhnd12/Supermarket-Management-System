-- Local development login: seed.pos / SeedPos123
-- Run in SSMS. Re-running resets this account's password and lockout.
USE [Supermarket_Management_System_Compact];
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

DECLARE @hash VARCHAR(255) = '$2a$10$YR/UdZ2YIF70kHHSPVwPxOq67w/pjOVPdaDcSP0QvHygmQvjpkBN.';
DECLARE @roleId BIGINT;
SELECT TOP (1) @roleId = role_id FROM dbo.[role] WITH (UPDLOCK, HOLDLOCK)
WHERE role_name = 'CASHIER' AND status = 'ACTIVE' ORDER BY role_id;
IF @roleId IS NULL
BEGIN
    INSERT INTO dbo.[role] (role_name, description, status)
    VALUES ('CASHIER', N'Cashier', 'ACTIVE');
    SET @roleId = SCOPE_IDENTITY();
END;

IF EXISTS (SELECT 1 FROM dbo.[user] WITH (UPDLOCK, HOLDLOCK) WHERE username = 'seed.pos')
BEGIN
    UPDATE dbo.[user]
    SET role_id = @roleId, password_hash = @hash, status = 'ACTIVE', failed_login_attempts = 0,
        locked_until = NULL, reset_code = NULL, reset_code_expires_at = NULL
    WHERE username = 'seed.pos';
END
ELSE
BEGIN
    INSERT INTO dbo.[user] (role_id, username, email, password_hash, status, failed_login_attempts)
    VALUES (@roleId, 'seed.pos', 'seed.pos@example.test', @hash, 'ACTIVE', 0);
END;

COMMIT TRANSACTION;
SELECT user_id, username, email, role_id, status FROM dbo.[user] WHERE username = 'seed.pos';
GO
