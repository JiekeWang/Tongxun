# Incremental Server Files Upload Script
# Usage: .\upload-server-incremental.ps1 [file1] [file2] ...
# If no files specified, uploads all changed files

$ServerIP = "47.116.197.230"
$ServerUser = "root"
$ServerPassword = "232629wh@"
$RemoteBasePath = "/var/www/tongxun/server"

# Define server file mappings (local path -> remote path)
$fileMappings = @{
    "server/src/websocket/socketHandler.js" = "server/src/websocket/socketHandler.js"
    "server/src/routes/group.js" = "server/src/routes/group.js"
    "server/src/config/database.js" = "server/src/config/database.js"
    "server/src/index.js" = "server/src/index.js"
    "server/src/routes/auth.js" = "server/src/routes/auth.js"
    "server/src/routes/user.js" = "server/src/routes/user.js"
    "server/src/routes/friend.js" = "server/src/routes/friend.js"
    "server/src/routes/message.js" = "server/src/routes/message.js"
    "server/src/routes/upload.js" = "server/src/routes/upload.js"
    "server/src/middleware/auth.js" = "server/src/middleware/auth.js"
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Incremental Server Files Upload" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Server: ${ServerUser}@${ServerIP}" -ForegroundColor Yellow
Write-Host "Remote Base Path: ${RemoteBasePath}" -ForegroundColor Yellow
Write-Host ""

# Get files to upload from command line arguments, or use all mappings
$filesToUpload = @()
if ($args.Count -gt 0) {
    # Upload specific files provided as arguments
    foreach ($arg in $args) {
        if ($fileMappings.ContainsKey($arg)) {
            $filesToUpload += $arg
        } else {
            Write-Host "Warning: File not in mappings: $arg" -ForegroundColor Yellow
            Write-Host "  Available files:" -ForegroundColor Gray
            foreach ($key in $fileMappings.Keys | Sort-Object) {
                Write-Host "    $key" -ForegroundColor Gray
            }
        }
    }
} else {
    # Upload all files in mappings
    $filesToUpload = $fileMappings.Keys | Sort-Object
}

if ($filesToUpload.Count -eq 0) {
    Write-Host "No files to upload!" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Usage examples:" -ForegroundColor Cyan
    Write-Host "  .\upload-server-incremental.ps1" -ForegroundColor White
    Write-Host "    (uploads all mapped files)" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  .\upload-server-incremental.ps1 server/src/routes/group.js" -ForegroundColor White
    Write-Host "    (uploads only group.js)" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  .\upload-server-incremental.ps1 server/src/routes/group.js server/src/config/database.js" -ForegroundColor White
    Write-Host "    (uploads multiple files)" -ForegroundColor Gray
    exit 0
}

Write-Host "Files to upload ($($filesToUpload.Count)):" -ForegroundColor Green
foreach ($file in $filesToUpload) {
    Write-Host "  - $file" -ForegroundColor White
}
Write-Host ""

# Upload each file
$uploadedCount = 0
$failedFiles = @()

foreach ($localFile in $filesToUpload) {
    if (-not (Test-Path $localFile)) {
        Write-Host "Error: File not found: $localFile" -ForegroundColor Red
        $failedFiles += $localFile
        continue
    }
    
    $remoteFile = $fileMappings[$localFile]
    $remoteFullPath = "$RemoteBasePath/$remoteFile"
    $remoteDir = Split-Path -Path $remoteFullPath -Parent
    
    Write-Host "[$($uploadedCount + 1)/$($filesToUpload.Count)] Uploading: $localFile" -ForegroundColor Cyan
    
    # Upload file
    scp "$localFile" "${ServerUser}@${ServerIP}:$remoteFullPath"
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ Uploaded successfully" -ForegroundColor Green
        $uploadedCount++
    } else {
        Write-Host "  ✗ Upload failed" -ForegroundColor Red
        $failedFiles += $localFile
    }
    Write-Host ""
}

# Summary
Write-Host "========================================" -ForegroundColor Cyan
if ($failedFiles.Count -eq 0) {
    Write-Host "  All files uploaded successfully!" -ForegroundColor Green
    Write-Host "  Uploaded: $uploadedCount file(s)" -ForegroundColor Green
    
    # Restart PM2 service
    Write-Host ""
    Write-Host "Restarting PM2 service..." -ForegroundColor Yellow
    Write-Host "Note: You will be prompted for password" -ForegroundColor Gray
    Write-Host ""
    
    ssh "${ServerUser}@${ServerIP}" "cd /var/www/tongxun/server && pm2 restart tongxun-server"
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ Service restarted successfully" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Service restart may have failed (exit code: $LASTEXITCODE)" -ForegroundColor Yellow
    }
    Write-Host ""
    
    # Show logs
    Write-Host "Fetching recent logs (last 20 lines)..." -ForegroundColor Yellow
    Write-Host ""
    ssh "${ServerUser}@${ServerIP}" "cd /var/www/tongxun/server && pm2 logs tongxun-server --lines 20 --nostream"
    
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
}
Write-Host ""

Write-Host "To view real-time logs, run:" -ForegroundColor Yellow
Write-Host "  ssh ${ServerUser}@${ServerIP}" -ForegroundColor White
Write-Host "  cd /var/www/tongxun/server" -ForegroundColor White
Write-Host "  pm2 logs tongxun-server" -ForegroundColor White
Write-Host ""

