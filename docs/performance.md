# Query performance

WorldTemps builds an immutable chronological snapshot and per-location indexes
when a dataset is loaded. Each location bucket is already sorted. A selected city
and inclusive date range use a location lookup plus binary lower/upper bounds;
up to three selected ranges are combined with a small priority queue. The result
does not need to be sorted again. Filter options are derived from unique geographic
options rather than scanning every reading on each dropdown change.

Index construction runs on the background import worker, before the new dataset
replaces the current view. The original custom linked-list ingestion remains in
`TemperatureData`; index entries share immutable reading objects.

## Measured example

Measured on October 26, 2026 in the managed Linux development environment:

| Item | Result |
| --- | --- |
| Input | 500,000 shuffled synthetic readings; 500 cities × 1,000 dates |
| Query | One city, inclusive 30-day range; 30 matching records |
| Query set | 50 distinct queries |
| Warmup | Five rounds of each algorithm |
| Measurement | Ten rounds; algorithm order alternates |
| JVM | OpenJDK 17.0.20, Linux amd64, fixed 512 MiB heap |
| Index build | 815.7 ms for one cold construction |
| Old scan/sort | 29.504 ms per query, median of round averages |
| Indexed query | 0.012 ms per query, median of round averages |

This selective workload favors an index because it retrieves 30 records from a
500,000-row dataset. The timings exclude CSV ingestion, index construction, chart
rendering, HTTP requests, aggregation, and export. They are not an end-to-end app
speedup or a production guarantee. This is an informational single-JVM
microbenchmark, not a JMH/multi-fork performance study; shared environment, JIT,
and garbage collection affect results. Reproduce locally before using precise
numbers outside this context.

## Reproduce

```sh
python scripts/benchmark.py
```

The script compiles test sources and starts a separate JVM with `-Xms512m -Xmx512m`.
Data uses seed 42. The benchmark first checks every measured query against the
retained pre-index scan implementation. It consumes each result count and reports
build time and median per-query timing. No wall-clock thresholds gate tests.

`IndexedQueryTest` separately compares full rows and summaries against the old
algorithm over 400 deterministic randomized filters, including duplicates,
comparisons, geography, and dates. Additional tests cover Unicode case aliases,
same-label buckets in different regions, and boundary duplicates.

## Tradeoffs

The one-time build sorts N records, so it costs O(N log N) and extra O(N) index
references. A selected-location query uses logarithmic lookup/date bounds, then
visits candidate records and calculates the result summary. All-location queries
still visit the selected chronological range, and broad results require time and
memory proportional to their size. CSV imports remain memory-resident. The
1,000-point-per-city plot limit does not truncate exports or summary data.
