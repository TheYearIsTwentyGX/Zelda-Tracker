#!/usr/bin/env bash
#
# Land the branch you have been working on: merge its pull request, then bring
# the main checkout up to date with what you just merged.
#
#   land                   merge the PR for this branch, then pull the main checkout
#   land --branch NAME     land a branch other than the one you are standing on
#   land --squash          squash instead of a merge commit (--rebase also works)
#   land --delete-branch   delete the remote branch afterwards
#   land --no-pull         merge only, leave the main checkout alone
#   land --dry-run         say what would happen and change nothing
#   land --status          report what is landable here and exit
#
# Adapted from claude-sessions' scripts/land.sh, minus the bridge restart: this
# project has nothing long-running to restart. A worktree-isolated agent session
# is refused `git -C <the main checkout>` by its harness, so this script is the
# sanctioned way to fast-forward it. It is deliberately narrow: it fast-forwards
# and nothing else, it will not touch a main checkout that is dirty or on
# another branch, and it never commits there.

set -uo pipefail

# Where the main checkout lives. Overridable, but this is the answer on this
# machine and the default is what makes the script callable from a worktree.
MAIN="${ZELDA_MAIN:-$HOME/Personal/Zelda-Tracker}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

METHOD=--merge
BRANCH=""; PULL=1; DRY=0; STATUS_ONLY=0; DELETE=0
while [ $# -gt 0 ]; do
    case "$1" in
        --branch) shift; BRANCH="${1:-}" ;;
        --branch=*) BRANCH="${1#*=}" ;;
        --squash) METHOD=--squash ;;
        --rebase) METHOD=--rebase ;;
        --merge) METHOD=--merge ;;
        --delete-branch) DELETE=1 ;;
        --no-pull) PULL=0 ;;
        --dry-run) DRY=1 ;;
        --status) STATUS_ONLY=1 ;;
        -h|--help) sed -n '3,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "land: unknown option '$1'" >&2; exit 1 ;;
    esac
    shift
done

say() { printf '%s\n' "$*"; }
die() { printf 'land: %s\n' "$1" >&2; shift; for l in "$@"; do printf '  %s\n' "$l" >&2; done; exit 1; }

# --- what are we landing ----------------------------------------------------

[ -n "$BRANCH" ] || BRANCH="$(git -C "$HERE" rev-parse --abbrev-ref HEAD 2>/dev/null)"
[ -n "$BRANCH" ] && [ "$BRANCH" != HEAD ] || die "could not work out which branch to land." \
    "Pass one: land --branch <name>"

DEFAULT_BRANCH="$(git -C "$HERE" symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>/dev/null | sed 's|^origin/||')"
[ -n "$DEFAULT_BRANCH" ] || DEFAULT_BRANCH=main

if [ "$BRANCH" = "$DEFAULT_BRANCH" ]; then
    die "you are on $DEFAULT_BRANCH." \
        "There is nothing to land: this is the branch things land onto." \
        "Run this from the worktree whose work you want merged."
fi

# --- is the branch actually finished ---------------------------------------

# Merging a PR that does not include edits still sitting in the working tree is
# the quiet way to lose them. Refuse rather than warn.
DIRTY="$(git -C "$HERE" status --porcelain 2>/dev/null | grep -cv '^??')"
if [ "${DIRTY:-0}" -gt 0 ] && [ "$STATUS_ONLY" != 1 ]; then
    git -C "$HERE" status --porcelain 2>/dev/null | grep -v '^??' | sed 's/^/    /' >&2
    die "$DIRTY tracked file(s) here are not committed." \
        "They are not in the PR, so landing it now would leave them behind." \
        "Commit them and push, or stash them deliberately, then run this again."
fi

UNPUSHED="$(git -C "$HERE" rev-list --count "origin/$BRANCH..$BRANCH" 2>/dev/null || echo unknown)"
if [ "$UNPUSHED" = unknown ]; then
    [ "$STATUS_ONLY" = 1 ] || die "$BRANCH has never been pushed." \
        "Push it first: git push -u origin $BRANCH"
elif [ "${UNPUSHED:-0}" -gt 0 ] && [ "$STATUS_ONLY" != 1 ]; then
    die "$BRANCH is $UNPUSHED commit(s) ahead of origin/$BRANCH." \
        "The PR would merge without them. Push first: git push"
fi

# --- find the pull request --------------------------------------------------

PR_JSON="$(cd "$HERE" && gh pr view "$BRANCH" --json number,state,mergeable,mergeStateStatus,title,url 2>/dev/null)"
if [ -z "$PR_JSON" ]; then
    die "no pull request found for $BRANCH." \
        "Open one first — the title and body are yours to write:" \
        "  gh pr create --head $BRANCH --fill"
fi

field() {
    printf '%s' "$PR_JSON" | python3 -c "import json,sys
try: print(json.load(sys.stdin).get('$1',''))
except Exception: print('')" 2>/dev/null
}

PR_NUM="$(field number)"; PR_STATE="$(field state)"; PR_TITLE="$(field title)"
PR_URL="$(field url)"; MERGEABLE="$(field mergeable)"; MERGE_STATE="$(field mergeStateStatus)"

