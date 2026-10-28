<#
.SYNOPSIS
    加载项目 .env 文件并启动 YonErp 后端。
#>
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path (Split-Path -Parent $root) '.env'

if (Test-Path $envFile) {
    Write-Host "Loading environment from $envFile"
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#')) {
            $parts = $line.Split('=', 2)
            if ($parts.Length -eq 2) {
                $name = $parts[0].Trim()
                $value = $parts[1].Trim()
                if ($name -and $null -ne $value) {
                    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
                }
            }
        }
    }
} else {
    Write-Warning ".env file not found at $envFile"
}

Set-Location $root
Write-Host "Starting YonErp backend...`n"
mvn spring-boot:run
