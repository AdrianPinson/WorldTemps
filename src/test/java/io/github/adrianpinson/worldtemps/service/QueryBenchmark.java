package io.github.adrianpinson.worldtemps.service;

import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.*;
import java.time.LocalDate;
import java.util.*;

/** Reproducible informational microbenchmark; no timing thresholds are used as tests. */
public final class QueryBenchmark {
    private static volatile long sink;
    private QueryBenchmark() { }
    public static void main(String[] args) {
        int cityCount = 500;
        int days = 1000;
        LocalDate start = LocalDate.of(2020, 1, 1);
        List<Reading> rows = new ArrayList<>(cityCount * days);
        List<String> names = new ArrayList<>();
        for (int city = 0; city < cityCount; city++) { names.add(String.format(Locale.ROOT, "City%04d", city)); }
        for (int day = 0; day < days; day++) {
            LocalDate date = start.plusDays(day);
            for (int city = 0; city < cityCount; city++) {
                rows.add(new Reading("Region", "Country", "", names.get(city), date, 30 + (day + city) % 70));
            }
        }
        Collections.shuffle(rows, new Random(42));
        TemperatureData data = new TemperatureData(rows);
        long construction = System.nanoTime();
        DashboardQuery indexed = new DashboardQuery(data);
        construction = System.nanoTime() - construction;
        List<Filter> filters = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            int offset = index * 17 % (days - 30);
            filters.add(new Filter(null, null, new Location("Country", "", names.get(index * 7 % cityCount)),
                    start.plusDays(offset), start.plusDays(offset + 29)));
        }
        for (Filter filter : filters) {
            if (!ReferenceScan.apply(rows, filter, List.of()).equals(indexed.apply(filter))) {
                throw new AssertionError("Benchmark results differ");
            }
        }
        for (int warmup = 0; warmup < 5; warmup++) { measure(rows, indexed, filters, false); measure(rows, indexed, filters, true); }
        double[] scans = new double[10];
        double[] indexes = new double[10];
        for (int round = 0; round < 10; round++) {
            if (round % 2 == 0) {
                scans[round] = measure(rows, indexed, filters, false);
                indexes[round] = measure(rows, indexed, filters, true);
            } else {
                indexes[round] = measure(rows, indexed, filters, true);
                scans[round] = measure(rows, indexed, filters, false);
            }
        }
        Arrays.sort(scans); Arrays.sort(indexes);
        double scanMedian = (scans[4] + scans[5]) / 2;
        double indexMedian = (indexes[4] + indexes[5]) / 2;
        System.out.printf(Locale.ROOT, "Dataset: %,d shuffled synthetic readings; %d cities; %d days; seed=42%n", rows.size(), cityCount, days);
        System.out.println("Workload: 50 distinct one-city / inclusive 30-day queries; 5 warmup rounds; 10 measured rounds; alternating order.");
        System.out.printf(Locale.ROOT, "Index construction (one cold build): %.1f ms%n", construction / 1_000_000.0);
        System.out.printf(Locale.ROOT, "Scan/sort median: %.3f ms/query%nIndexed median: %.3f ms/query%nSelective-query ratio: %.1fx%n",
                scanMedian, indexMedian, scanMedian / indexMedian);
        System.out.println("JVM: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("These measurements exclude ingestion, index construction, rendering, API latency, and export. No general UI speedup is claimed.");
    }

    private static double measure(List<Reading> rows, DashboardQuery indexed, List<Filter> filters, boolean useIndex) {
        long begin = System.nanoTime();
        long count = 0;
        for (Filter filter : filters) {
            Result result = useIndex ? indexed.apply(filter) : ReferenceScan.apply(rows, filter, List.of());
            count += result.readings().size();
        }
        sink = count;
        return (System.nanoTime() - begin) / 1_000_000.0 / filters.size();
    }
}