if [ "$STATUS_ONLY" = 1 ]; then
    say "branch      $BRANCH"
    say "pull request #$PR_NUM  $PR_STATE  $PR_TITLE"
    say "            $PR_URL"
    say "mergeable   $MERGEABLE ($MERGE_STATE)"
    say "unpushed    ${UNPUSHED:-0} commit(s)"
    say "uncommitted ${DIRTY:-0} tracked file(s)"
    say "main        $MAIN"
    exit 0
fi

[ "$PR_STATE" = OPEN ] || die "PR #$PR_NUM is $PR_STATE, not OPEN." "$PR_URL"

if [ "$MERGEABLE" = CONFLICTING ]; then
    die "PR #$PR_NUM conflicts with $DEFAULT_BRANCH." \
        "Merge $DEFAULT_BRANCH into $BRANCH and resolve it, then run this again." \
        "$PR_URL"
fi

case "$MERGE_STATE" in
    BLOCKED|DIRTY)
        die "PR #$PR_NUM is not mergeable yet — $MERGE_STATE." \
            "Failing checks or an unmet review rule. Look before forcing it:" \
            "$PR_URL" ;;
esac

# --- merge ------------------------------------------------------------------

say "Landing #$PR_NUM — $PR_TITLE"
MERGE_ARGS=("$PR_NUM" "$METHOD")
[ "$DELETE" = 1 ] && MERGE_ARGS+=(--delete-branch)

if [ "$DRY" = 1 ]; then
    say "  would run: gh pr merge ${MERGE_ARGS[*]}"
else
    if ! (cd "$HERE" && gh pr merge "${MERGE_ARGS[@]}"); then
        die "gh pr merge failed. Nothing was pulled." "$PR_URL"
    fi
    say "Merged."
    # Tell TGXCode, so PRs this merge just put into conflict show it now rather
    # than on its idle floor. Backgrounded and best-effort.
    TOKEN_FILE="${XDG_DATA_HOME:-$HOME/.local/share}/tgxcode/token"
    [ -r "$TOKEN_FILE" ] || TOKEN_FILE="${XDG_DATA_HOME:-$HOME/.local/share}/claude-sessions/token"
    if [ -r "$TOKEN_FILE" ]; then
        ( curl -fsS -m 30 -o /dev/null \
            -H "Authorization: Bearer $(cat "$TOKEN_FILE")" \
            'http://127.0.0.1:45888/api/dashboard?refresh=1' >/dev/null 2>&1 & ) || true
    fi
fi

# --- bring the main checkout up to date ------------------------------------

if [ "$PULL" != 1 ]; then
    say "Left $MAIN alone (--no-pull)."
    exit 0
fi

[ -d "$MAIN/.git" ] || die "no main checkout at $MAIN." \
    "Set ZELDA_MAIN if it lives somewhere else." \
    "The merge is done; only the pull was skipped."

MAIN_BRANCH="$(git -C "$MAIN" rev-parse --abbrev-ref HEAD 2>/dev/null)"
if [ "$MAIN_BRANCH" != "$DEFAULT_BRANCH" ]; then
    say "Heads up: $MAIN is on '$MAIN_BRANCH', not $DEFAULT_BRANCH — not pulling."
    say "  The merge is done. Pull it yourself once that checkout is back on $DEFAULT_BRANCH."
    exit 0
fi

MAIN_DIRTY="$(git -C "$MAIN" status --porcelain 2>/dev/null | grep -cv '^??')"
if [ "${MAIN_DIRTY:-0}" -gt 0 ]; then
    git -C "$MAIN" status --porcelain 2>/dev/null | grep -v '^??' | sed 's/^/    /'
    say "Heads up: $MAIN has ${MAIN_DIRTY} uncommitted tracked file(s) — not pulling."
    say "  The merge is done. Deal with those, then: git -C $MAIN pull --ff-only"
    exit 0
fi

if [ "$DRY" = 1 ]; then
    say "  would run: git -C $MAIN pull --ff-only"
    exit 0
fi

BEFORE="$(git -C "$MAIN" rev-parse HEAD 2>/dev/null)"

say "Updating $MAIN…"
# --ff-only so this can never leave a merge or a conflict in the main checkout.
# A diverged main is a decision for a human.
if ! git -C "$MAIN" pull --ff-only; then
    die "pull failed in $MAIN — it has probably diverged from origin." \
        "The merge is done; sort the checkout out by hand."
fi

AFTER="$(git -C "$MAIN" rev-parse HEAD 2>/dev/null)"

if [ "$BEFORE" = "$AFTER" ]; then
    say "$MAIN was already up to date."
    exit 0
fi

say "$MAIN is now at $(git -C "$MAIN" log --oneline -1 | cut -c1-60)"

# The phone runs whatever was last installed, not what is on disk.
if git -C "$MAIN" diff --name-only "$BEFORE" "$AFTER" 2>/dev/null | grep -qE '^(androidApp|shared)/'; then
    say ""
    say "This merge changed the app. Press *Install on phone* to put it on the device."
fi
exit 0
