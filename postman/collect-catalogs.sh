#!/usr/bin/env bash
# Refresh postman/catalogs/ from running range images.
#
#   ./postman/collect-catalogs.sh
#   REGISTRY=harbor.example/cloudrasp TAG=0.1.0 ./postman/collect-catalogs.sh
#
# Each image is started on a random loopback port, its /api/catalog is saved,
# and the container is removed. Then run `python3 -I postman/build.py`.
set -euo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
out=${OUT:-$root/postman/catalogs}
registry=${REGISTRY:-ghcr.io/MemShellParty/memshell-party-rasp-range}
tag=${TAG:-0.1.0}
mkdir -p "$out"

images=(
  "tomcat-javax|/range"
  "tomcat-jakarta|/range"
  "jetty-javax|/range"
  "springmvc-javax|"
  "springmvc-jakarta|"
  "webflux|"
)

containers=()
cleanup() {
  for container in "${containers[@]}"; do
    docker rm -f "$container" >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT

for entry in "${images[@]}"; do
  IFS='|' read -r name context_path <<<"$entry"
  image="$registry/memshell-range-$name:$tag"
  container="memshell-catalog-$name-$$"
  containers+=("$container")
  echo "==> $image"
  docker run -d --name "$container" -p 127.0.0.1::8080 "$image" >/dev/null
  port=$(docker port "$container" 8080/tcp | head -1 | sed 's/.*://')
  base="http://127.0.0.1:$port$context_path"
  ready=0
  for _ in $(seq 1 180); do
    if curl -fsS --max-time 3 "$base/api/runtime" >/dev/null 2>&1; then
      ready=1
      break
    fi
    sleep 1
  done
  if [[ "$ready" != 1 ]]; then
    echo "error: $image did not become ready" >&2
    docker logs --tail 30 "$container" >&2 || true
    exit 1
  fi
  curl -fsS --max-time 10 "$base/api/catalog" \
    | python3 -I -m json.tool --indent 1 > "$out/$name.json"
  python3 -I -c 'import json,sys; d=json.load(open(sys.argv[1])); print("    server=%s mounts=%d" % (d["server"], sum(len(v) for v in d["matrix"].values())))' "$out/$name.json"
done
