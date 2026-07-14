#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
BASE_IMAGE="${BASE_IMAGE:-alibabadragonwell/dragonwell:25.0.3.0.3.9-standard-ga-anolis}"

cd "$ROOT_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker not found"
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn not found"
  exit 1
fi

required_vars=(
  SAASBASE_DATASOURCE_HOST
  SAASBASE_DATASOURCE_USERNAME
  SAASBASE_DATASOURCE_PASSWORD
  SAASBASE_REDIS_HOST
  SAASBASE_JWT_SECRET
)

for var in "${required_vars[@]}"; do
  if [ -z "${!var:-}" ]; then
    echo "$var is required"
    exit 1
  fi
done

echo "1/5 package jar"
mvn -DskipTests package

echo "2/5 pull base image"
if docker image inspect "$BASE_IMAGE" >/dev/null 2>&1; then
  echo "base image already present locally"
else
  docker pull "$BASE_IMAGE"
fi

echo "3/5 build image"
docker compose -f "$COMPOSE_FILE" build app

echo "4/5 start app"
docker compose -f "$COMPOSE_FILE" down --remove-orphans
docker compose -f "$COMPOSE_FILE" up -d app

echo "publish done"
