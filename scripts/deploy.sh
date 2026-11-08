#!/usr/bin/env bash
set -euo pipefail

deploy_path="${1:?Usage: deploy.sh DEPLOY_PATH COMMIT BRANCH}"
commit="${2:?Usage: deploy.sh DEPLOY_PATH COMMIT BRANCH}"
branch="${3:?Usage: deploy.sh DEPLOY_PATH COMMIT BRANCH}"

cd -- "$deploy_path"

if [[ "$(git branch --show-current)" != "$branch" ]]; then
  echo "The server checkout must be on branch $branch." >&2
  exit 1
fi

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "The server has changes to tracked files. Resolve them before deploying." >&2
  exit 1
fi

if [[ ! -f .env ]]; then
  echo "Missing .env in the server checkout." >&2
  exit 1
fi

export GIT_TERMINAL_PROMPT=0
export GIT_SSH_COMMAND='ssh -o BatchMode=yes'
git fetch --no-tags origin "$branch" < /dev/null

if [[ "$(git rev-parse FETCH_HEAD)" != "$commit" ]]; then
  echo "Skipping this deployment: origin/$branch has moved to another commit."
  exit 0
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

echo "Deployed $commit."
