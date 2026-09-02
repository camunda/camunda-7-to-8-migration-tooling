[CmdletBinding()]
param(
    [ValidateRange(1, 100)]
    [int] $MinMajor = 21,
    [ValidateRange(0, 100)]
    [int] $MaxMajor = 0
)

Set-StrictMode -Version Latest

if ($MaxMajor -gt 0 -and $MaxMajor -lt $MinMajor) {
    throw "MaxMajor must be greater than or equal to MinMajor."
}

$candidates = @()

function Resolve-ExistingPath {
    param([string] $Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }

    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
    if ($null -eq $resolved) {
        return $null
    }

    return $resolved.Path
}

function Get-JavaMajor {
    param([string] $JavaPath)

    try {
        $output = (& $JavaPath -version 2>&1 | Out-String)
    } catch {
        return $null
    }

    $match = [regex]::Match(
        $output,
        '(?im)(?:version\s+["'']?|openjdk\s+["'']?|java\s+["'']?)(\d+)(?:\.(\d+))?'
    )
    if (-not $match.Success) {
        return $null
    }

    $major = [int] $match.Groups[1].Value
    if ($major -eq 1 -and $match.Groups[2].Success) {
        $major = [int] $match.Groups[2].Value
    }

    return $major
}

function Test-CompatibleMajor {
    param([int] $Major)

    if ($Major -lt $MinMajor) {
        return $false
    }
    if ($MaxMajor -gt 0 -and $Major -gt $MaxMajor) {
        return $false
    }
    return $true
}

function Add-JavaCandidate {
    param(
        [string] $Source,
        [int] $Priority,
        [string] $JavaPath,
        [string] $Home
    )

    $resolvedJava = Resolve-ExistingPath $JavaPath
    if ($null -eq $resolvedJava -or -not (Test-Path -LiteralPath $resolvedJava -PathType Leaf)) {
        return
    }

    $major = Get-JavaMajor $resolvedJava
    if ($null -eq $major -or -not (Test-CompatibleMajor $major)) {
        return
    }

    $resolvedHome = Resolve-ExistingPath $Home
    if ($null -eq $resolvedHome) {
        $javaBin = Split-Path -Parent $resolvedJava
        $resolvedHome = Resolve-ExistingPath (Join-Path $javaBin '..')
    }
    if ($null -eq $resolvedHome) {
        return
    }

    $javacPaths = @(
        (Join-Path $resolvedHome 'bin\javac.exe'),
        (Join-Path $resolvedHome 'bin\javac.cmd'),
        (Join-Path $resolvedHome 'bin\javac')
    )
    if (-not ($javacPaths | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })) {
        return
    }

    $script:candidates += [pscustomobject] @{
        Source  = $Source
        Priority = $Priority
        Major   = [int] $major
        Home    = $resolvedHome
        Command = $resolvedJava
    }
}

function Add-HomeCandidate {
    param(
        [string] $Source,
        [int] $Priority,
        [string] $Home
    )

    $resolvedHome = Resolve-ExistingPath $Home
    if ($null -eq $resolvedHome) {
        return
    }

    $javaPaths = @(
        (Join-Path $resolvedHome 'bin\java.exe'),
        (Join-Path $resolvedHome 'bin\java.cmd'),
        (Join-Path $resolvedHome 'bin\java')
    )
    $javaPath = $javaPaths |
        Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
        Select-Object -First 1
    if ($null -eq $javaPath) {
        return
    }
    Add-JavaCandidate $Source $Priority $javaPath $resolvedHome
}

function Add-HomeChildren {
    param(
        [string] $Source,
        [int] $Priority,
        [string] $Path
    )

    Get-ChildItem -Path $Path -Directory -Force -ErrorAction SilentlyContinue |
        ForEach-Object { Add-HomeCandidate $Source $Priority $_.FullName }
}

function Add-HomePattern {
    param(
        [string] $Source,
        [int] $Priority,
        [string] $Pattern
    )

    Get-ChildItem -Path $Pattern -Directory -Force -ErrorAction SilentlyContinue |
        ForEach-Object { Add-HomeCandidate $Source $Priority $_.FullName }
}

$javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME')
if (-not [string]::IsNullOrWhiteSpace($javaHome)) {
    Add-HomeCandidate 'JAVA_HOME' 0 $javaHome
}

$jdkHome = [Environment]::GetEnvironmentVariable('JDK_HOME')
if (-not [string]::IsNullOrWhiteSpace($jdkHome) -and $jdkHome -ne $javaHome) {
    Add-HomeCandidate 'JDK_HOME' 1 $jdkHome
}

$registryRoots = @(
    'HKLM:\SOFTWARE\JavaSoft\JDK',
    'HKLM:\SOFTWARE\JavaSoft\Java Runtime Environment',
    'HKLM:\SOFTWARE\WOW6432Node\JavaSoft\JDK',
    'HKLM:\SOFTWARE\WOW6432Node\JavaSoft\Java Runtime Environment',
    'HKCU:\Software\JavaSoft\JDK',
    'HKCU:\Software\JavaSoft\Java Runtime Environment'
)
foreach ($registryRoot in $registryRoots) {
    if (-not (Test-Path -LiteralPath $registryRoot)) {
        continue
    }

    Get-ChildItem -LiteralPath $registryRoot -ErrorAction SilentlyContinue |
        ForEach-Object {
            $properties = Get-ItemProperty -LiteralPath $_.PSPath -ErrorAction SilentlyContinue
            if ($null -ne $properties -and
                $properties.PSObject.Properties.Name -contains 'JavaHome') {
                Add-HomeCandidate 'WINDOWS_REGISTRY' 10 $properties.JavaHome
            }
        }
}

