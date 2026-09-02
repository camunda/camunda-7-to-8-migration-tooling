#!/bin/sh

set -u

MIN_MAJOR=21
MAX_MAJOR=

usage() {
  cat >&2 <<'EOF'
Usage: detect-java.sh [--min-major N] [--max-major N]

Prints JAVA_FOUND, JAVA_HOME, JAVA_CMD, JAVA_MAJOR, and JAVA_SOURCE.
EOF
}

fail_usage() {
  usage
  exit 2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --min-major)
      [ "$#" -ge 2 ] || fail_usage
      MIN_MAJOR=$2
      shift 2
      ;;
    --min-major=*)
      MIN_MAJOR=${1#*=}
      shift
      ;;
    --max-major)
      [ "$#" -ge 2 ] || fail_usage
      MAX_MAJOR=$2
      shift 2
      ;;
    --max-major=*)
      MAX_MAJOR=${1#*=}
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      fail_usage
      ;;
  esac
done

case "$MIN_MAJOR" in
  ''|*[!0-9]*) fail_usage ;;
esac

if [ -n "$MAX_MAJOR" ]; then
  case "$MAX_MAJOR" in
    ''|*[!0-9]*) fail_usage ;;
  esac
  [ "$MAX_MAJOR" -ge "$MIN_MAJOR" ] || fail_usage
fi

records=$(mktemp "${TMPDIR:-/tmp}/camunda-java-detect.XXXXXX") || exit 2
unique_records=$(mktemp "${TMPDIR:-/tmp}/camunda-java-detect-unique.XXXXXX") || {
  rm -f "$records"
  exit 2
}
trap 'rm -f "$records" "$unique_records"' EXIT HUP INT TERM

parse_major() {
  printf '%s\n' "$1" | awk '
    /version|openjdk|java/ {
      for (i = 1; i <= NF; i++) {
        token = $i
        gsub(/"/, "", token)
        if (token ~ /^[0-9]+([.][0-9]+)*/) {
          count = split(token, parts, /[._-]/)
          major = parts[1]
          if (major == 1 && count > 1) {
            major = parts[2]
          }
          print major
          exit
        }
      }
    }
  '
}

is_compatible() {
  compat_major=$1
  case "$compat_major" in
    ''|*[!0-9]*) return 1 ;;
  esac

  [ "$compat_major" -ge "$MIN_MAJOR" ] || return 1
  if [ -n "$MAX_MAJOR" ] && [ "$compat_major" -gt "$MAX_MAJOR" ]; then
    return 1
  fi
  return 0
}

canonical_home() {
  (cd "$1" 2>/dev/null && pwd -P) || return 1
}

