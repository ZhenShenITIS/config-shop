#!/usr/bin/env bash
set -euo pipefail

base="${1:?A base SHA is required}"
head="${2:?A head SHA is required}"
event="${3:?An event name is required}"

if [[ "$event" == pull_request ]]; then
  base="$(git merge-base "$base" "$head")"
elif [[ "$base" =~ ^0+$ ]]; then
  base="$(git hash-object -t tree -w /dev/null)"
fi

git diff --name-only --no-renames -z "$base" "$head" -- | {
  app=false
  web=false
  while IFS= read -r -d '' path; do
    case "$path" in
      api/*|.github/workflows/ci-cd.yml|.github/workflows/deploy-component.yml|scripts/ci-changes.sh|scripts/deploy.sh|docker-compose.yaml)
        app=true
        web=true
        ;;
      src/*|pom.xml|mvnw|mvnw.cmd|.mvn/*|Dockerfile|.dockerignore|.env_dist)
        app=true
        ;;
      frontend/*)
        web=true
        ;;
    esac
  done
  printf 'app=%s\nweb=%s\n' "$app" "$web"
}
