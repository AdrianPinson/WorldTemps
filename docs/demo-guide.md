# Demo and interview guide

## A two-minute walkthrough

1. Launch the app with `--demo` to show the offline sample immediately. Explain
   that the source badge distinguishes invented demo data from API and CSV data.
2. Compare Longview, London, and Tokyo. Switch from raw readings to monthly means;
   the table and cards still summarize the raw selection.
3. Sort the temperature column and export either the displayed readings or full
   chart periods. The raw export can be imported back into WorldTemps.
4. Open Find weather, search for Longview, and show how state/country and coordinates
   distinguish similarly named locations. Select historical dates and load.
5. Inspect Source details: ERA5 reanalysis, daily means, fetched time, timezone,
   network/cache origin, and unavailable days. Reload the same range to reuse cache.

## Technical decisions to explain

- Preserve the original custom linked-list project behind immutable reading models.
- Keep API parsing, CSV handling, filtering/indexing, aggregation, and UI concerns separate.
- Validate all city responses before replacing a dataset, so partial failures do not
  leave the screen showing a mixture of old and new data.
- Treat missing values as missing, preserve duplicate source rows explicitly, and
  calculate yearly means from raw records rather than averages of monthly means.
- Use binary date bounds on sorted location buckets and merge up to three ranges.
  Discuss one-time index cost and memory tradeoffs, not just the selective timing.
- Test indexed results against an independent scan reference rather than asserting
  speed on a shared CI runner.
- Build app images with Java, JavaFX dependencies, cryptographic support, provenance,
  checksums, and extracted-package smoke tests.

## Resume wording

Use only claims you can demonstrate and explain:

- Built a JavaFX historical temperature analytics application integrating
  Open-Meteo APIs, asynchronous loading, validated offline caching, multi-city
  comparisons, and CSV import/export.
- Implemented indexed location/date queries and a reproducible 500,000-row
  selective-query benchmark; added regression tests and cross-platform desktop
  packaging through GitHub Actions.

Precise benchmark figures belong with the workload and measurement conditions in
`performance.md`; they do not describe API latency or overall application speed.
The repository currently follows its existing access settings, so arrange source
or demo access before sharing it with a resume reviewer.
