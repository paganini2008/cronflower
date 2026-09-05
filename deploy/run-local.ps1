<#
  cronsmith - one-click LOCAL runner for Windows (PowerShell). Mirror of run-local.sh.

    .\run-local.ps1            [-n N] [-e M]     build & start everything ('up' is optional)
    .\run-local.ps1 down                         stop everything this script started
    .\run-local.ps1 logs [scheduler-1|executor-1|frontend]   tail a log

  -n  number of scheduler (server) nodes   (default 1)
  -e  number of executor (client) nodes    (default 0 = none)

  Store: each node gets its OWN H2 file (deploy\data\cronsmith-<n>); the leader broadcasts every write
  and each node keeps its own copy in sync (so a failover keeps the data). Persists across restarts.
  Uncomment a datasource in conf\scheduler.properties for a shared MySQL/PostgreSQL.
  Ports: seed scheduler 19090 . followers RANDOM (auto-discovered) . console 7200 . executors random.
  The console (web-server.mjs) discovers the whole cluster from the single seed and load-balances -
  no nginx/KONG. Seed (node 1) starts first, then followers on random ports.

  If scripts are blocked, run:  powershell -ExecutionPolicy Bypass -File .\run-local.ps1 -n 2 -e 1
#>
param(
  [Parameter(Position = 0)][string]$Action = 'up',
  [Parameter(Position = 1)][string]$Svc = 'scheduler-1',
  [int]$n = 1,
  [int]$e = 0
)

. "$PSScriptRoot\_build.ps1"

$SchedBasePort = if ($env:SCHED_BASE_PORT) { [int]$env:SCHED_BASE_PORT } else { 19090 }
$FrontendPort  = if ($env:FRONTEND_PORT)   { [int]$env:FRONTEND_PORT }   else { 7200 }
$SpreaderPort  = if ($env:SPREADER_PORT)   { [int]$env:SPREADER_PORT }   else { 22000 }
$ConfUrl       = 'file:' + ($Conf -replace '\\', '/') + '/scheduler.properties'

function Save-Pid([int]$procId, [string]$name) { $procId | Out-File -Encoding ascii (Join-Path $RunDir "$name.pid") }

# Each node gets its OWN independent H2 file (node-local replicated model: leader broadcasts, every
# node keeps its own copy). conf wins if it sets a datasource (e.g. a shared MySQL/PostgreSQL).
function Scheduler-DsArgs([int]$i) {
  $confFile = Join-Path $Conf 'scheduler.properties'
  if ((Test-Path $confFile) -and (Select-String -Path $confFile -Pattern '^\s*spring\.datasource\.url=' -Quiet)) { return @() }
  return @("--spring.datasource.url=jdbc:h2:file:./data/cronsmith-$i;DB_CLOSE_DELAY=-1", '--spring.datasource.username=sa', '--spring.datasource.password=')
}

function Launch-Scheduler([int]$i) {
  # Node 1 is the seed on a fixed, well-known HTTP port (the console/executors bootstrap from it).
  # Followers take a RANDOM HTTP port (server.port=0): nobody needs to know it - the console discovers
  # every node (and its real HTTP port) from the seed's /cluster roster and load-balances across them.
  $port = if ($i -eq 1) { $SchedBasePort } else { 0 }
  $jarArgs = @(
    "-Xmx$($SchedXmxGb)g",
    '-jar', (Join-Path $Bin $SchedJar),
    "--server.port=$port",
    "--spring.spreader.port=$SpreaderPort",
    '--spring.spreader.ip-addresses=127.0.0.1',
    "--spring.config.additional-location=$ConfUrl"
  ) + (Scheduler-DsArgs $i)
  $p = Start-Process -FilePath 'java' -ArgumentList $jarArgs -WorkingDirectory $Here `
        -RedirectStandardOutput (Join-Path $Logs "scheduler-$i.log") `
        -RedirectStandardError  (Join-Path $Logs "scheduler-$i.err.log") `
        -PassThru -WindowStyle Hidden
  Save-Pid $p.Id "scheduler-$i"
  if ($i -eq 1) { Write-Host "     node 1 (seed)  ->  http://localhost:$SchedBasePort/cronsmith/tasks   (log: logs\scheduler-1.log)" }
  else          { Write-Host "     node $i         ->  random HTTP port, auto-discovered via the seed   (log: logs\scheduler-$i.log)" }
}

function Start-Schedulers([int]$nodes) {
  Write-Host ">> starting $nodes scheduler node(s) - seed (node 1) on :$SchedBasePort first, then the rest on random ports"
  Launch-Scheduler 1
  if (-not (Wait-Up $SchedBasePort)) { Write-Host '!! scheduler-1 did not come up - see logs\scheduler-1.log'; exit 1 }
  for ($i = 2; $i -le $nodes; $i++) { Launch-Scheduler $i }
  # Followers use random ports, so wait on cluster convergence (roster size), not per-node ports.
  if ($nodes -gt 1) { Wait-Cluster $nodes }
}

# Wait until the seed reports the full roster - port-agnostic, so it works with random follower ports.
function Wait-Cluster([int]$want) {
  Write-Host -NoNewline ">> waiting for the $want-node cluster to converge "
  for ($i = 0; $i -lt 60; $i++) {
    try {
      $r = Invoke-RestMethod "http://localhost:$SchedBasePort/cronsmith/cluster" -TimeoutSec 3
      if ($r.nodeCount -ge $want) { Write-Host "ok ($($r.nodeCount) nodes)"; return }
    } catch { }
    Write-Host -NoNewline '.'; Start-Sleep -Seconds 2
  }
  Write-Host ' timeout - continuing anyway'
}

function Start-Frontend {
  $dist = Join-Path $Frontend 'dist\cronflower\browser'
  Write-Host ">> building the web console (ng build), then serving via web-server.mjs on :$FrontendPort"
  Write-Host "   (single-seed cluster discovery + load-balancing - no nginx/KONG; matches the Docker path)"
  if (-not $env:NG_CONFIG) { $env:NG_CONFIG = 'development' }
  Build-FrontendDist
  # Patch the served (disposable) build output's config.json - never the source under public\.
  $cfg = Join-Path $dist 'config.json'
  if (Test-Path $cfg) {
    try { $j = Get-Content $cfg -Raw | ConvertFrom-Json } catch { $j = [pscustomobject]@{} }
    $j | Add-Member -NotePropertyName apiPrefix -NotePropertyValue '/cronsmith' -Force
    ($j | ConvertTo-Json) | Out-File -Encoding ascii $cfg
  }
  # web-server.mjs: static dist + discover the cluster from the seed and round-robin the API across nodes.
  $env:PORT = "$FrontendPort"; $env:WEB_ROOT = $dist
  $env:SCHEDULER_URL = "http://localhost:$SchedBasePort"; $env:API_PREFIX = '/cronsmith'
  $p = Start-Process -FilePath 'node' -ArgumentList @((Join-Path $Here 'web-server.mjs')) -WorkingDirectory $Here `
        -RedirectStandardOutput (Join-Path $Logs 'frontend.log') `
        -RedirectStandardError  (Join-Path $Logs 'frontend.err.log') `
        -PassThru -WindowStyle Hidden
  Save-Pid $p.Id 'frontend'
  Write-Host "     console  ->  http://localhost:$FrontendPort   (log: logs\frontend.log)"
}

