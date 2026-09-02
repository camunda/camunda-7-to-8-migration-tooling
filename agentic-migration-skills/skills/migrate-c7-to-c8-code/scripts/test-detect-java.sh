#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd -P)
detector=$script_dir/detect-java.sh
test_root=$(mktemp -d "${TMPDIR:-/tmp}/camunda-java-detect-test.XXXXXX")
trap 'rm -rf "$test_root"' EXIT HUP INT TERM

create_fake_jdk() {
  fake_home=$1
  fake_version=$2
  mkdir -p "$fake_home/bin"
  printf '#!/bin/sh\nprintf "openjdk version \\"%s\\"\\n" >&2\n' "$fake_version" >"$fake_home/bin/java"
  printf '#!/bin/sh\nexit 0\n' >"$fake_home/bin/javac"
  chmod +x "$fake_home/bin/java" "$fake_home/bin/javac"
}

assert_contains() {
  value=$1
  expected=$2
  case "$value" in
    *"$expected"*) ;;
    *)
      printf 'Expected output to contain: %s\nActual output:\n%s\n' "$expected" "$value" >&2
      exit 1
      ;;
  esac
}

create_fake_jdk "$test_root/jdk-21" "21.0.12"
result=$(
  JAVA_HOME="$test_root/jdk-21" \
  HOME="$test_root/home" \
  PATH="/usr/bin:/bin" \
  "$detector" --min-major 21 --max-major 23
)
assert_contains "$result" "JAVA_FOUND=true"
assert_contains "$result" "JAVA_MAJOR=21"
assert_contains "$result" "JAVA_SOURCE=JAVA_HOME"

create_fake_jdk "$test_root/jdk-24" "24.0.1"
result=$(
  JAVA_HOME="$test_root/jdk-24" \
  HOME="$test_root/home" \
  PATH="/usr/bin:/bin" \
  "$detector" --min-major 24 --max-major 24
)
assert_contains "$result" "JAVA_FOUND=true"
assert_contains "$result" "JAVA_MAJOR=24"

create_fake_jdk "$test_root/jdk-8" "1.8.0_402"
result=$(
  JAVA_HOME="$test_root/jdk-8" \
  HOME="$test_root/home" \
  PATH="$test_root/jdk-24/bin:/usr/bin:/bin" \
  "$detector" --min-major 24 --max-major 24
)
assert_contains "$result" "JAVA_MAJOR=24"
assert_contains "$result" "JAVA_SOURCE=PATH"

result=$(
  JAVA_HOME="$test_root/jdk-8" \
  HOME="$test_root/home" \
  PATH="/usr/bin:/bin" \
  "$detector" --min-major 8 --max-major 8
)
assert_contains "$result" "JAVA_FOUND=true"
assert_contains "$result" "JAVA_MAJOR=8"

set +e
result=$(
  JAVA_HOME="$test_root/jdk-8" \
  HOME="$test_root/home" \
  PATH="/usr/bin:/bin" \
  "$detector" --min-major 99 2>"$test_root/no-compatible.err"
)
status=$?
set -e
[ "$status" -ne 0 ] || {
  printf 'Expected discovery to fail when no compatible JDK exists.\n' >&2
  exit 1
}
assert_contains "$result" "JAVA_FOUND=false"

printf 'detect-java.sh tests passed\n'
