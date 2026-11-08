$ErrorActionPreference = 'Stop'
$properties = @{}
Get-Content (Join-Path $PSScriptRoot '../src/main/resources/application.properties') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $properties[$matches[1].Trim()] = $matches[2].Trim() }
}
$url = $env:SPRING_DATASOURCE_URL
if (-not $url) { $url = $properties['spring.datasource.url'] }
if ($url -notmatch '^jdbc:sqlserver://localhost:1433;databaseName=Supermarket_Management_System_Compact(?:;|$)') {
    throw 'Migration only supports localhost / Supermarket_Management_System_Compact.'
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
    $command = $connection.CreateCommand()
    $command.CommandText = Get-Content (Join-Path $PSScriptRoot 'uc28-payment-migration.sql') -Raw -Encoding UTF8
    [void]$command.ExecuteNonQuery()
    Write-Output 'UC28 additive migration applied.'
} finally { $connection.Dispose() }
