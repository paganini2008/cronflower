# Shared build helpers for run-local.ps1 and run-docker.ps1 (dot-sourced, not run directly).
# Mirrors _build.sh. Paths are derived from this file's own directory (= deploy/).

$ErrorActionPreference = 'Stop'

$Here     = $PSScriptRoot
$Root     = Split-Path -Parent $Here
$Backend  = Join-Path $Root 'backend'
$Frontend = Join-Path $Root 'frontend'
$Bin      = Join-Path $Here 'bin'
$Conf     = Join-Path $Here 'conf'
$RunDir   = Join-Path $Here 'run'
$Logs     = Join-Path $Here 'logs'
$Data     = Join-Path $Here 'data'

$Version  = if ($env:VERSION) { $env:VERSION } else { '1.0.0-SNAPSHOT' }
$SchedJar = "cronsmith-scheduler-example-$Version.jar"
$ExecJar  = "cronsmith-executor-example-$Version.jar"

# Executors get a random port in this range.
$ExecPortLo = if ($env:EXEC_PORT_LO) { [int]$env:EXEC_PORT_LO } else { 50000 }
$ExecPortHi = if ($env:EXEC_PORT_HI) { [int]$env:EXEC_PORT_HI } else { 60000 }

# JVM heap per node, in whole GB (applied as -Xmx). Default 1G each. Also drives the capacity guard.
$SchedXmxGb  = if ($env:SCHED_XMX_GB)   { [int]$env:SCHED_XMX_GB }   else { 1 }
$ExecXmxGb   = if ($env:EXEC_XMX_GB)    { [int]$env:EXEC_XMX_GB }    else { 1 }
$MemBudgetPct = if ($env:MEM_BUDGET_PCT) { [int]$env:MEM_BUDGET_PCT } else { 70 }

# Maven: prefer the project's wrapper (backend\mvnw.cmd — no system Maven needed), then $env:MVN, then mvn.
$Mvn =
  if     (Test-Path (Join-Path $Backend 'mvnw.cmd')) { Join-Path $Backend 'mvnw.cmd' }
  elseif ($env:MVN)                                  { $env:MVN }
  elseif (Get-Command mvn -ErrorAction SilentlyContinue) { 'mvn' }
  else   { throw '!! Maven not found - set $env:MVN or keep backend\mvnw.cmd' }

# The base engine cronsmith + openspreader are not on Maven Central yet; resolved from the local repo.
# Set $env:M2_REPO to pin a specific local repository (else Maven's default / your settings.xml is used).
$RepoArg = @()
if ($env:M2_REPO -and (Test-Path $env:M2_REPO)) { $RepoArg = @("-Dmaven.repo.local=$($env:M2_REPO)") }

# A cronsmith checkout next to cronflower is installed first when present.
$CronsmithRepo = if ($env:CRONSMITH_REPO) { $env:CRONSMITH_REPO } else { Join-Path (Split-Path -Parent $Root) 'cronsmith' }

function Build-Backend {
  if (Test-Path (Join-Path $CronsmithRepo 'pom.xml')) {
    Write-Host ">> installing base engine (cronsmith) from $CronsmithRepo"
    & $Mvn -q @RepoArg -f (Join-Path $CronsmithRepo 'pom.xml') install -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'base engine install failed' }
  } else {
    Write-Host ">> base engine checkout not found at $CronsmithRepo - assuming 'cronsmith' is already in the local Maven repo"
  }
  Write-Host ">> building backend (4-module reactor)"
  & $Mvn -q @RepoArg -f (Join-Path $Backend 'pom.xml') clean install -DskipTests
  if ($LASTEXITCODE -ne 0) { throw 'backend build failed' }
}

function Stage-Jars {
  New-Item -ItemType Directory -Force -Path $Bin | Out-Null
  Copy-Item (Join-Path $Backend "cronsmith-scheduler-example\target\$SchedJar") (Join-Path $Bin $SchedJar) -Force
  Copy-Item (Join-Path $Backend "cronsmith-executor-example\target\$ExecJar")   (Join-Path $Bin $ExecJar)  -Force
  Write-Host ">> staged jars into bin\: $SchedJar, $ExecJar"
}

