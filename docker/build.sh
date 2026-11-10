#!/usr/bin/env bash
# Build every range target for both architectures and push it to Harbor.
#
#   ./docker/build.sh                 # build and push all seven
#   ONLY=jetty-javax ./docker/build.sh
#   PUSH=false ./docker/build.sh      # keep the image in the local store
set -euo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
registry=${REGISTRY:-harbor.corp.boundaryx.net/cloudrasp}
tag=${TAG:-0.1.0}
platforms=${PLATFORMS:-linux/amd64,linux/arm64}
push=${PUSH:-true}
only=${ONLY:-}

images=(
  "tomcat-javax|Dockerfile.tomcat-javax|range-war-javax/build/libs"
  "tomcat-jakarta|Dockerfile.tomcat-jakarta|range-war-jakarta/build/libs"
  "jetty-javax|Dockerfile.jetty-javax|range-war-javax/build/libs"
  "bes-javax|Dockerfile.bes-javax|range-war-javax/build/libs"
  "springmvc-javax|Dockerfile.springmvc-javax|range-springmvc-javax/build/libs"
  "springmvc-jakarta|Dockerfile.springmvc-jakarta|range-springmvc-jakarta/build/libs"
  "webflux|Dockerfile.webflux|range-webflux/build/libs"
)

for entry in "${images[@]}"; do
  IFS='|' read -r name file context <<<"$entry"
  if [[ -n "$only" && ",$only," != *",$name,"* ]]; then
    continue
  fi
  ref="$registry/memshell-range-$name:$tag"
  args=(--platform "$platforms" -f "$root/docker/$file" -t "$ref")
  if [[ "$push" == "true" ]]; then
    args+=(--push)
  else
    args+=(--load)
  fi
  echo "==> $ref ($platforms)"
  docker buildx build "${args[@]}" "$root/$context"
done
