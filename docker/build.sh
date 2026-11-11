#!/usr/bin/env bash
# Build the range images and push them to a registry.
#
#   ./docker/build.sh                              # six public images to GHCR
#   REGISTRY=registry.example.com/team ./docker/build.sh
#   PUSH=false PLATFORMS=linux/amd64 ./docker/build.sh   # local amd64 images
#   ONLY=tomcat-javax,webflux ./docker/build.sh
#
# All six images use Docker Hub base images.
set -euo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
registry=${REGISTRY:-$(printf '%s' 'ghcr.io/MemShellParty/memshell-party-rasp-range' | tr '[:upper:]' '[:lower:]')}
tag=${TAG:-0.1.0}
platforms=${PLATFORMS:-linux/amd64,linux/arm64}
push=${PUSH:-true}
only=${ONLY:-}

if [[ "$push" != "true" && "$platforms" == *,* ]]; then
  echo "error: --load supports one platform, set PLATFORMS=linux/amd64" >&2
  exit 1
fi

# name|dockerfile|build context|artifact glob
images=(
  "tomcat-javax|Dockerfile.tomcat-javax|range-war-javax/build/libs|range-war-javax-*.war"
  "tomcat-jakarta|Dockerfile.tomcat-jakarta|range-war-jakarta/build/libs|range-war-jakarta-*.war"
  "jetty-javax|Dockerfile.jetty-javax|range-war-javax/build/libs|range-war-javax-*.war"
  "springmvc-javax|Dockerfile.springmvc-javax|range-springmvc-javax/build/libs|range-springmvc-javax-*.jar"
  "springmvc-jakarta|Dockerfile.springmvc-jakarta|range-springmvc-jakarta/build/libs|range-springmvc-jakarta-*.jar"
  "webflux|Dockerfile.webflux|range-webflux/build/libs|range-webflux-*.jar"
)

for entry in "${images[@]}"; do
  IFS='|' read -r name file context artifact <<<"$entry"
  if [[ -n "$only" && ",$only," != *",$name,"* ]]; then
    continue
  fi
  ref="$registry/memshell-range-$name:$tag"
  args=(--platform "$platforms" -f "$root/docker/$file" -t "$ref"
        --build-arg "ARTIFACT=$artifact")
  if [[ "$push" == "true" ]]; then
    args+=(--push)
  else
    args+=(--load)
  fi
  echo "==> $ref ($platforms)"
  docker buildx build "${args[@]}" "$root/$context"
done
