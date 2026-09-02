$ErrorActionPreference = 'Stop'

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$detector = Join-Path $scriptDirectory 'detect-java.ps1'
$testRoot = Join-Path ([IO.Path]::GetTempPath()) "camunda-java-detect-test-$([guid]::NewGuid())"
$originalJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME')

function New-FakeJdk {
    param(
        [string] $Home,
        [string] $Version
    )

    $bin = Join-Path $Home 'bin'
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

    Write-Output 'detect-java.ps1 tests passed'
} finally {
    if ($null -eq $originalJavaHome) {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_HOME = $originalJavaHome
    }
    Remove-Item -LiteralPath $testRoot -Recurse -Force -ErrorAction SilentlyContinue
}
