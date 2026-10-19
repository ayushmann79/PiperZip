#!/usr/bin/env bash
# Reproducible GZIP benchmark using PiperZip's `stats` command (compresses into a counting sink, no disk writes).
# Usage: scripts/benchmark.sh target/piperzip-<version>.jar   (or set PIPERZIP_CMD to a custom command)
set -euo pipefail

CMD=${PIPERZIP_CMD:-"java -jar ${1:?usage: benchmark.sh <piperzip.jar>}"}
SIZE_MB=${SIZE_MB:-32}
RUNS=${RUNS:-3}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

echo "# $(java -version 2>&1 | head -n 1) on $(uname -sm)"
echo "# dataset size: ${SIZE_MB} MiB, runs per cell: ${RUNS} (median shown)"

# Deterministic datasets
python3 - "$work" "$SIZE_MB" <<'PY'
import random, sys
d, mb = sys.argv[1], int(sys.argv[2]); n = mb << 20
random.seed(1234)
open(f"{d}/repetitive.txt", "wb").write((b"The quick brown fox jumps over the lazy dog.\n" * (n // 45 + 1))[:n])
open(f"{d}/numbers.txt", "wb").write(("\n".join(str(i) for i in range(n // 7)) + "\n").encode()[:n])
open(f"{d}/random.bin", "wb").write(random.randbytes(n))
PY

printf '%-16s %5s %14s %14s %8s %10s\n' dataset level original compressed ratio ms
for file in repetitive.txt numbers.txt random.bin; do
  for level in 1 6 9; do
    times=()
    for _ in $(seq "$RUNS"); do
      json=$($CMD stats --json --level "$level" "$work/$file")
      times+=("$(sed -E 's/.*"elapsedMillis":([0-9]+).*/\1/' <<<"$json")")
    done
    median=$(printf '%s\n' "${times[@]}" | sort -n | sed -n "$(( (RUNS + 1) / 2 ))p")
    orig=$(sed -E 's/.*"originalBytes":([0-9]+).*/\1/' <<<"$json")
    comp=$(sed -E 's/.*"compressedBytes":([0-9]+).*/\1/' <<<"$json")
    ratio=$(sed -E 's/.*"ratio":([0-9.]+).*/\1/' <<<"$json")
    printf '%-16s %5s %14s %14s %8s %10s\n' "$file" "$level" "$orig" "$comp" "$ratio" "$median"
  done
done
