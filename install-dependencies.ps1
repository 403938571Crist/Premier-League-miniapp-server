# 英超资讯聚合服务 - 环境安装脚本
# 以管理员身份运行 PowerShell，然后执行: .\install-dependencies.ps1

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "英超资讯聚合服务 - 环境安装脚本" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 检查是否以管理员身份运行
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Warning "请以管理员身份运行此脚本!"
    exit 1
}

# 安装 OpenJDK 17
Write-Host "`n[1/3] 正在安装 OpenJDK 17..." -ForegroundColor Yellow
$jdkUrl = "https://aka.ms/download-jdk/microsoft-jdk-17.0.12-windows-x64.msi"
$jdkInstaller = "$env:TEMP\microsoft-jdk-17.msi"

try {
    Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkInstaller -ErrorAction Stop
    Start-Process msiexec.exe -ArgumentList "/i", $jdkInstaller, "/quiet", "/norestart" -Wait
    Write-Host "OK OpenJDK 17 安装完成" -ForegroundColor Green
    
    # 设置环境变量
    $jdkPath = "C:\Program Files\Microsoft\jdk-17"
    if (Test-Path $jdkPath) {
        [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkPath, "Machine")
        $path = [Environment]::GetEnvironmentVariable("Path", "Machine")
        $binPath = "$jdkPath\bin"
        if ($path -notlike "*$binPath*") {
            [Environment]::SetEnvironmentVariable("Path", "$path;$binPath", "Machine")
        }
        Write-Host "OK JAVA_HOME 环境变量已设置" -ForegroundColor Green
    }
} catch {
    Write-Error "JDK 安装失败: $_"
}

# 安装 Maven
Write-Host "`n[2/3] 正在安装 Apache Maven..." -ForegroundColor Yellow
$mavenUrl = "https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip"
$mavenZip = "$env:TEMP\apache-maven-3.9.6-bin.zip"
$mavenInstallDir = "C:\apache-maven"

try {
    Invoke-WebRequest -Uri $mavenUrl -OutFile $mavenZip -ErrorAction Stop
    Expand-Archive -Path $mavenZip -DestinationPath $mavenInstallDir -Force
    
    # 设置环境变量
    $mavenPath = "$mavenInstallDir\apache-maven-3.9.6"
    [Environment]::SetEnvironmentVariable("MAVEN_HOME", $mavenPath, "Machine")
    $path = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $mavenBin = "$mavenPath\bin"
    if ($path -notlike "*$mavenBin*") {
        [Environment]::SetEnvironmentVariable("Path", "$path;$mavenBin", "Machine")
    }
    Write-Host "OK Maven 安装完成" -ForegroundColor Green
} catch {
    Write-Error "Maven 安装失败: $_"
}

# 安装 MySQL
Write-Host "`n[3/3] 正在安装 MySQL..." -ForegroundColor Yellow
$mysqlUrl = "https://dev.mysql.com/get/Downloads/MySQLInstaller/mysql-installer-community-8.0.36.0.msi"
$mysqlInstaller = "$env:TEMP\mysql-installer.msi"

try {
    Invoke-WebRequest -Uri $mysqlUrl -OutFile $mysqlInstaller -ErrorAction Stop
    Start-Process msiexec.exe -ArgumentList "/i", $mysqlInstaller, "/quiet", "/norestart" -Wait
    Write-Host "OK MySQL Installer 已启动，请完成后续安装配置" -ForegroundColor Green
    Write-Host "  安装时请设置 root 密码为: root" -ForegroundColor Yellow
} catch {
    Write-Error "MySQL 安装失败: $_"
    Write-Host "  请手动从 https://dev.mysql.com/downloads/installer/ 下载安装" -ForegroundColor Yellow
}

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "安装脚本执行完成!" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "请重启终端或 IDE 以应用环境变量更改" -ForegroundColor Yellow
Write-Host "`n验证安装:" -ForegroundColor Cyan
Write-Host "  java -version" -ForegroundColor Gray
Write-Host "  mvn -version" -ForegroundColor Gray
Write-Host "  mysql --version" -ForegroundColor Gray
