$ErrorActionPreference = 'Stop'

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$detector = Join-Path $scriptDirectory 'detect-java.ps1'
$testRoot = Join-Path ([IO.Path]::GetTempPath()) "camunda-java-detect-test-$([guid]::NewGuid())"
$originalJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME')
$originalProgramFiles = [Environment]::GetEnvironmentVariable('ProgramFiles')
$originalProgramFilesX86 = [Environment]::GetEnvironmentVariable('ProgramFiles(x86)')

function New-FakeJdk {
    param(
        [string] $JdkHome,
        [string] $Version
    )

    $bin = Join-Path $JdkHome 'bin'
    New-Item -ItemType Directory -Path $bin -Force | Out-Null
    $java = Join-Path $bin 'java.cmd'
    $javac = Join-Path $bin 'javac.cmd'
    [IO.File]::WriteAllText(
        $java,
        "@echo off`r`necho openjdk version `"$Version`" 1>&2`r`n",
        [Text.Encoding]::ASCII
    )
    [IO.File]::WriteAllText($javac, "@echo off`r`n", [Text.Encoding]::ASCII)
}

function Assert-OutputContains {
    param(
        [string[]] $Output,
        [string] $Expected
    )

    if ($Output -notcontains $Expected) {
        throw "Expected output to contain '$Expected'. Actual output: $($Output -join "`n")"
    }
}

$isWindows = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
$isWindowsVariable = Get-Variable -Name IsWindows -ValueOnly -ErrorAction SilentlyContinue
if ($null -ne $isWindowsVariable) {
    $isWindows = [bool] $isWindowsVariable
}
if (-not $isWindows) {
    Write-Output 'Skipping detect-java.ps1 tests on non-Windows platforms'
    exit 0
}

try {
    New-FakeJdk (Join-Path $testRoot 'jdk-21') '21.0.12'
    $env:JAVA_HOME = Join-Path $testRoot 'jdk-21'
    $result = & $detector -MinMajor 21 -MaxMajor 23
    if ($LASTEXITCODE -ne 0) {
        throw "Expected a Java 21 JDK to be discovered."
    }
    Assert-OutputContains $result 'JAVA_FOUND=true'
    Assert-OutputContains $result 'JAVA_MAJOR=21'
    Assert-OutputContains $result 'JAVA_SOURCE=JAVA_HOME'

    New-FakeJdk (Join-Path $testRoot 'jdk-24') '24.0.1'
    $env:JAVA_HOME = Join-Path $testRoot 'jdk-24'
    $result = & $detector -MinMajor 24 -MaxMajor 24
    if ($LASTEXITCODE -ne 0) {
        throw "Expected a Java 24 JDK to be discovered."
    }
    Assert-OutputContains $result 'JAVA_MAJOR=24'

    New-FakeJdk (Join-Path $testRoot 'jdk-8') '1.8.0_402'
    $env:JAVA_HOME = Join-Path $testRoot 'jdk-8'
    $result = & $detector -MinMajor 99 2>$null
    if ($LASTEXITCODE -eq 0) {
        throw "Expected discovery to fail when no compatible JDK exists."
    }
    Assert-OutputContains $result 'JAVA_FOUND=false'

    Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    $env:ProgramFiles = Join-Path $testRoot 'Program Files'
    Remove-Item 'Env:ProgramFiles(x86)' -ErrorAction SilentlyContinue
    New-FakeJdk (Join-Path $env:ProgramFiles 'BellSoft\LibericaJDK-41') '41.0.2'
    $result = & $detector -MinMajor 41 -MaxMajor 41
    if ($LASTEXITCODE -ne 0) {
        throw "Expected a BellSoft Liberica JDK under Program Files to be discovered."
    }
    Assert-OutputContains $result 'JAVA_FOUND=true'
    Assert-OutputContains $result 'JAVA_MAJOR=41'
    Assert-OutputContains $result 'JAVA_SOURCE=WINDOWS_PROGRAM_FILES'

    Write-Output 'detect-java.ps1 tests passed'
} finally {
    if ($null -eq $originalJavaHome) {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_HOME = $originalJavaHome
    }
    if ($null -eq $originalProgramFiles) {
        Remove-Item Env:ProgramFiles -ErrorAction SilentlyContinue
    } else {
        $env:ProgramFiles = $originalProgramFiles
    }
    if ($null -eq $originalProgramFilesX86) {
        Remove-Item 'Env:ProgramFiles(x86)' -ErrorAction SilentlyContinue
    } else {
        ${env:ProgramFiles(x86)} = $originalProgramFilesX86
    }
    Remove-Item -LiteralPath $testRoot -Recurse -Force -ErrorAction SilentlyContinue
}
