# Script de Automatización de Benchmark para SITM-MIO
# Uso: .\scripts\benchmark.ps1 -DatasetPath "ruta/al/archivo.csv" -NumWorkers 4

param (
    [string]$DatasetPath = "app/src/main/resources/data/datagrams_1M.csv",
    [int]$NumWorkers = 1
)

$ResultsFile = "benchmark_results.csv"
$ServerPort = 10000
$BaseWorkerPort = 10001

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "INICIANDO BENCHMARK SITM-MIO" -ForegroundColor Cyan
Write-Host "Dataset: $DatasetPath" -ForegroundColor Yellow
Write-Host "Workers: $NumWorkers" -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Matar procesos anteriores (Java)
Write-Host "Limpiando procesos anteriores..." -ForegroundColor Gray
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

# 2. Iniciar Servidor (Master)
Write-Host "Iniciando Servidor Master..." -ForegroundColor Green
$ServerProcess = Start-Process -FilePath "./gradlew" -ArgumentList "runServer" -NoNewWindow -PassThru
Start-Sleep -Seconds 15 # Esperar a que el servidor inicie

# 3. Iniciar Workers
for ($i = 0; $i -lt $NumWorkers; $i++) {
    $Port = $BaseWorkerPort + $i
    Write-Host "Iniciando Worker $i en puerto $Port..." -ForegroundColor Green
    # Usamos Start-Process para correr en paralelo
    # Corregido el quoting: "-Pargs=`"--port $Port`"" para que Gradle reciba la propiedad completa
    Start-Process -FilePath "./gradlew" -ArgumentList "runWorker", "-Pargs=`"--port $Port`"" -NoNewWindow
    Start-Sleep -Seconds 5 # Esperar a que cada worker inicie y se registre
}

Start-Sleep -Seconds 5 # Esperar estabilización

# 4. Ejecutar Benchmark Client
Write-Host "Ejecutando Cliente de Benchmark..." -ForegroundColor Magenta
# El cliente escribirá en benchmark_results.csv
# Pasamos argumentos: <DatasetPath> <ResultsFile>
# Nota: Usamos cmd /c para ver la salida en la misma consola o Start-Process -Wait
$BenchmarkCommand = "./gradlew runBenchmark -Pargs=`"$DatasetPath $ResultsFile`""
Invoke-Expression $BenchmarkCommand

# 5. Limpieza
Write-Host "Benchmark finalizado. Limpiando procesos..." -ForegroundColor Cyan
Stop-Process -Id $ServerProcess.Id -ErrorAction SilentlyContinue
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "PRUEBA COMPLETADA" -ForegroundColor Cyan
Write-Host "Resultados guardados en: $ResultsFile" -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Cyan
