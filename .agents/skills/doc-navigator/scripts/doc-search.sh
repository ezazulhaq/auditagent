#!/usr/bin/env bash
#
# doc-search.sh - Fast search utility across AuditAgent documentation (doc/*.md)
#
# Usage:
#   ./doc-search.sh [options] <search-term>
#
# Options:
#   -h, --help          Show this help message
#   -H, --headers-only  Search only markdown headers (#, ##, ###)
#   -c, --count-only    Show match counts per document
#   -f, --file PATTERN  Filter search to matching document names (e.g., '07' or 'api')
#   -C, --context NUM   Show NUM lines of context around each match (default: 0)
#   -s, --case-sensitive Enable case-sensitive matching (default: case-insensitive)
#

set -euo pipefail

# Find repository root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if ! REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null)"; then
  REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
fi
DOC_DIR="$REPO_ROOT/doc"

if [ ! -d "$DOC_DIR" ]; then
  echo "Error: Documentation directory not found at $DOC_DIR" >&2
  exit 1
fi

show_help() {
  cat <<'EOF'
doc-search.sh - Search AuditAgent documentation (doc/*.md)

Usage:
  ./doc-search.sh [options] <query>

Options:
  -H, --headers-only   Search only markdown headings (#, ##, ###)
  -c, --count-only     Show match count per document
  -f, --file PATTERN   Filter search to matching doc filenames (e.g. "07", "api", "auth")
  -C, --context NUM    Show NUM lines of context around each match (default: 0)
  -s, --case-sensitive Case-sensitive matching (default is case-insensitive)
  -h, --help           Show this help message

Examples:
  ./doc-search.sh "GitHubApp"
  ./doc-search.sh -H "remediation"
  ./doc-search.sh -f api "SSE"
  ./doc-search.sh -c "DuckDB"
EOF
}

HEADERS_ONLY=false
COUNT_ONLY=false
FILE_FILTER=""
CONTEXT=0
CASE_INSENSITIVE="-i"
QUERY=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      show_help
      exit 0
      ;;
    -H|--headers-only)
      HEADERS_ONLY=true
      shift
      ;;
    -c|--count-only)
      COUNT_ONLY=true
      shift
      ;;
    -f|--file)
      if [[ -z "${2:-}" ]]; then
        echo "Error: -f/--file requires an argument" >&2
        exit 1
      fi
      FILE_FILTER="$2"
      shift 2
      ;;
    -C|--context)
      if [[ -z "${2:-}" ]] || ! [[ "$2" =~ ^[0-9]+$ ]]; then
        echo "Error: -C/--context requires a non-negative integer" >&2
        exit 1
      fi
      CONTEXT="$2"
      shift 2
      ;;
    -s|--case-sensitive)
      CASE_INSENSITIVE=""
      shift
      ;;
    *)
      if [[ -z "$QUERY" ]]; then
        QUERY="$1"
      else
        QUERY="$QUERY $1"
      fi
      shift
      ;;
  esac
done

if [[ -z "$QUERY" ]]; then
  echo "Error: Search query required." >&2
  echo "Run './doc-search.sh --help' for usage." >&2
  exit 1
fi

# Collect target doc files
DOC_FILES=()
for f in "$DOC_DIR"/*.md; do
  [ -e "$f" ] || continue
  fname="$(basename "$f")"
  if [[ -n "$FILE_FILTER" ]]; then
    if [[ "$fname" =~ $FILE_FILTER ]]; then
      DOC_FILES+=("$f")
    fi
  else
    DOC_FILES+=("$f")
  fi
done

if [[ ${#DOC_FILES[@]} -eq 0 ]]; then
  echo "No documentation files matched filter: $FILE_FILTER" >&2
  exit 0
fi

if $COUNT_ONLY; then
  echo "Match counts for query: \"$QUERY\""
  echo "----------------------------------------"
  total=0
  for f in "${DOC_FILES[@]}"; do
    fname="$(basename "$f")"
    if $HEADERS_ONLY; then
      cnt=$(grep $CASE_INSENSITIVE -E "^#+ .*${QUERY}" "$f" 2>/dev/null | wc -l || true)
    else
      cnt=$(grep $CASE_INSENSITIVE "$QUERY" "$f" 2>/dev/null | wc -l || true)
    fi
    if [[ "$cnt" -gt 0 ]]; then
      printf "  %-35s : %d\n" "$fname" "$cnt"
      total=$((total + cnt))
    fi
  done
  echo "----------------------------------------"
  echo "Total matches: $total"
  exit 0
fi

MATCH_FOUND=false
for f in "${DOC_FILES[@]}"; do
  fname="$(basename "$f")"
  rel_path="doc/$fname"
  
  if $HEADERS_ONLY; then
    results=$(grep $CASE_INSENSITIVE -n -E "^#+ .*${QUERY}" "$f" 2>/dev/null || true)
  else
    if [[ "$CONTEXT" -gt 0 ]]; then
      results=$(grep $CASE_INSENSITIVE -n -C "$CONTEXT" "$QUERY" "$f" 2>/dev/null || true)
    else
      results=$(grep $CASE_INSENSITIVE -n "$QUERY" "$f" 2>/dev/null || true)
    fi
  fi

  if [[ -n "$results" ]]; then
    MATCH_FOUND=true
    echo "=== [$rel_path] ==="
    echo "$results"
    echo ""
  fi
done

if ! $MATCH_FOUND; then
  echo "No matches found for query: \"$QUERY\" in doc/*.md"
  exit 0
fi
