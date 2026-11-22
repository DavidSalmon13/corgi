#!/usr/bin/env bash
# context-budget: reports how much of this session's context window is in use,
# measured from the last assistant turn's token usage in the session transcript.
#
# Output (one line):  used=<tokens> window=<tokens> used_pct=<n> left_pct=<n> verdict=<continue|stop>
# verdict=stop once left_pct <= STOP_AT_LEFT_PCT (default 20).
#
# Env overrides:
#   CONTEXT_WINDOW     window size in tokens (default: 200000, or 1000000 once usage exceeds 200k)
#   STOP_AT_LEFT_PCT   stop threshold, percent of the window still free (default 20)
#   CLAUDE_CODE_SESSION_ID  set by Claude Code; used to find this session's transcript
set -euo pipefail

STOP_AT_LEFT_PCT="${STOP_AT_LEFT_PCT:-20}"
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
# Claude Code stores transcripts under ~/.claude/projects/<path with / and . replaced by ->/
SLUG="$(printf '%s' "$PROJECT_DIR" | sed 's#[/.]#-#g')"
TDIR="$HOME/.claude/projects/$SLUG"

TRANSCRIPT=""
if [ -n "${CLAUDE_CODE_SESSION_ID:-}" ] && [ -f "$TDIR/$CLAUDE_CODE_SESSION_ID.jsonl" ]; then
  TRANSCRIPT="$TDIR/$CLAUDE_CODE_SESSION_ID.jsonl"
else
  # Fallback: the most recently written transcript is the live session.
  TRANSCRIPT="$(ls -t "$TDIR"/*.jsonl 2>/dev/null | head -n 1 || true)"
fi

if [ -z "$TRANSCRIPT" ] || [ ! -f "$TRANSCRIPT" ]; then
  echo "used=unknown verdict=unknown reason=no-transcript-found dir=$TDIR"
  exit 0
fi

python3 - "$TRANSCRIPT" "$STOP_AT_LEFT_PCT" "${CONTEXT_WINDOW:-}" <<'PY'
import json, sys
path, stop_left, window_env = sys.argv[1], float(sys.argv[2]), sys.argv[3]
used = None
with open(path, encoding="utf-8") as f:
    for line in f:
        if '"usage"' not in line:
            continue
        try:
            d = json.loads(line)
        except Exception:
            continue
        if d.get("type") != "assistant" or d.get("isSidechain"):
            continue
        u = (d.get("message") or {}).get("usage") or {}
        total = sum(int(u.get(k) or 0) for k in
                    ("input_tokens", "cache_creation_input_tokens", "cache_read_input_tokens"))
        if total:
            used = total
if used is None:
    print("used=unknown verdict=unknown reason=no-usage-yet")
    sys.exit(0)
window = int(window_env) if window_env else (200_000 if used <= 200_000 else 1_000_000)
used_pct = round(100 * used / window, 1)
left_pct = round(100 - used_pct, 1)
verdict = "stop" if left_pct <= stop_left else "continue"
print(f"used={used} window={window} used_pct={used_pct} left_pct={left_pct} verdict={verdict}")
PY
