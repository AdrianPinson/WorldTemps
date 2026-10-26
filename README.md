# WorldTemps

Explore daily temperature CSV data with a reusable Java analysis core. Originally a
linked-list coursework project, WorldTemps is being developed into an interactive
temperature dashboard. This first milestone repairs the core and retains a working
console interface; the graphical dashboard is **not implemented yet**.

## Run locally

Requirements: **JDK 17+** and **Maven 3.9+**.

```sh
mvn clean verify
java -jar target/worldtemps.jar
```

The executable JAR includes its dependencies and an 18-reading sample covering
Longview, London, and Tokyo. Sample values are **invented demonstration data in
Fahrenheit**, not historical observations. It works from any working directory.

To use your own data:

```sh
java -jar target/worldtemps.jar /path/to/city_temperature.csv
java -jar target/worldtemps.jar --help
```

## Current features

- Overall minimum and maximum temperatures.
- Minimum by country and maximum by region.
- Maximum and chronological history for a country/state/city combination, avoiding
  accidental merging of cities with identical names.
- Case-insensitive location searches with surrounding whitespace ignored.
- Bounded relative console bars for city history, including negative temperatures.
- Quoted CSV fields, validation, and a rejected-record count with up to 20 diagnostic examples.
- JUnit regression tests and a GitHub Actions build/test workflow.

## CSV contract

UTF-8 CSV with a header containing these columns (order and capitalization may vary):

```csv
Region,Country,State,City,Month,Day,Year,AvgTemperature
North America,US,Texas,Longview,1,1,2025,45.0
Europe,UK,,London,1,1,2025,38.0
```

Temperatures are interpreted as **Fahrenheit**. `-99` is treated as the original
dataset's missing-temperature marker and excluded; this convention also means an
actual value of -99 °F cannot be imported using this format. Other finite negative
temperatures are valid. Dates must be calendar-valid. Region, country, and city are
required; state may be blank. Location spellings are matched as provided (for
example, `US` and `United States` are distinct labels).

Invalid individual records are skipped and counted. Missing required headers or
malformed CSV quoting fail the import instead of exposing a partial dataset.
Diagnostic record numbers count the header as record 1, not physical lines when
quoted fields contain newlines. Duplicate readings are retained as separate source
records; this milestone does not deduplicate or aggregate them.

## Design

| Component | Responsibility |
| --- | --- |
| `model/Reading` | Immutable location, date, and Fahrenheit temperature |
| `collection/ReadingList` | Custom linked-list nodes, append, and iteration |
| `importer/CsvTemperatureLoader` | CSV parsing and validation diagnostics |
| `service/TemperatureData` | UI-independent min/max and city-history queries |
| `WorldTemps` | Console input, output, and process exit status |

The custom linked list preserves the project's data-structures foundation, but
readings no longer carry mutable `next` pointers. Counts belong to individual
lists. The analysis service snapshots its input and exposes immutable query results.
Queries currently scan the list (O(n)); sorting city history is O(k log k) for k
matching records. Imports hold the dataset in memory. No large-dataset performance
claims are made at this stage.

## Validation

`mvn clean verify` runs tests for linked-list ordering and isolation, extrema,
same-name cities, chronological history, CSV quoting, invalid dates, missing data,
nonfinite temperatures, diagnostics, console workflows, and error exit statuses.
CI also launches the packaged JAR against the bundled sample.

## Next milestone

After approval: add the JavaFX dashboard with location filters, summary cards,
temperature charts, and a reading table. Subsequent work will add aggregation,
city comparisons, background imports, export, and measured query performance.

The original coursework files remain available in Git history.
