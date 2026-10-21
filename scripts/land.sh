#!/usr/bin/env bash
#
# Land the branch you have been working on: merge its pull request, bring the
# main checkout up to date with what you just merged, then clear away the
# worktree it was built in.
#
#   land                   merge the PR, pull the main checkout, remove this worktree
#   land --branch NAME     land a branch other than the one you are standing on
#   land --squash          squash instead of a merge commit (--rebase also works)
#   land --delete-branch   delete the remote branch afterwards
#   land --no-pull         merge only, leave the main checkout alone
#   land --keep-worktree   do not remove the worktree or its local branch
#   land --dry-run         say what would happen and change nothing
#   land --status          report what is landable here and exit
#
# Adapted from claude-sessions' scripts/land.sh, minus the bridge restart: this
# project has nothing long-running to restart. A worktree-isolated agent session
# is refused `git -C <the main checkout>` by its harness, so this script is the
# sanctioned way to fast-forward it. It is deliberately narrow: it fast-forwards
# and nothing else, it will not touch a main checkout that is dirty or on
# another branch, and it never commits there.
#
# Run from a linked worktree, it then removes that worktree (`git worktree
# remove`, never --force) and deletes the local branch with the safe `branch
# -d`. It leaves the worktree alone if anything in it is uncommitted or
# untracked-and-not-ignored, and says what is there. The Land button is meant to
# be the LAST step of a worker's job: a session still running in the worktree
# loses its working directory when this succeeds. Pass --keep-worktree if the
# session is not finished with it.

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Where the main checkout lives: the parent of the repository's common .git,
# which is the same answer from the main checkout and from any linked worktree.
# ZELDA_MAIN still overrides it.
COMMON_DIR="$(git -C "$HERE" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)"
GIT_DIR="$(git -C "$HERE" rev-parse --path-format=absolute --git-dir 2>/dev/null)"
MAIN="${ZELDA_MAIN:-$(dirname "${COMMON_DIR:-$HOME/Projects/Zelda-Tracker/.git}")}"

METHOD=--merge
BRANCH=""; PULL=1; DRY=0; STATUS_ONLY=0; DELETE=0; KEEP_WORKTREE=0
while [ $# -gt 0 ]; do
    case "$1" in
        --branch) shift; BRANCH="${1:-}" ;;
        --branch=*) BRANCH="${1#*=}" ;;
        --squash) METHOD=--squash ;;
        --rebase) METHOD=--rebase ;;
        --merge) METHOD=--merge ;;
        --delete-branch) DELETE=1 ;;
        --no-pull) PULL=0 ;;
        --keep-worktree) KEEP_WORKTREE=1 ;;
        --dry-run) DRY=1 ;;
        --status) STATUS_ONLY=1 ;;
        -h|--help) sed -n '3,14p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "land: unknown option '$1'" >&2; exit 1 ;;
    esac
    shift
done

say() { printf '%s\n' "$*"; }
die() { printf 'land: %s\n' "$1" >&2; shift; for l in "$@"; do printf '  %s\n' "$l" >&2; done; exit 1; }

# --- what are we landing ----------------------------------------------------

HERE_BRANCH="$(git -C "$HERE" rev-parse --abbrev-ref HEAD 2>/dev/null)"
[ -n "$BRANCH" ] || BRANCH="$HERE_BRANCH"
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

# --- will the worktree go afterwards ----------------------------------------

# Only a linked worktree that has the landed branch checked out is removed; the
# main checkout, or a worktree landing some other --branch, is left standing.
# `status --porcelain` already hides ignored files, so anything it prints —
# tracked edits or untracked files — is work that removal would throw away.
REMOVE_WORKTREE=0; WORKTREE_NOTE=""
if [ "$KEEP_WORKTREE" = 1 ]; then
    WORKTREE_NOTE="kept (--keep-worktree)"
elif [ -z "$GIT_DIR" ] || [ "$GIT_DIR" = "$COMMON_DIR" ] || [ "$HERE" = "$MAIN" ]; then
    WORKTREE_NOTE="not a linked worktree — nothing to remove"
elif [ "$HERE_BRANCH" != "$BRANCH" ]; then
    WORKTREE_NOTE="kept — it has $HERE_BRANCH checked out, not $BRANCH"
else
    REMOVE_WORKTREE=1
    WORKTREE_NOTE="will be removed after landing, with local branch $BRANCH"
fi

# Prints what would block removal, if anything.
worktree_leftovers() { git -C "$HERE" status --porcelain 2>/dev/null; }

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
    if [ "$REMOVE_WORKTREE" = 1 ] && [ -n "$(worktree_leftovers)" ]; then
        WORKTREE_NOTE="would be kept — it has uncommitted or untracked files"
    fi
    say "branch      $BRANCH"
    say "pull request #$PR_NUM  $PR_STATE  $PR_TITLE"
    say "            $PR_URL"
    say "mergeable   $MERGEABLE ($MERGE_STATE)"
    say "unpushed    ${UNPUSHED:-0} commit(s)"
    say "uncommitted ${DIRTY:-0} tracked file(s)"
    say "main        $MAIN"
    say "worktree    $HERE"
    say "            $WORKTREE_NOTE"
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

