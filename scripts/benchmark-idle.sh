#!/usr/bin/env bash
set -euo pipefail

worktrees=10
runs=5
seconds=60
warmup=20
profile=BALANCED
measure_status=true
measure_app=true

usage() {
    echo "usage: $0 [--worktrees N] [--runs K] [--seconds S] [--warmup S] [--profile LIGHT|BALANCED|PER_WORKTREE|AUTOMATIC] [--status-only|--app-only]"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --worktrees) worktrees="$2"; shift 2 ;;
        --runs) runs="$2"; shift 2 ;;
        --seconds) seconds="$2"; shift 2 ;;
        --warmup) warmup="$2"; shift 2 ;;
        --profile) profile="$2"; shift 2 ;;
        --status-only) measure_app=false; shift ;;
        --app-only) measure_status=false; shift ;;
        -h|--help) usage; exit 0 ;;
        *) usage; exit 2 ;;
    esac
done

root="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
home="$work/home"
repo="$work/main-repo"
trap 'pkill -f "user.home=$home" 2> /dev/null; rm -rf "$work"' EXIT

create_repository() {
    git init -q -b main "$repo"
    git -C "$repo" -c user.name=bench -c user.email=bench@example.com commit -q --allow-empty -m "Initial commit"
    for index in $(seq 1 "$worktrees"); do
        git -C "$repo" worktree add -q -b "feature/TASK-$index" "$work/worktrees/TASK-$index"
    done
    mkdir -p "$home/.shogunai"
    cat > "$home/.shogunai/projects.json" <<JSON
[{"id":"benchmark","name":"main-repo","config":{"baseRepositoryPath":"$repo","worktreesRoot":"$work/worktrees","secretFiles":[]}}]
JSON
    cat > "$home/.shogunai/settings.json" <<JSON
{"tourOffered":true,"resources":{"profile":"$profile"}}
JSON
}

measure_shogun_status() {
    "$root/gradlew" -q -p "$root" :cli:installDist
    local shogun="$root/cli/build/install/shogun/bin/shogun"
    local times="$work/status-times"
    : > "$times"
    for _ in $(seq 1 "$runs"); do
        local start end
        start=$(python3 -c 'import time; print(time.perf_counter())')
        (cd "$repo" && JAVA_OPTS="-Duser.home=$home" "$shogun" status > /dev/null)
        end=$(python3 -c 'import time; print(time.perf_counter())')
        python3 -c "print(f'{$end - $start:.3f}')" >> "$times"
    done
    echo "shogun status with $worktrees worktrees, $runs runs (seconds): $(sort -n "$times" | paste -sd' ' -)"
    echo "median: $(sort -n "$times" | awk '{ v[NR] = $1 } END { print v[int((NR + 1) / 2)] }') s"
}

find_jfr() {
    if [ -n "${JFR_BIN:-}" ]; then echo "$JFR_BIN"; return; fi
    if command -v jfr > /dev/null; then command -v jfr; return; fi
    find "$HOME/.gradle/jdks" /Library/Java/JavaVirtualMachines -maxdepth 7 -path "*/bin/jfr" -type f 2> /dev/null | head -1
}

recording_finished() {
    [ -s "$1" ] || return 1
    local before
    before=$(wc -c < "$1")
    sleep 3
    [ "$(wc -c < "$1")" = "$before" ]
}

measure_app_processes() {
    local jfr recording="$work/app.jfr"
    jfr="$(find_jfr)"
    if [ -z "$jfr" ]; then
        echo "jfr not found: set JFR_BIN to a JDK's bin/jfr" >&2
        exit 1
    fi
    echo "Starting the app; leave its window in the state you want to measure (focused, or visible behind another app)."
    echo "Recording starts after ${warmup}s and lasts ${seconds}s."
    "$root/gradlew" -q -p "$root" :desktopApp:run \
        -PbenchmarkHome="$home" -PbenchmarkJfr="$recording" \
        -PbenchmarkDelaySeconds="$warmup" -PbenchmarkSeconds="$seconds" > "$work/app.log" 2>&1 &
    local gradle=$!
    local deadline=$((SECONDS + warmup + seconds + 300))
    until recording_finished "$recording"; do
        if [ $SECONDS -gt $deadline ]; then
            echo "no recording after waiting; app log: $work/app.log" >&2
            exit 1
        fi
        sleep 2
    done
    pkill -f "user.home=$home" 2> /dev/null || true
    wait "$gradle" 2> /dev/null || true
    local commands="$work/commands"
    "$jfr" print --events jdk.ProcessStart "$recording" | sed -n 's/^ *command = "\(.*\)"$/\1/p' > "$commands"
    local count
    count=$(wc -l < "$commands" | tr -d ' ')
    echo "app with $worktrees worktrees, profile $profile: $count processes in ${seconds}s ($(python3 -c "print(round($count * 60 / $seconds, 1))") per minute)"
    echo "most frequent commands:"
    awk '{ n = split($1, parts, "/"); key = parts[n]; if (NF > 1) key = key " " $2; print key }' "$commands" | sort | uniq -c | sort -rn | head -10
}

create_repository
if $measure_status; then measure_shogun_status; fi
if $measure_app; then measure_app_processes; fi
