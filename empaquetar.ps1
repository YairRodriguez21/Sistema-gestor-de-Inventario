# Empaqueta SGIA en una carpeta que se puede copiar a otra computadora.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File .\empaquetar.ps1
#
# Sale en target\dist:
#   SGIA.jar   el programa
#   lib\       las 84 librerias
#   SGIA.bat   el doble clic que lo arranca
#
# En la otra computadora solo hace falta tener Java 17 o superior.
# NO se usa jlink porque el proyecto no es un modulo Java (no tiene
# module-info.java) y jlink lo exige; tampoco el jar con todo dentro,
# porque ese arranca la API y no la ventana.

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $raiz

Write-Output "[1/4] Compilando..."
& .\mvnw.cmd -B clean package -DskipTests "-Dspring-boot.repackage.skip=true" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Fallo la compilacion" }

Write-Output "[2/4] Copiando las librerias..."
if (Test-Path ".\target\dist") { Remove-Item ".\target\dist" -Recurse -Force }
& .\mvnw.cmd -B dependency:copy-dependencies "-DoutputDirectory=target\dist\lib" "-DincludeScope=runtime" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Fallo al copiar las librerias" }

Write-Output "[3/4] Armando la carpeta..."
Copy-Item ".\target\inventario-0.0.1-SNAPSHOT.jar" ".\target\dist\SGIA.jar" -Force

$bat = @(
    '@echo off',
    'chcp 65001 >nul',
    'title SGIA - Sistema de Gestion de Inventario',
    '',
    'REM La base de datos se toma de aqui si no se dice otra cosa.',
    'REM Para apuntar a otro servidor, paselo como argumento, por ejemplo:',
    'REM   SGIA.bat --spring.datasource.url=jdbc:mysql://192.168.1.10:3306/sapam_inventario --spring.datasource.username=sgia --spring.datasource.password=clave',
    '',
    'where java >nul 2>nul',
    'if errorlevel 1 (',
    '  echo No se encontro Java en esta computadora.',
    '  echo Instale un JDK 17 o superior y vuelva a intentarlo.',
    '  pause',
    '  exit /b 1',
    ')',
    '',
    'set "URL=jdbc:mysql://localhost:3306/sapam_inventario?useSSL=false&serverTimezone=UTC"',
    'if not "%~1"=="" goto conargs',
    'start "SGIA" javaw -cp "SGIA.jar;lib\*" com.sapam.inventario.javafx.Lanzador --spring.datasource.url=%URL%',
    'exit /b 0',
    ':conargs',
    'start "SGIA" javaw -cp "SGIA.jar;lib\*" com.sapam.inventario.javafx.Lanzador %*',
    'exit /b 0'
)
Set-Content -Path ".\target\dist\SGIA.bat" -Value $bat -Encoding ASCII

Write-Output "[4/4] Listo."
$tam = (Get-ChildItem ".\target\dist" -Recurse -File | Measure-Object -Property Length -Sum).Sum
Write-Output ""
Write-Output "Carpeta:  $raiz\target\dist"
Write-Output "Tamano:   $([math]::Round($tam/1MB,1)) MB"
Write-Output "Libs:     $((Get-ChildItem '.\target\dist\lib' -Filter *.jar).Count) jars"
Write-Output ""
Write-Output "Copie esa carpeta entera a la otra computadora y doble clic en SGIA.bat"
