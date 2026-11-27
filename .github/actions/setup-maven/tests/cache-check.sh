#!/usr/bin/env bash
set -euo pipefail

action_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
action_file="$action_dir/action.yml"

cache_check_script=$(ruby -ryaml - "$action_file" <<'RUBY'
steps = YAML.load_file(ARGV.fetch(0)).fetch("runs").fetch("steps")
cache_check = steps.find { |step| step["id"] == "check-maven-cache" }
preflight = steps.find { |step| step["name"] == "Verify Maven distribution availability" }
setup = steps.find { |step| step["uses"] == "stCarolas/setup-maven@v5" }

abort "missing Maven tool-cache check step" unless cache_check
abort "missing Maven distribution preflight" unless preflight
abort "missing Maven setup step" unless setup

expected_cache_check_if = "steps.check-maven.outputs.matches != 'true'"
expected_preflight_if = "steps.check-maven.outputs.matches != 'true' && steps.check-maven-cache.outputs.hit != 'true'"
expected_setup_if = "steps.check-maven.outputs.matches != 'true'"

abort "Maven tool-cache check must run only when PATH Maven does not match" unless cache_check["if"] == expected_cache_check_if
abort "Maven preflight must skip when the requested version is cached" unless preflight["if"] == expected_preflight_if
abort "Maven setup must still run when PATH Maven does not match" unless setup["if"] == expected_setup_if
abort "Maven tool-cache check must precede the preflight and installer" unless [steps.index(cache_check), steps.index(preflight), steps.index(setup)] == [steps.index(cache_check), steps.index(preflight), steps.index(setup)].sort

print cache_check.fetch("run")
RUBY
)

test_dir="$(mktemp -d "$action_dir/tests/.cache-check.XXXXXX")"
trap 'rm -rf "$test_dir"' EXIT

run_case() {
  local name="$1"
  local requested_version="$2"
  local runner_arch="$3"
  local cached_version="$4"
  local cached_arch="$5"
  local complete="$6"
  local expected="$7"
  local runner_os="${8:-Linux}"
  local cache_root="$test_dir/$name/tool-cache"
  local output="$test_dir/$name/output"
  local path_prefix="$PATH"

  mkdir -p "$test_dir/$name"
  if [[ "$cached_version" != "-" ]]; then
    mkdir -p "$cache_root/maven/$cached_version/$cached_arch"
    if [[ "$complete" == "true" ]]; then
      : > "$cache_root/maven/$cached_version/$cached_arch.complete"
    fi
  fi

  if [[ "$runner_os" == "Windows" ]]; then
    local fake_bin="$test_dir/$name/bin"
    mkdir -p "$fake_bin"
    cat > "$fake_bin/cygpath" <<'CYGPATH'
#!/usr/bin/env bash
[[ "$1" == "-u" ]] || exit 2
printf '%s\n' "$2"
CYGPATH
    chmod +x "$fake_bin/cygpath"
    path_prefix="$fake_bin:$PATH"
  fi

  RUNNER_TOOL_CACHE="$cache_root" \
    RUNNER_ARCH="$runner_arch" \
    RUNNER_OS="$runner_os" \
    MAVEN_VERSION="$requested_version" \
    GITHUB_OUTPUT="$output" \
    PATH="$path_prefix" \
    bash -e -o pipefail -c "$cache_check_script"

  local actual
  actual="$(cat "$output")"
  if [[ "$actual" != "hit=$expected" ]]; then
    printf 'FAIL %s: expected hit=%s, got %s\n' "$name" "$expected" "$actual" >&2
    return 1
  fi
  printf 'PASS %s\n' "$name"
}

run_case "x64-cache-hit" "3.9.16" "X64" "3.9.16" "x64" true true
run_case "arm64-cache-hit" "3.9.16" "ARM64" "3.9.16" "arm64" true true
run_case "windows-cache-hit" "3.9.16" "X64" "3.9.16" "x64" true true Windows
run_case "incomplete-cache-entry" "3.9.16" "X64" "3.9.16" "x64" false false
run_case "different-cached-version" "3.9.16" "X64" "3.9.15" "x64" true false
run_case "different-cached-architecture" "3.9.16" "ARM64" "3.9.16" "x64" true false
run_case "missing-tool-cache" "3.9.16" "X64" "-" "-" false false
