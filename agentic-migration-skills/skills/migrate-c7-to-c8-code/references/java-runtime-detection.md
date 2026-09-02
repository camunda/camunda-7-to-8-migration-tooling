# Cross-platform Java runtime discovery

Java discovery must not rely on `java` on `PATH` alone. A user can have a
compatible JDK installed while `PATH` points to an older JRE, an incompatible
JDK, or a launcher that is not registered with the operating system.

The skill includes two optional helpers for deterministic discovery:

- POSIX systems (macOS, Linux, and other Unix-like systems):
  `scripts/detect-java.sh`
- Windows PowerShell:
  `scripts/detect-java.ps1`

When the skill is installed without the helper scripts, follow the same
discovery and validation contract manually with the platform-appropriate
commands.

## Compatibility window

Pass the compatibility window required by the operation instead of assuming
that every Java operation has the same upper bound:

- OpenRewrite code migration: minimum major `21`, maximum major `23` for the
  current rewrite-maven-plugin and recipe line.
- Diagram Converter CLI: minimum major `21`; do not impose the OpenRewrite
  upper bound unless the selected CLI release documents one.

Examples:

```text
POSIX:      sh scripts/detect-java.sh --min-major 21 --max-major 23
PowerShell: & .\scripts\detect-java.ps1 -MinMajor 21 -MaxMajor 23
cmd.exe (from the skill directory):
            powershell.exe -NoProfile -File ".\scripts\detect-java.ps1" -MinMajor 21 -MaxMajor 23
```

Omit the maximum when the operation only has a minimum requirement.

## Discovery sources

Collect candidates from every source that exists on the current machine. A
source returning no candidates does not prove that Java is unavailable.

1. An explicitly set `JAVA_HOME` (and `JDK_HOME` when present). Check both
   `<home>/bin/java` and `<home>/bin/javac`; on Windows use `.exe` when
   present. Resolve the home before validating it.
2. The active runtime launcher:
   - POSIX: `command -v java`
   - Windows: `Get-Command java.exe` and `Get-Command java`
3. Operating-system locations:
   - macOS:
     `/usr/libexec/java_home -V 2>&1` and, where available,
     `/usr/libexec/java_home -v <major>` for every major in the requested
     window;
     `/Library/Java/JavaVirtualMachines/*/Contents/Home`.
   - Linux and other Unix-like systems:
     `/usr/lib/jvm/*`, `/usr/lib64/jvm/*`, `/usr/java/*`, `/opt/jdk*`, and
     paths returned by `update-alternatives --list java` when available.
   - Windows:
     `HKLM:\SOFTWARE\JavaSoft\JDK\*`,
     `HKLM:\SOFTWARE\JavaSoft\Java Runtime Environment\*`, the equivalent
     `HKCU` keys and `WOW6432Node` keys; `%ProgramFiles%\Java\*`,
     `%ProgramFiles(x86)%\Java\*`, and common vendor directories such as
     Eclipse Adoptium, Microsoft, Amazon Corretto, Azul Zulu, BellSoft,
     Oracle, and IBM Semeru.
4. Package-manager locations:
   - macOS Homebrew: query `brew --prefix openjdk`, every
     `openjdk@<major>` in the requested window, and both
     `/opt/homebrew/opt/openjdk@*/` and `/usr/local/opt/openjdk@*/`.
     Check both the formula prefix and
     `libexec/openjdk.jdk/Contents/Home`.
   - Windows package-manager roots such as Chocolatey and Scoop when they
     exist.
5. Version-manager and IDE locations:
   `~/.sdkman/candidates/java/*`, `~/.jenv/versions/*`,
   `~/.asdf/installs/java/*`, `~/.mise/installs/java/*`,
   `~/.local/share/mise/installs/java/*`, `~/.jabba/jdk/*`, and
   `~/.jdks/*`. Include the selected `current` or `default` symlink where the
   manager exposes one. Also check package-manager profiles such as
   `~/.nix-profile`.

Use the platform's path separator and quoting rules. Do not expand a
Windows path with POSIX glob syntax or execute a PowerShell path as a shell
command.

## Candidate validation

For each candidate:

1. Resolve symlinks and canonicalize the JDK home. Deduplicate candidates by
   the canonical home and Java executable path.
2. Require an executable `bin/java` (or `bin/java.exe`) and `bin/javac`.
   This excludes stale directories and JRE-only installations.
3. Execute that exact Java binary with `-version`, capturing stderr as well
   as stdout. Parse the actual major version:
   - `21.0.12` becomes `21`.
   - `1.8.0_402` becomes `8`.
   - Do not infer a version from a directory name such as `openjdk@21`.
4. Keep only candidates inside the requested compatibility window. Ignore
   launchers that fail, report an unparsable version, or point outside the
   window.

Prefer candidates in this order: a compatible `JAVA_HOME`, operating-system
discovery, package-manager JDK, version-manager/IDE JDK, then `PATH`. Within a
source, prefer the exact minimum major (Java 21 for the current operations),
then the lowest compatible major. If the explicit `JAVA_HOME` is invalid or
incompatible, continue with every other source instead of asking the user to
install Java immediately.

Record the selected values for the rest of the migration:

```text
JAVA_HOME=<canonical JDK home>
JAVA_CMD=<exact java executable>
JAVA_MAJOR=<actual major>
JAVA_SOURCE=<discovery source>
```

If no candidate is found, report the required window and the sources checked.
Only then ask the user whether to install a compatible JDK. After an approved
installation, run the complete discovery and validation process again.

## Scoped use

Use the selected executable and home only for the command that needs Java. Do
not edit shell profiles, create system-wide symlinks, require the optional
Homebrew link step, or replace the user's default Java.

For POSIX commands, use the recorded executable directly and put its JDK first
on `PATH`:

```text
JAVA_HOME="<detected home>" PATH="<detected home>/bin:$PATH" \
  "<detected java executable>" -Dfile.encoding=UTF-8 -jar <jar> ...
```

For PowerShell, set `$env:JAVA_HOME` and prepend `<home>\bin` to
`$env:Path`, then invoke the recorded path with the call operator (`&`).
Restore any temporary environment changes after the migration step.
