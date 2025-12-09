# Incremental Server Files Upload Script
# Automatically detects and uploads changed/new files since last upload
# Usage: .\upload-websocket.ps1

$ServerIP = "47.116.197.230"
$ServerUser = "root"
$ServerPassword = "232629wh@"
$RemoteBasePath = "/var/www/tongxun/server"
$LocalBasePath = "server/src"

# File to store last upload timestamp
$timestampFile = ".last-upload-timestamp"

# SSH options configuration
# Note: ControlMaster is disabled by default on Windows OpenSSH due to limited support
# SSH key authentication is used instead (configured separately)
# This avoids "getsockname failed: Not a socket" errors
$sshOptions = "-o StrictHostKeyChecking=no -o UserKnownHostsFile=NUL"
$useControlMaster = $false

# Function to parse SSH options string into argument array
function Parse-SSHOptions {
    param([string]$OptionsString)
    
    $args = @()
    # Simple approach: split by spaces and reconstruct
    # Handle quoted values properly
    $tokens = @()
    $currentToken = ""
    $inQuotes = $false
    
    for ($i = 0; $i -lt $OptionsString.Length; $i++) {
        $char = $OptionsString[$i]
        if ($char -eq '"') {
            $inQuotes = -not $inQuotes
            $currentToken += $char
        } elseif ($char -eq ' ' -and -not $inQuotes) {
            if ($currentToken) {
                $tokens += $currentToken
                $currentToken = ""
            }
        } else {
            $currentToken += $char
        }
    }
    if ($currentToken) {
        $tokens += $currentToken
    }
    
    # Now process tokens
    for ($i = 0; $i -lt $tokens.Length; $i++) {
        if ($tokens[$i] -eq '-o') {
            $args += "-o"
            if ($i + 1 -lt $tokens.Length) {
                $value = $tokens[$i + 1]
                # Remove quotes if present
                if ($value.StartsWith('"') -and $value.EndsWith('"')) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
                $args += $value
                $i++  # Skip the next token as we've already processed it
            }
        }
    }
    
    return $args
}

# Function to establish SSH master connection
function Establish-SSHConnection {
    param([string]$User, [string]$ServerHost, [string]$Options)
    
    Write-Host "Testing SSH connection..." -ForegroundColor Yellow
    Write-Host "(Using SSH key authentication - no password needed)" -ForegroundColor Gray
    Write-Host ""
    
    # Test SSH connection with key authentication
    # Parse SSH options properly
    $sshArgs = Parse-SSHOptions -OptionsString $Options
    $sshArgs += "$User@$ServerHost"
    $sshArgs += "echo 'Connection test successful'"
    
    # Capture both stdout and stderr
    $testResult = & ssh $sshArgs 2>&1
    $exitCode = $LASTEXITCODE
    $outputText = $testResult | Out-String
    
    if ($exitCode -eq 0) {
        Write-Host "✓ SSH connection successful!" -ForegroundColor Green
        Write-Host ""
    } else {
        Write-Host "⚠ Warning: SSH connection test failed (exit code: $exitCode)" -ForegroundColor Yellow
        if ($outputText -and $outputText.Trim()) {
            Write-Host "  Error details:" -ForegroundColor Yellow
            $outputText.Trim().Split("`n") | ForEach-Object {
                Write-Host "    $_" -ForegroundColor Gray
            }
        }
        Write-Host "  Continuing anyway..." -ForegroundColor Yellow
        Write-Host ""
    }
}