$programFilesRoots = @(
    [Environment]::GetEnvironmentVariable('ProgramFiles'),
    [Environment]::GetEnvironmentVariable('ProgramFiles(x86)')
) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique

foreach ($programFiles in $programFilesRoots) {
    Add-HomePattern 'WINDOWS_PROGRAM_FILES' 10 (Join-Path $programFiles 'Java\*')
    Add-HomePattern 'WINDOWS_PROGRAM_FILES' 10 (Join-Path $programFiles 'OpenJDK\*')
    Add-HomePattern 'WINDOWS_PROGRAM_FILES' 10 (Join-Path $programFiles 'AdoptOpenJDK\*')
    Add-HomePattern 'WINDOWS_PROGRAM_FILES' 10 (Join-Path $programFiles 'Eclipse Adoptium\jdk*')
    Add-HomePattern 'WINDOWS_PROGRAM_FILES' 10 (Join-Path $programFiles 'Microsoft\jdk-*')
    Add-HomePattern 'WINDOWS_PROGRAM_FILES' 10 (Join-Path $programFiles 'Amazon Corretto\jdk*')
    Add-HomePattern 'WINDOWS_PROGRAM_FILES' 10 (Join-Path $programFiles 'Zulu\zulu-*')
    Add-HomePattern 'WINDOWS_PROGRAM_FILES' 10 (Join-Path $programFiles 'BellSoft\LibericaJDK*\*')
    Add-HomePattern 'WINDOWS_PROGRAM_FILES' 10 (Join-Path $programFiles 'Semeru\*')
}

$chocolateyRoot = [Environment]::GetEnvironmentVariable('ChocolateyInstall')
if (-not [string]::IsNullOrWhiteSpace($chocolateyRoot)) {
    Add-HomePattern 'PACKAGE_MANAGER' 20 (Join-Path $chocolateyRoot 'lib\*jdk*\tools')
}

$userProfile = [Environment]::GetFolderPath('UserProfile')
if (-not [string]::IsNullOrWhiteSpace($userProfile)) {
    Add-HomePattern 'PACKAGE_MANAGER' 20 (Join-Path $userProfile 'scoop\apps\*\current')
    Add-HomeChildren 'VERSION_MANAGER' 30 (Join-Path $userProfile '.sdkman\candidates\java')
    Add-HomeChildren 'VERSION_MANAGER' 30 (Join-Path $userProfile '.jenv\versions')
    Add-HomeChildren 'VERSION_MANAGER' 30 (Join-Path $userProfile '.asdf\installs\java')
    Add-HomeChildren 'VERSION_MANAGER' 30 (Join-Path $userProfile '.mise\installs\java')
    Add-HomeChildren 'VERSION_MANAGER' 30 (Join-Path $userProfile '.local\share\mise\installs\java')
    Add-HomeChildren 'VERSION_MANAGER' 30 (Join-Path $userProfile '.jabba\jdk')
    Add-HomeChildren 'IDE' 30 (Join-Path $userProfile '.jdks')
    Add-HomeCandidate 'PACKAGE_MANAGER' 20 (Join-Path $userProfile '.nix-profile')
}

$pathJava = Get-Command java.exe -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -eq $pathJava) {
    $pathJava = Get-Command java -ErrorAction SilentlyContinue | Select-Object -First 1
}
if ($null -ne $pathJava) {
    $pathJavaPath = $null
    if ($pathJava.PSObject.Properties.Name -contains 'Path') {
        $pathJavaPath = $pathJava.Path
    }
    if ([string]::IsNullOrWhiteSpace($pathJavaPath)) {
        if ($pathJava.PSObject.Properties.Name -contains 'Source') {
            $pathJavaPath = $pathJava.Source
        }
    }
    Add-JavaCandidate 'PATH' 50 $pathJavaPath $null
}

$sortProperties = @(
    'Priority'
    @{ Expression = { if ($_.Major -eq $MinMajor) { 0 } else { 1 } } }
    'Major'
    'Home'
)
$uniqueCandidates = $candidates |
    Group-Object -Property Home, Command |
    ForEach-Object { $_.Group | Sort-Object -Property Priority | Select-Object -First 1 }
$selected = $uniqueCandidates |
    Sort-Object -Property $sortProperties |
    Select-Object -First 1

if ($null -eq $selected) {
    Write-Output 'JAVA_FOUND=false'
    Write-Output 'JAVA_HOME='
    Write-Output 'JAVA_CMD='
    Write-Output 'JAVA_MAJOR='
    Write-Output 'JAVA_SOURCE='
    $window = "$MinMajor+"
    if ($MaxMajor -gt 0) {
        $window = "$MinMajor-$MaxMajor"
    }
    [Console]::Error.WriteLine(
        "No compatible JDK found for Java $window. Checked JAVA_HOME, Windows installation and registry locations, package managers, version managers, and PATH."
    )
    exit 1
}

Write-Output 'JAVA_FOUND=true'
Write-Output "JAVA_HOME=$($selected.Home)"
Write-Output "JAVA_CMD=$($selected.Command)"
Write-Output "JAVA_MAJOR=$($selected.Major)"
Write-Output "JAVA_SOURCE=$($selected.Source)"
