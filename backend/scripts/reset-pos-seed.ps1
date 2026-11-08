# Explicit reset of SEED-* fixtures and their test sales; keeps shared roles and other data.
$ErrorActionPreference = 'Stop'
$properties = @{}
Get-Content (Join-Path $PSScriptRoot '../src/main/resources/application.properties') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $properties[$matches[1].Trim()] = $matches[2].Trim() }
}
$url = $env:SPRING_DATASOURCE_URL
if (-not $url) { $url = $properties['spring.datasource.url'] }
if ($url -notmatch '^jdbc:sqlserver://localhost:1433;databaseName=Supermarket_Management_System_Compact(?:;|$)') {
    throw 'Seed only supports localhost:1433 / Supermarket_Management_System_Compact.'
}
$builder = New-Object System.Data.SqlClient.SqlConnectionStringBuilder
$builder['Data Source'] = 'localhost,1433'
$builder['Initial Catalog'] = 'Supermarket_Management_System_Compact'
$builder['User ID'] = $properties['spring.datasource.username']
$builder['Password'] = $properties['spring.datasource.password']
if ($env:SPRING_DATASOURCE_USERNAME) { $builder['User ID'] = $env:SPRING_DATASOURCE_USERNAME }
if ($env:SPRING_DATASOURCE_PASSWORD) { $builder['Password'] = $env:SPRING_DATASOURCE_PASSWORD }
$builder['Encrypt'] = $true
$builder['TrustServerCertificate'] = $true
$connection = New-Object System.Data.SqlClient.SqlConnection($builder.ConnectionString)
try {
    $connection.Open()
    $backup = New-Object System.Data.DataSet('LocalSeedBackup')
    $list = $connection.CreateCommand()
    $list.CommandText = "SELECT name FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo')"
    $names = New-Object System.Data.DataTable
    $names.Load($list.ExecuteReader())
    foreach ($row in $names.Rows) {
        $safeName = $row.name.Replace(']', ']]')
        $adapter = New-Object System.Data.SqlClient.SqlDataAdapter("SELECT * FROM dbo.[$safeName]", $connection)
        [void]$adapter.Fill($backup, $row.name)
        $adapter.Dispose()
    }
    $backupPath = Join-Path ([IO.Path]::GetTempPath()) ('sms-before-seed-reset-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.xml')
    $backup.WriteXml($backupPath, [System.Data.XmlWriteMode]::WriteSchema)
    Write-Output "Local backup: $backupPath"
} finally { $connection.Dispose() }
& (Join-Path $PSScriptRoot 'seed-local.ps1') -SqlFile reset-pos-seed.sql