# Function to cleanup SSH control socket
function Close-SSHConnection {
    param([string]$User, [string]$ServerHost, [string]$Options)
    
    # Try to close the master connection gracefully
    $sshArgs = Parse-SSHOptions -OptionsString $Options
    $sshArgs += "-O"
    $sshArgs += "exit"
    $sshArgs += "$User@$ServerHost"
    & ssh $sshArgs 2>&1 | Out-Null
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Incremental Server Files Upload" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Server: ${ServerUser}@${ServerIP}" -ForegroundColor Yellow
Write-Host "Remote Path: ${RemoteBasePath}" -ForegroundColor Yellow
Write-Host "Local Path: ${LocalBasePath}" -ForegroundColor Yellow
Write-Host ""

# Establish SSH master connection (user will be prompted for password once)
Establish-SSHConnection -User $ServerUser -ServerHost $ServerIP -Options $sshOptions

# Check if local server directory exists
if (-not (Test-Path $LocalBasePath)) {
    Write-Host "Error: Local server directory not found: $LocalBasePath" -ForegroundColor Red
    exit 1
}

# Get last upload timestamp
$lastUploadTime = $null
if (Test-Path $timestampFile) {
    $timestampContent = Get-Content $timestampFile -Raw
    if ($timestampContent) {
        $timestampContent = $timestampContent.Trim()
        # Try to parse the timestamp using PowerShell's type casting
        try {
            $lastUploadTime = [DateTime]$timestampContent
            Write-Host "Last upload time: $lastUploadTime" -ForegroundColor Gray
        } catch {
            Write-Host "Warning: Invalid timestamp format in file, will upload all files" -ForegroundColor Yellow
            Write-Host "  Timestamp content: '$timestampContent'" -ForegroundColor Gray
            Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Gray
        }
    } else {
        Write-Host "Warning: Timestamp file is empty, will upload all files" -ForegroundColor Yellow
    }
} else {
    Write-Host "No previous upload record found, will upload all files" -ForegroundColor Yellow
}

Write-Host ""

# Resolve and normalize the local server/src path
try {
    $localServerSrcPath = (Resolve-Path $LocalBasePath).Path
} catch {
    Write-Host "Error: Cannot resolve local path: $LocalBasePath" -ForegroundColor Red
    Write-Host "Please ensure the directory exists and try again." -ForegroundColor Red
    exit 1
}

# Get all files in server/src directory (recursively)
$allFiles = Get-ChildItem -Path $localServerSrcPath -File -Recurse | Where-Object {
    # Exclude test files, logs, and other non-essential files
    $_.FullName -notmatch "\\__tests__\\" -and
    $_.FullName -notmatch "\\node_modules\\" -and
    $_.FullName -notmatch "\\.log$" -and
    $_.FullName -notmatch "\\.test\\.js$" -and
    $_.FullName -notmatch "\\.spec\\.js$"
}

# Filter files that have changed since last upload
$filesToUpload = @()
if ($null -eq $lastUploadTime) {
    # Upload all files if no previous timestamp
    $filesToUpload = $allFiles
    Write-Host "Found $($allFiles.Count) files to upload (first upload)" -ForegroundColor Green
} else {
    # Only upload files modified after last upload time
    $filesToUpload = $allFiles | Where-Object { 
        $_.LastWriteTime -gt $lastUploadTime 
    }
    Write-Host "Found $($filesToUpload.Count) changed/new files to upload" -ForegroundColor Green
}

if ($filesToUpload.Count -eq 0) {
    Write-Host "No files to upload - all files are up to date!" -ForegroundColor Green
    Write-Host ""
    exit 0
}

Write-Host ""
Write-Host "Files to upload:" -ForegroundColor Cyan
foreach ($file in $filesToUpload) {
    try {
        $relativePathFromServerSrc = $file.FullName.Substring($localServerSrcPath.Length + 1) -replace "\\", "/"
        $displayPath = "server/src/$relativePathFromServerSrc"
        Write-Host "  - $displayPath" -ForegroundColor White
    } catch {
        Write-Host "  - $($file.FullName) [Error calculating relative path]" -ForegroundColor Yellow
    }
}
Write-Host ""

# Upload each file
$uploadedCount = 0
$failedFiles = @()

foreach ($file in $filesToUpload) {
    try {
        # Get relative path from server/src directory
        # Use Substring to reliably extract relative path from absolute path
        $fileFullPath = $file.FullName
        if (-not $fileFullPath.StartsWith($localServerSrcPath)) {
            Write-Host "  ✗ Error: File path does not start with expected base path" -ForegroundColor Red
            Write-Host "    File: $fileFullPath" -ForegroundColor Red
            Write-Host "    Base: $localServerSrcPath" -ForegroundColor Red
            $failedFiles += $fileFullPath
            continue
        }
        $relativePathFromServerSrc = $fileFullPath.Substring($localServerSrcPath.Length + 1) -replace "\\", "/"
        
        # Build remote path: /var/www/tongxun/server/src/[relative path]
        $remoteFullPath = "$RemoteBasePath/src/$relativePathFromServerSrc"
        $remoteDir = Split-Path -Path $remoteFullPath -Parent
        
        # Get display relative path (for logging)
        $displayPath = "server/src/$relativePathFromServerSrc"
        
        Write-Host "[$($uploadedCount + 1)/$($filesToUpload.Count)] Uploading: $displayPath" -ForegroundColor Cyan
        Write-Host "  Local:  $fileFullPath" -ForegroundColor Gray
        Write-Host "  Remote: $remoteFullPath" -ForegroundColor Gray
        
        # Ensure remote directory exists before uploading (silently create if needed)
        if ($remoteDir -and $remoteDir -ne "$RemoteBasePath/src") {
            $sshArgs = Parse-SSHOptions -OptionsString $sshOptions
            $sshArgs += "${ServerUser}@${ServerIP}"
            $sshArgs += "mkdir -p `"$remoteDir`""
            $createDirResult = & ssh $sshArgs 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Host "  ⚠ Warning: Failed to create remote directory (may already exist)" -ForegroundColor Yellow
            }
        }
        
        # Upload file with proper path quoting and SSH options
        $scpArgs = Parse-SSHOptions -OptionsString $sshOptions
        $scpArgs += "`"$fileFullPath`""
        $scpArgs += "${ServerUser}@${ServerIP}:`"$remoteFullPath`""
        & scp $scpArgs
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  ✓ Uploaded successfully" -ForegroundColor Green
            $uploadedCount++
        } else {
            Write-Host "  ✗ Upload failed (exit code: $LASTEXITCODE)" -ForegroundColor Red
            $failedFiles += $displayPath
        }
    } catch {
        Write-Host "  ✗ Error processing file: $($_.Exception.Message)" -ForegroundColor Red
        $failedFiles += $file.FullName
    }
    Write-Host ""
}

# Summary
Write-Host "========================================" -ForegroundColor Cyan
if ($failedFiles.Count -eq 0) {
    Write-Host "  All files uploaded successfully!" -ForegroundColor Green
    Write-Host "  Uploaded: $uploadedCount file(s)" -ForegroundColor Green
    
    # Update timestamp file
    $currentTime = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Set-Content -Path $timestampFile -Value $currentTime
    Write-Host "  Timestamp updated: $currentTime" -ForegroundColor Gray
    
    # Restart PM2 service
    Write-Host ""
    Write-Host "Restarting PM2 service..." -ForegroundColor Yellow
    Write-Host ""
    
    $sshArgs = Parse-SSHOptions -OptionsString $sshOptions
    $sshArgs += "${ServerUser}@${ServerIP}"
    $sshArgs += "cd /var/www/tongxun/server && pm2 restart tongxun-server"
    & ssh $sshArgs
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ Service restarted successfully" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Service restart may have failed (exit code: $LASTEXITCODE)" -ForegroundColor Yellow
    }
    Write-Host ""
    
    # Show logs
    Write-Host "Fetching recent logs (last 20 lines)..." -ForegroundColor Yellow
    Write-Host ""
    $sshArgs = Parse-SSHOptions -OptionsString $sshOptions
    $sshArgs += "${ServerUser}@${ServerIP}"
    $sshArgs += "cd /var/www/tongxun/server && pm2 logs tongxun-server --lines 20 --nostream"
    & ssh $sshArgs
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  Deployment completed!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host "  Upload completed with errors" -ForegroundColor Yellow
    Write-Host "  Success: $uploadedCount file(s)" -ForegroundColor Green
    Write-Host "  Failed: $($failedFiles.Count) file(s)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Failed files:" -ForegroundColor Red
    foreach ($file in $failedFiles) {
        Write-Host "  - $file" -ForegroundColor Red
    }
    Write-Host ""
    Write-Host "Note: Timestamp was NOT updated due to errors" -ForegroundColor Yellow
}

# Close SSH master connection
Close-SSHConnection -User $ServerUser -ServerHost $ServerIP -Options $sshOptions

Write-Host ""
Write-Host "To view real-time logs, run:" -ForegroundColor Yellow
Write-Host "  ssh ${ServerUser}@${ServerIP}" -ForegroundColor White
Write-Host "  cd /var/www/tongxun/server" -ForegroundColor White
Write-Host "  pm2 logs tongxun-server" -ForegroundColor White
Write-Host ""
