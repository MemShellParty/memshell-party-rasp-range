#!/usr/bin/env bash
# Start a built range image and inject every Godzilla/Behinder/Suo5 combination
# the running middleware advertises. Requires only docker and curl on the host.
#
#   ./docker/verify-range.sh harbor.corp.boundaryx.net/cloudrasp/memshell-range-tomcat-javax:0.1.0
#   TOOLS=Godzilla SUITE=javax-tomcat ./docker/verify-range.sh <image>
#
# Every combination is injected twice: once plain and once with obfuscate=true.
# Set MODES=plain or MODES=obfuscated to run a single mode.
# Against a remote docker host set VERIFY_BIND=0.0.0.0 and VERIFY_HOST=<host>,
# because a 127.0.0.1 publish binds on the daemon's loopback, not this machine.
set -euo pipefail

image=${1:?usage: verify-range.sh <image> [context-path]}
# ${2-} keeps an explicitly empty context path (Spring ranges serve at /).
context_path=${2-/range}
tools=${TOOLS:-Godzilla,Behinder,Suo5}
name=${SUITE:-$(basename "${image%%:*}")}
log_dir=${LOG_DIR:-/tmp/memshell-range-logs}
container="memshell-verify-$name-$$"
mkdir -p "$log_dir"
catalog_file="$log_dir/$name.catalog.json"
results_file="$log_dir/$name.results.tsv"
: >"$results_file"

cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker run -d --name "$container" -p "${VERIFY_BIND:-127.0.0.1}::8080" "$image" >/dev/null
port=$(docker port "$container" 8080/tcp | head -1 | sed 's/.*://')
base="http://${VERIFY_HOST:-127.0.0.1}:$port$context_path"

for _ in $(seq 1 180); do
  if curl -fsS --max-time 5 "$base/api/runtime" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl -fsS --max-time 10 "$base/api/runtime" >/dev/null
curl -fsS --max-time 10 "$base/api/catalog" >"$catalog_file"

server=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["server"])' "$catalog_file")
pairs=$(python3 -I -c '
import json, sys
data = json.load(open(sys.argv[1]))
wanted = set(sys.argv[2].split(","))
for tool, mounts in data["matrix"].items():
    if tool in wanted:
        for mount in mounts:
            print(tool + "\t" + mount)
' "$catalog_file" "$tools")

modes=${MODES:-plain,obfuscated}
total=0
failed=0
while IFS=$'\t' read -r tool mount; do
  [[ -z "$tool" ]] && continue
  for mode in ${modes//,/ }; do
    total=$((total + 1))
    query="server=$server&tool=$tool&mount=$mount"
    if [[ "$mode" == "obfuscated" ]]; then
      query="$query&obfuscate=true"
    fi
    url="$base/entrypoint?$query"
    response=$(curl -sS --max-time 150 -w '\n%{http_code}' "$url") || response=$'\n000'
    status=${response##*$'\n'}
    body=${response%$'\n'*}
    verdict=$(printf '%s' "$body" | python3 -I -c '
import json, sys
try:
    data = json.load(sys.stdin)
except Exception:
    print("bad-json")
    sys.exit()
if data.get("defined") or data.get("attached"):
    print("ok")
else:
    print("fail:" + str(data.get("error") or "not defined"))
' 2>/dev/null || echo bad-json)
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$mode" "$status" "$tool" "$mount" "$verdict" "$body" >>"$results_file"
    if [[ "$verdict" == "ok" ]]; then
      printf 'ok   %-9s %-10s %s\n' "$mode" "$tool" "$mount"
    else
      failed=$((failed + 1))
      printf 'FAIL %-9s %-10s %s http=%s %s\n' "$mode" "$tool" "$mount" "$status" "$verdict"
    fi
  done
done <<<"$pairs"

echo "==> $name: $((total - failed))/$total injected (catalog: $catalog_file, results: $results_file)"
[[ "$failed" -eq 0 ]]