# The merge is done by now, so nothing in here stops the worktree cleanup that
# follows: a skipped or failed pull returns, and only sets STATUS on failure.
STATUS=0
pull_main() {
    if [ "$PULL" != 1 ]; then
        say "Left $MAIN alone (--no-pull)."
        return
    fi

    if [ ! -e "$MAIN/.git" ]; then
        say "Heads up: no main checkout at $MAIN — not pulling."
        say "  The merge is done. Set ZELDA_MAIN if it lives somewhere else."
        STATUS=1; return
    fi

    local main_branch
    main_branch="$(git -C "$MAIN" rev-parse --abbrev-ref HEAD 2>/dev/null)"
    if [ "$main_branch" != "$DEFAULT_BRANCH" ]; then
        say "Heads up: $MAIN is on '$main_branch', not $DEFAULT_BRANCH — not pulling."
        say "  The merge is done. Pull it yourself once that checkout is back on $DEFAULT_BRANCH."
        return
    fi

    local main_dirty
    main_dirty="$(git -C "$MAIN" status --porcelain 2>/dev/null | grep -cv '^??')"
    if [ "${main_dirty:-0}" -gt 0 ]; then
        git -C "$MAIN" status --porcelain 2>/dev/null | grep -v '^??' | sed 's/^/    /'
        say "Heads up: $MAIN has ${main_dirty} uncommitted tracked file(s) — not pulling."
        say "  The merge is done. Deal with those, then: git -C $MAIN pull --ff-only"
        return
    fi

    if [ "$DRY" = 1 ]; then
        say "  would run: git -C $MAIN pull --ff-only"
        return
    fi

    local before after
    before="$(git -C "$MAIN" rev-parse HEAD 2>/dev/null)"

    say "Updating $MAIN…"
    # --ff-only so this can never leave a merge or a conflict in the main checkout.
    # A diverged main is a decision for a human.
    if ! git -C "$MAIN" pull --ff-only; then
        say "land: pull failed in $MAIN — it has probably diverged from origin." >&2
        say "  The merge is done; sort the checkout out by hand." >&2
        STATUS=1; return
    fi

    after="$(git -C "$MAIN" rev-parse HEAD 2>/dev/null)"

    if [ "$before" = "$after" ]; then
        say "$MAIN was already up to date."
        return
    fi

    say "$MAIN is now at $(git -C "$MAIN" log --oneline -1 | cut -c1-60)"

    # The phone runs whatever was last installed, not what is on disk.
    if git -C "$MAIN" diff --name-only "$before" "$after" 2>/dev/null | grep -qE '^(androidApp|shared)/'; then
        say ""
        say "This merge changed the app. Press *Install on phone* to put it on the device."
    fi
}
pull_main

# --- clear the worktree away ------------------------------------------------

if [ "$REMOVE_WORKTREE" != 1 ]; then
    [ "$KEEP_WORKTREE" = 1 ] && say "Kept worktree $HERE (--keep-worktree)."
    exit "$STATUS"
fi

LEFTOVERS="$(worktree_leftovers)"
if [ -n "$LEFTOVERS" ]; then
    printf '%s\n' "$LEFTOVERS" | sed 's/^/    /'
    say "Kept worktree $HERE: the files above are uncommitted or untracked, and"
    say "  removing it would throw them away. Deal with them, then:"
    say "  git -C $MAIN worktree remove $HERE && git -C $MAIN branch -d $BRANCH"
    exit "$STATUS"
fi

if [ "$DRY" = 1 ]; then
    say "  would remove worktree $HERE"
    say "  would run: git -C $MAIN worktree prune"
    say "  would run: git -C $MAIN branch -d $BRANCH"
    exit "$STATUS"
fi

# Step out of the directory about to disappear. Bash keeps this script's file
# open, so reading the rest of it after the removal is fine.
cd "$MAIN" || die "could not cd to $MAIN; worktree $HERE left in place."

if ! git -C "$MAIN" worktree remove "$HERE"; then
    say "Kept worktree $HERE: git worktree remove refused (see above)."
    exit 1
fi
git -C "$MAIN" worktree prune

# The safe -d: it refuses a branch whose commits are not in main (or its
# upstream), which is what a --squash or --rebase landing looks like to git.
# The PR is merged either way, so say so and leave the branch for a human.
if ! git -C "$MAIN" branch -d "$BRANCH" 2>/dev/null; then
    say "Kept local branch $BRANCH: git branch -d refused, as it does after a"
    say "  squash or rebase landing (or when main was not pulled). The PR is merged;"
    say "  if you are sure: git -C $MAIN branch -D $BRANCH"
fi

say "Removed worktree $HERE; you're now done with this branch."
exit "$STATUS"