function Build-FrontendDist {
  $ng = if ($env:NG_CONFIG) { $env:NG_CONFIG } else { 'production' }
  Write-Host ">> building the web console ($Frontend, ng $ng)"
  Push-Location $Frontend
  try {
    & npm.cmd install --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
    & npx.cmd ng build --configuration $ng
    if ($LASTEXITCODE -ne 0) { throw 'ng build failed' }
  } finally { Pop-Location }
}

# Echo a random free port in [ExecPortLo, ExecPortHi], skipping any in $used (int[]) and any bound host port.
function Get-FreePort([int[]]$used) {
  for ($t = 0; $t -lt 300; $t++) {
    $p = Get-Random -Minimum $ExecPortLo -Maximum ($ExecPortHi + 1)
    if ($used -contains $p) { continue }
    $bound = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
    if (-not $bound) { return $p }
  }
  return $p
}

# Poll a scheduler's health endpoint until UP (or time out). Returns $true/$false.
function Wait-Up([int]$port) {
  Write-Host -NoNewline ">> waiting for scheduler :$port "
  for ($i = 0; $i -lt 40; $i++) {
    try {
      $r = Invoke-WebRequest "http://localhost:$port/actuator/health" -UseBasicParsing -TimeoutSec 3
      if ($r.StatusCode -eq 200) { Write-Host 'ok'; return $true }
    } catch { }
    Write-Host -NoNewline '.'; Start-Sleep -Seconds 2
  }
  Write-Host ' timeout'; return $false
}

# --- Preflight & capacity guards ------------------------------------------------------------

function Check-Java {
  if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Write-Host '!! java not found on PATH - install a JDK 17+'; exit 1 }
  $line = (& java -version 2>&1 | Select-Object -First 1) -join ' '
  $ver = if ($line -match '"([0-9][0-9._]*)"') { $matches[1] } else { '0' }
  $parts = $ver -split '\.'
  $major = if ($parts[0] -eq '1' -and $parts.Count -gt 1) { [int]$parts[1] } else { [int]$parts[0] }
  if ($major -lt 17) { Write-Host "!! JDK 17+ required, found '$ver'. Point JAVA_HOME at a 17+ JDK."; exit 1 }
  Write-Host ">> JDK ok (java $ver)"
}

function Check-Node {
  if (-not (Get-Command node -ErrorAction SilentlyContinue)) { Write-Host '!! node not found on PATH - install Node 20+'; exit 1 }
  if (-not (Get-Command npm  -ErrorAction SilentlyContinue)) { Write-Host '!! npm not found on PATH - install Node 20+ (with npm)'; exit 1 }
  $ver = (& node -v).TrimStart('v')
  $major = [int](($ver -split '\.')[0])
  if ($major -lt 20) { Write-Host "!! Node 20+ required, found 'v$ver'."; exit 1 }
  Write-Host ">> Node ok (v$ver)"
}

function Check-Prereqs([bool]$docker = $false) {
  Write-Host '>> checking prerequisites'
  Check-Java
  Check-Node
  if ($docker) {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Write-Host '!! docker not found on PATH'; exit 1 }
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) { Write-Host '!! Docker is not running - start Docker Desktop'; exit 1 }
    Write-Host '>> Docker ok'
  }
}

function Host-MemGb { [int][math]::Floor((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB) }

function Docker-MemGb {
  try { $b = [int64](& docker info --format '{{.MemTotal}}' 2>$null); return [int][math]::Floor($b / 1GB) } catch { return 0 }
}

# Refuse to start if requested heap exceeds MemBudgetPct% of $total GB.
function Check-Capacity([int]$nodes, [int]$execs, [int]$total, [string]$label) {
  if ($total -le 0) { Write-Host ">> capacity check skipped (could not read $label memory)"; return }
  $req = $nodes * $SchedXmxGb + $execs * $ExecXmxGb
  $budget = [int][math]::Floor($total * $MemBudgetPct / 100)
  Write-Host ">> capacity: need ~${req}G heap ($nodes sched x ${SchedXmxGb}G + $execs exec x ${ExecXmxGb}G); budget ${budget}G ($MemBudgetPct% of ${total}G $label)"
  if ($req -gt $budget) {
    Write-Host "!! refusing to start: ${req}G requested > ${budget}G budget."
    Write-Host '   reduce -n / -e, lower $env:SCHED_XMX_GB / $env:EXEC_XMX_GB, or raise $env:MEM_BUDGET_PCT.'
    exit 1
  }
}
