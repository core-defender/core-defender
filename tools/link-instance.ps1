# CurseForge 인스턴스의 kubejs/ 하위 폴더를 이 저장소의 kubejs/ 로 연결(디렉터리 정션)한다.
# 사용: powershell -ExecutionPolicy Bypass -File tools/link-instance.ps1 -Instance "C:\...\Instances\CoreDefender"
# 인스턴스에 같은 이름의 실제 폴더가 있으면 <이름>.bak 으로 옮겨 둔다. 게임을 끈 상태에서 실행할 것.
param(
    [Parameter(Mandatory = $true)][string]$Instance
)

$repoKubejs = Join-Path (Split-Path $PSScriptRoot -Parent) 'kubejs'
$instKubejs = Join-Path $Instance 'kubejs'
New-Item -ItemType Directory -Force $instKubejs | Out-Null

foreach ($dir in @('server_scripts', 'startup_scripts', 'client_scripts', 'data', 'assets', 'config')) {
    $link = Join-Path $instKubejs $dir
    $target = Join-Path $repoKubejs $dir
    $existing = Get-Item $link -Force -ErrorAction SilentlyContinue
    if ($existing -and $existing.LinkType -eq 'Junction') {
        Remove-Item $link -Force
    } elseif ($existing) {
        Move-Item $link "$link.bak" -Force
        Write-Host "기존 폴더를 $link.bak 으로 옮겼습니다"
    }
    New-Item -ItemType Junction -Path $link -Target $target | Out-Null
    Write-Host "$link -> $target"
}