canonical_command() {
  canonical_command_path=$1
  case "$canonical_command_path" in
    /*) ;;
    *) canonical_command_path=$(command -v "$canonical_command_path" 2>/dev/null || printf '%s' "$canonical_command_path") ;;
  esac
  [ -x "$canonical_command_path" ] || return 1

  if command -v readlink >/dev/null 2>&1; then
    canonical_link_depth=0
    while [ -L "$canonical_command_path" ]; do
      [ "$canonical_link_depth" -lt 32 ] || return 1
      canonical_link_target=$(readlink "$canonical_command_path") || break
      case "$canonical_link_target" in
        /*) canonical_command_path=$canonical_link_target ;;
        *) canonical_command_path=$(dirname "$canonical_command_path")/$canonical_link_target ;;
      esac
      canonical_link_depth=$((canonical_link_depth + 1))
    done
  fi

  canonical_command_dir=$(dirname "$canonical_command_path")
  canonical_command_home=$(canonical_home "$canonical_command_dir/..") || return 1
  printf '%s\n' "$canonical_command_home/bin/$(basename "$canonical_command_path")"
}

add_java_candidate() {
  candidate_source=$1
  candidate_priority=$2
  candidate_java_path=$3
  candidate_home=$4

  [ -n "$candidate_java_path" ] || return
  [ -x "$candidate_java_path" ] || return

  candidate_output=$("$candidate_java_path" -version 2>&1) || return
  candidate_major=$(parse_major "$candidate_output")
  is_compatible "$candidate_major" || return

  if [ -n "$candidate_home" ]; then
    candidate_home=$(canonical_home "$candidate_home") || return
  fi

  printf '%s\t%s\t%s\t%s\t%s\n' \
    "$candidate_priority" "$candidate_major" "$candidate_source" "$candidate_home" "$candidate_java_path" >>"$records"
}

add_home_candidate() {
  home_source=$1
  home_priority=$2
  home_path=$3

  [ -d "$home_path" ] || return
  home_path=$(canonical_home "$home_path") || return
  home_java_path=$home_path/bin/java
  home_javac_path=$home_path/bin/javac
  [ -x "$home_java_path" ] || return
  [ -x "$home_javac_path" ] || return
  add_java_candidate "$home_source" "$home_priority" "$home_java_path" "$home_path"
}

add_java_from_path() {
  path_source=$1
  path_priority=$2
  path_java=$3
  [ -n "$path_java" ] || return

  path_resolved_command=$(canonical_command "$path_java" 2>/dev/null || true)
  if [ -n "$path_resolved_command" ]; then
    path_resolved_home=$(canonical_home "$(dirname "$path_resolved_command")/.." 2>/dev/null || true)
    if [ -n "$path_resolved_home" ] && [ -x "$path_resolved_home/bin/javac" ]; then
      add_java_candidate "$path_source" "$path_priority" "$path_resolved_home/bin/java" "$path_resolved_home"
      return
    fi
  fi
}

# The explicit environment is authoritative only after its executable and
# version have been validated.
if [ -n "${JAVA_HOME:-}" ]; then
  add_home_candidate "JAVA_HOME" 0 "$JAVA_HOME"
fi
if [ -n "${JDK_HOME:-}" ] && [ "${JDK_HOME:-}" != "${JAVA_HOME:-}" ]; then
  add_home_candidate "JDK_HOME" 1 "$JDK_HOME"
fi

os_name=$(uname -s 2>/dev/null || printf 'unknown')

if [ "$os_name" = "Darwin" ]; then
  if [ -x /usr/libexec/java_home ]; then
    registered_homes=$(/usr/libexec/java_home -V 2>&1 || true)
    printf '%s\n' "$registered_homes" |
      sed -n 's/.*\(\/.*\/Contents\/Home\)$/\1/p' |
      while IFS= read -r mac_home; do
        add_home_candidate "MACOS_JAVA_HOME" 10 "$mac_home"
      done

    if [ -n "$MAX_MAJOR" ]; then
      major=$MIN_MAJOR
      while [ "$major" -le "$MAX_MAJOR" ]; do
        mac_home=$(/usr/libexec/java_home -v "$major" 2>/dev/null || true)
        add_home_candidate "MACOS_JAVA_HOME" 10 "$mac_home"
        major=$((major + 1))
      done
    else
      mac_home=$(/usr/libexec/java_home -v "$MIN_MAJOR" 2>/dev/null || true)
      add_home_candidate "MACOS_JAVA_HOME" 10 "$mac_home"
    fi
  fi

  for home in /Library/Java/JavaVirtualMachines/*/Contents/Home; do
    add_home_candidate "MACOS_STANDARD" 11 "$home"
  done
fi

if [ "$os_name" != "Darwin" ]; then
  for root in /usr/lib/jvm /usr/lib64/jvm /usr/java /opt/jdk /opt/java; do
    for home in "$root"/*; do
      add_home_candidate "SYSTEM_JDK" 10 "$home"
    done
  done
  for home in /opt/jdk* /opt/java* /usr/local/jdk* /usr/local/java*; do
    add_home_candidate "SYSTEM_JDK" 10 "$home"
  done

  if command -v update-alternatives >/dev/null 2>&1; then
    update-alternatives --list java 2>/dev/null |
      while IFS= read -r java_path; do
        add_java_from_path "SYSTEM_ALTERNATIVES" 11 "$java_path"
      done
  fi
fi

if command -v brew >/dev/null 2>&1; then
  for formula in openjdk openjdk@"$MIN_MAJOR"; do
    brew_home=$(brew --prefix "$formula" 2>/dev/null || true)
    add_home_candidate "HOMEBREW" 20 "$brew_home"
    add_home_candidate "HOMEBREW" 20 "$brew_home/libexec/openjdk.jdk/Contents/Home"
  done
  if [ -n "$MAX_MAJOR" ]; then
    major=$MIN_MAJOR
    while [ "$major" -le "$MAX_MAJOR" ]; do
      brew_home=$(brew --prefix "openjdk@$major" 2>/dev/null || true)
      add_home_candidate "HOMEBREW" 20 "$brew_home"
      add_home_candidate "HOMEBREW" 20 "$brew_home/libexec/openjdk.jdk/Contents/Home"
      major=$((major + 1))
    done
  fi
fi

for root in /opt/homebrew/opt /usr/local/opt; do
  for formula_home in "$root"/openjdk*; do
    add_home_candidate "HOMEBREW" 20 "$formula_home"
    add_home_candidate "HOMEBREW" 20 "$formula_home/libexec/openjdk.jdk/Contents/Home"
  done
done

user_home=${HOME:-}
if [ -n "$user_home" ]; then
  for root in \
    "$user_home/.sdkman/candidates/java" \
    "$user_home/.jenv/versions" \
    "$user_home/.asdf/installs/java" \
    "$user_home/.mise/installs/java" \
    "$user_home/.local/share/mise/installs/java" \
    "$user_home/.jabba/jdk" \
    "$user_home/.jdks"; do
    for home in "$root"/*; do
      add_home_candidate "VERSION_MANAGER" 30 "$home"
    done
  done
  add_home_candidate "PACKAGE_MANAGER" 20 "$user_home/.nix-profile"
fi

path_java=$(command -v java 2>/dev/null || true)
add_java_from_path "PATH" 50 "$path_java"

best_priority=
best_version=
best_preferred=
best_source=
best_home=
best_command=

awk -F '\t' '!seen[$4 SUBSEP $5]++' "$records" >"$unique_records"
tab=$(printf '\t')
while IFS="$tab" read -r priority version source home java_path; do
  [ -n "$priority" ] || continue
  preferred=1
  [ "$version" -eq "$MIN_MAJOR" ] && preferred=0

  if [ -z "$best_priority" ] ||
    [ "$priority" -lt "$best_priority" ] ||
    {
      [ "$priority" -eq "$best_priority" ] &&
      {
        [ "$preferred" -lt "$best_preferred" ] ||
        { [ "$preferred" -eq "$best_preferred" ] && [ "$version" -lt "$best_version" ]; }
      }
    }; then
    best_priority=$priority
    best_version=$version
    best_preferred=$preferred
    best_source=$source
    best_home=$home
    best_command=$java_path
  fi
done <"$unique_records"

if [ -z "$best_command" ]; then
  printf 'JAVA_FOUND=false\nJAVA_HOME=\nJAVA_CMD=\nJAVA_MAJOR=\nJAVA_SOURCE=\n'
  {
    printf 'No compatible JDK found for Java %s' "$MIN_MAJOR"
    [ -n "$MAX_MAJOR" ] && printf '%s' "-$MAX_MAJOR"
    printf '. Checked JAVA_HOME, operating-system locations, package managers, version managers, and PATH.\n'
  } >&2
  exit 1
fi

printf 'JAVA_FOUND=true\nJAVA_HOME=%s\nJAVA_CMD=%s\nJAVA_MAJOR=%s\nJAVA_SOURCE=%s\n' \
  "$best_home" "$best_command" "$best_version" "$best_source"
