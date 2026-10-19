# Benchmarking plan (planned, not yet run)

No benchmark results are published, because none have been produced and verified. This document describes how to
produce them reproducibly.

`scripts/benchmark.sh` times PiperZip's `stats` command (compression into a counting sink: no disk writes) on
generated datasets at each level, and prints size, ratio and time. Run it yourself:

```bash
mvn -DskipTests package
scripts/benchmark.sh target/piperzip-*.jar
```

Datasets are generated deterministically (repetitive text, numeric text, random bytes), so results differ only by
hardware and JVM. Rules for honest comparisons:

- Same data, same level, warm JVM, several runs; report the median and the machine.
- Compare GZIP with GZIP and ZIP with ZIP. A ZIP of one file is larger than the GZIP of it because of container
  overhead (headers and central directory); that is not a compression difference.
- Never claim "better" without the raw numbers and the command that produced them.
- Keep benchmarks out of normal CI: they are slow and noisy on shared runners. Run them on demand
  (`workflow_dispatch`) if at all.

Future work: memory profiling (`-Xmx` sweeps), JMH micro-benchmarks, and optional LZ4 / Zstandard codecs behind
`CompressionCodec` (check each library's licence and compatibility first).
