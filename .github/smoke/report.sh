#!/usr/bin/env bash

REPORT_FAILURES=0

expect_report() {
  local report="$1" filter="$2" expected="$3" actual
  actual="$(jq -r "$filter" "$report")"
  if [ "$actual" = "$expected" ]; then
    echo "ok: $filter = $expected"
  else
    echo "::error::Smoke test report: $filter is '$actual', expected '$expected'." >&2
    REPORT_FAILURES=1
  fi
}

print_failed_steps() {
  jq -r '.steps[] | select(.status == "failed") | "::error::Step \(.name) failed: \(.detail)"' "$1" >&2
}

finish_report() {
  local report="$1"
  print_failed_steps "$report"
  if [ "$REPORT_FAILURES" -ne 0 ]; then
    echo "Full report:" >&2
    cat "$report" >&2
    exit 1
  fi
}
