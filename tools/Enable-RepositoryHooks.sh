#!/bin/sh
# Run from macOS, Linux, or Git Bash for Windows. This only configures the local clone.
set -eu
repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_root"
[ "$(git remote)" = origin ] || {
    printf '%s\n' 'Expected exactly one remote named origin; inspect remotes first.' >&2
    exit 1
}
fetch_url=$(git remote get-url --all origin)
push_url=$(git remote get-url --push --all origin)
[ "$fetch_url" = "$push_url" ] || {
    printf '%s\n' 'Origin must use the same URL for fetch and push.' >&2
    exit 1
}
existing=$(git config --local --get core.hooksPath) || {
    status=$?
    [ "$status" -eq 1 ] || exit "$status"
}
case "$existing" in
    ''|.githooks) ;;
    *) printf '%s\n' 'An existing custom hooksPath must be reviewed first.' >&2; exit 1 ;;
esac
[ -f .githooks/pre-push ] || {
    printf '%s\n' 'The tracked push guard is missing.' >&2
    exit 1
}
sh .githooks/pre-push origin "$push_url" </dev/null
chmod u+x .githooks/pre-push
git config --local core.hooksPath .githooks
git config --local remote.pushDefault origin
git config --local push.default simple
printf '%s\n' 'Verified the official repository and enabled its push guard.'