function Start-Executors([int]$execs, [int]$schedNodes) {
  Write-Host ">> starting $execs executor node(s) - random ports in $ExecPortLo-$ExecPortHi"
  # The executor only needs to reach ONE node to register (the cluster forwards writes to the leader);
  # it also fails over across a comma-separated list if you give it more. Seed is enough here.
  $urls = "http://localhost:$SchedBasePort"
  $execConf = 'file:' + ($Conf -replace '\\', '/') + '/executor.properties'
  $used = @()
  for ($i = 1; $i -le $execs; $i++) {
    $port = Get-FreePort $used; $used += $port
    $jarArgs = @(
      "-Xmx$($ExecXmxGb)g",
      '-jar', (Join-Path $Bin $ExecJar),
      "--server.port=$port",
      # SHARED app name so the scheduler round-robins across every executor instance.
      '--spring.application.name=demo-executor',
      "--spring.config.additional-location=$execConf",
      "--cronsmith.client.server-urls=$urls",
      '--cronsmith.client.server-api-prefix=/cronsmith'
    )
    $p = Start-Process -FilePath 'java' -ArgumentList $jarArgs -WorkingDirectory $Here `
          -RedirectStandardOutput (Join-Path $Logs "executor-$i.log") `
          -RedirectStandardError  (Join-Path $Logs "executor-$i.err.log") `
          -PassThru -WindowStyle Hidden
    Save-Pid $p.Id "executor-$i"
    Write-Host "     executor $i  ->  :$port  (log: logs\executor-$i.log)"
  }
}

function Do-Up {
  Check-Prereqs
  Check-Capacity $n $e (Host-MemGb) 'host RAM'
  New-Item -ItemType Directory -Force -Path $RunDir, $Logs, $Data | Out-Null
  Build-Backend
  Stage-Jars
  Start-Schedulers $n
  Start-Frontend
  if ($e -gt 0) { Start-Executors $e $n }
  else { Write-Host ">> no -e given: executors NOT started (create tasks in the console; run executors later to run them)" }

  $names = if ($n -gt 1) { "scheduler-1..$n" } else { 'scheduler-1' }
  $names += ' | frontend'
  if ($e -eq 1)     { $names += ' | executor-1' }
  elseif ($e -gt 1) { $names += " | executor-1..$e" }

  Write-Host ''
  Write-Host 'cronsmith is up (local):'
  Write-Host "  console    : http://localhost:$FrontendPort"
  Write-Host "  scheduler  : http://localhost:$SchedBasePort/cronsmith/tasks   ($n node(s): seed + random-port followers, per-node H2 @ deploy\data)"
  if ($e -gt 0) { Write-Host "  executors  : $e node(s) on random ports $ExecPortLo-$ExecPortHi (shown above)" }
  Write-Host "  real DB?   : edit conf\scheduler.properties (MySQL/PostgreSQL) - default is per-node H2 files replicated by broadcast"
  Write-Host "  tail a log : .\run-local.ps1 logs <name>   (name: $names)"
  Write-Host "  stop all   : .\run-local.ps1 down"
}

function Do-Down {
  if (Test-Path $RunDir) {
    Get-ChildItem (Join-Path $RunDir '*.pid') -ErrorAction SilentlyContinue | ForEach-Object {
      $procId = (Get-Content $_ | Select-Object -First 1).Trim()
      if ($procId) { taskkill /PID $procId /T /F 2>$null | Out-Null; Write-Host ">> stopped $($_.BaseName) (pid $procId)" }
      Remove-Item $_ -Force
    }
  }
  Write-Host '>> all local cronsmith processes stopped'
}

switch ($Action) {
  'down' { Do-Down }
  'logs' { Get-Content -Wait -Tail 40 (Join-Path $Logs "$Svc.log") }
  'up'   { Do-Up }
  default { Do-Up }   # no/unknown action => up
}
