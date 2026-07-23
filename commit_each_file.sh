#!/usr/bin/env bash
# =============================================================================
# commit_each_file.sh
# Commits every tracked-eligible file in the monorepo ONE AT A TIME.
#
# Prunes at traversal time (via -prune) so it never enters:
#   .git/  target/  node_modules/  .aider*/
# Also skips:
#   application-local.properties
#
# Usage: bash commit_each_file.sh
# Run from: /home/prrssshhhh/smartwater
# =============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_ROOT"

echo "=== SmartWater Monorepo — Per-File Commit Script ==="
echo "Repository root: $REPO_ROOT"
echo ""

# Configure git identity if not already set (CI-safe)
git config user.email "prrssshhhh@smartwater.local" 2>/dev/null || true
git config user.name  "SmartWater Dev"               2>/dev/null || true

# -----------------------------------------------------------------------
# Build the file list, pruning directories that must never be committed.
# We use null-delimited output (-print0) to safely handle any filename.
# -----------------------------------------------------------------------
mapfile -d '' FILES < <(
  find . \
    -path ./.git                                                                          -prune -o \
    -path ./smartwaterbackend/target                                                      -prune -o \
    -path ./smartwaterfrontend/node_modules                                               -prune -o \
    -path ./smartwaterbackend/src/main/resources/application-local.properties            -prune -o \
    -name '.aider*'                                                                       -prune -o \
    -type f -print0
)

TOTAL=${#FILES[@]}
echo "Files to commit: $TOTAL"
echo "--------------------------------------------------------------"

COMMITTED=0
SKIPPED=0

for FILEPATH in "${FILES[@]}"; do
  # Strip leading ./
  REL="${FILEPATH#./}"

  # Safety net: skip if git itself would ignore the file
  if git check-ignore -q "$REL" 2>/dev/null; then
    echo "  [IGNORED]  $REL"
    ((SKIPPED++)) || true
    continue
  fi

  # Skip if the file is already tracked and unmodified (nothing to commit)
  if git ls-files --error-unmatch "$REL" &>/dev/null; then
    # File is tracked — check if it's clean
    if git diff --quiet HEAD -- "$REL" 2>/dev/null && \
       git diff --cached --quiet -- "$REL" 2>/dev/null; then
      echo "  [CLEAN]    $REL"
      ((SKIPPED++)) || true
      continue
    fi
  fi

  ((COMMITTED++)) || true
  echo "  Committing $COMMITTED/$TOTAL: $REL"

  git add -- "$REL"
  git commit -m "Add $REL" --quiet
done

echo ""
echo "============================================================"
echo "Done."
echo "  Files committed : $COMMITTED"
echo "  Files skipped   : $SKIPPED  (already clean or gitignored)"
echo "  Total scanned   : $TOTAL"
echo "============================================================"
