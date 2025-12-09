# Quick upload script for group-related server files
# Usage: .\upload-server-group.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Upload Group Feature Server Files" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Upload group-related files
.\upload-server-incremental.ps1 server/src/routes/group.js server/src/config/database.js server/src/websocket/socketHandler.js

