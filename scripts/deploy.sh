#!/usr/bin/env bash
set -euo pipefail

deploy_path="${1:?Usage: deploy.sh DEPLOY_PATH COMMIT BRANCH [app|web]}"
commit="${2:?A commit is required}"
branch="${3:?A branch is required}"
component="${4:-app}"
case "$component" in app|web) ;; *) exit 2 ;; esac

cd -- "$deploy_path"

if [[ "$(git branch --show-current)" != "$branch" ]]; then
  echo "The server checkout must be on branch $branch." >&2
  exit 1
fi

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "The server has changes to tracked files. Resolve them before deploying." >&2
  exit 1
fi

export GIT_TERMINAL_PROMPT=0
export GIT_SSH_COMMAND='ssh -o BatchMode=yes'
git fetch --no-tags origin "$branch" < /dev/null

if ! git merge-base --is-ancestor "$commit" FETCH_HEAD; then
  echo "The checked commit is no longer part of origin/$branch." >&2
  exit 1
fi

release_file="$(git rev-parse --git-path "config-shop-$component-deployed")"
if [[ -f "$release_file" ]] && deployed="$(cat -- "$release_file")" &&
   [[ "$commit" != "$deployed" ]] && git merge-base --is-ancestor "$commit" "$deployed"; then
  echo "Skipping this deployment: $component already runs a later revision."
  exit 0
fi

if [[ "$component" == web ]]; then
  # Build the tested frontend revision independently of the backend checkout.
  stage="$(mktemp -d)"
  trap 'rm -rf -- "$stage"' EXIT
  git archive "$commit" api frontend | tar -x -C "$stage"
  compose=(docker compose --project-name config-shop-web --env-file /dev/null -f "$stage/frontend/compose.yaml")
  "${compose[@]}" config --quiet
  "${compose[@]}" build web
  "${compose[@]}" up --detach --no-deps --no-build --wait --wait-timeout 90 web
  "${compose[@]}" ps web
  printf '%s\n' "$commit" > "$release_file"
  echo "Deployed web $commit."
  exit 0
fi

if [[ ! -f .env ]]; then
  echo "Missing .env in the server checkout." >&2
  exit 1
fi

if ! git merge-base --is-ancestor HEAD "$commit"; then
  echo "The server has local commits or divergent history. A fast-forward is required." >&2
  exit 1
fi

git merge --ff-only "$commit" < /dev/null
docker compose config --quiet < /dev/null
docker compose build app < /dev/null

umask 077
backup_dir="$PWD/backups"
mkdir -p -- "$backup_dir"
backup_tmp="$(mktemp "$backup_dir/.shop_db_$(date -u +%Y%m%dT%H%M%SZ)_${commit:0:12}_XXXXXX")"
trap 'rm -f -- "$backup_tmp"' EXIT

echo "Creating a full PostgreSQL backup before starting the new app."
docker compose exec -T db sh -ec '
  export PGPASSWORD="$POSTGRES_PASSWORD"
  exec pg_dump --format=custom --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"
' < /dev/null > "$backup_tmp"

if [[ ! -s "$backup_tmp" ]]; then
  echo "The database backup is empty. Deployment aborted." >&2
  exit 1
fi

docker compose exec -T db pg_restore --file=/dev/null < "$backup_tmp"
backup_name="${backup_tmp##*/}"
backup_file="$backup_dir/${backup_name#.}.dump"
mv -- "$backup_tmp" "$backup_file"
trap - EXIT
echo "Database backup saved to $backup_file."

docker compose up --detach --no-deps --no-build --wait --wait-timeout 180 app < /dev/null
docker compose ps app < /dev/null

printf '%s\n' "$commit" > "$release_file"
echo "Deployed app $commit."
