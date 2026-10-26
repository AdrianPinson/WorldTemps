package io.github.adrianpinson.worldtemps;

import io.github.adrianpinson.worldtemps.importer.CsvTemperatureLoader;
import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.TemperatureData;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;

/** Console entry point retained while the desktop dashboard is developed separately. */
public final class WorldTemps {
    private WorldTemps() { }

    public static void main(String[] args) {
        int status = run(args, System.in, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(String[] args, InputStream input, PrintStream out, PrintStream err) {
        if (args.length > 1) {
            err.println("Usage: java -jar worldtemps.jar [temperature-data.csv]");
            return 2;
        }
        if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
            out.println("Usage: java -jar worldtemps.jar [temperature-data.csv]");
            out.println("No file argument loads the bundled synthetic Fahrenheit sample.");
            return 0;
        }
        try {
            CsvTemperatureLoader.ImportResult result;
            try (Reader reader = openData(args)) {
                result = new CsvTemperatureLoader().load(reader);
            }
            out.println(args.length == 0 ? "WorldTemps — synthetic demo data (°F)" : "WorldTemps — imported data (°F)");
            out.printf("Loaded %d readings; rejected %d records.%n", result.data().size(), result.rejectedRows());
            result.issues().forEach(issue -> out.printf("Record %d: %s%n", issue.recordNumber(), issue.reason()));
            if (result.rejectedRows() > result.issues().size()) {
                out.printf("%d additional rejected records omitted.%n", result.rejectedRows() - result.issues().size());
            }
            if (result.data().size() == 0) {
                err.println("No valid readings. Check your CSV and try again.");
                return 1;
            }
            menu(result.data(), new Scanner(input, StandardCharsets.UTF_8), out);
            return 0;
        } catch (IOException | IllegalArgumentException e) {
            err.println("Unable to load temperature data: " + e.getMessage());
            return 1;
        }
    }

    private static Reader openData(String[] args) throws IOException {
        if (args.length == 1) {
            return Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8);
        }
        InputStream resource = WorldTemps.class.getResourceAsStream("/data/sample-temperatures.csv");
        if (resource == null) {
            throw new IOException("Bundled sample data is missing");
        }
        return new InputStreamReader(resource, StandardCharsets.UTF_8);
    }

    private static void menu(TemperatureData data, Scanner scanner, PrintStream out) {
        while (true) {
            out.println("\n1. Overall minimum\n2. Overall maximum\n3. Minimum by country"
                    + "\n4. Maximum by region\n5. Maximum by city\n6. City history\n0. Exit");
            String choice = prompt(scanner, out, "Choice: ");
            if (choice == null || choice.equals("0")) {
                out.println("Goodbye!");
                return;
            }
            switch (choice) {
                case "1" -> show(data.minimum(), out);
                case "2" -> show(data.maximum(), out);
                case "3" -> show(data.minimumByCountry(prompt(scanner, out, "Country: ")), out);
                case "4" -> show(data.maximumByRegion(prompt(scanner, out, "Region: ")), out);
                case "5", "6" -> {
                    String country = prompt(scanner, out, "Country: ");
                    String state = prompt(scanner, out, "State (blank if none): ");
                    String city = prompt(scanner, out, "City: ");
                    if (city == null) {
                        return;
                    }
                    if (choice.equals("5")) {
                        show(data.maximumByCity(country, state, city), out);
                    } else {
                        showHistory(data.cityHistory(country, state, city), out);
                    }
                }
                default -> out.println("Invalid option. Enter a number from 0 to 6.");
            }
        }
    }

    private static String prompt(Scanner scanner, PrintStream out, String label) {
        out.print(label);
        return scanner.hasNextLine() ? scanner.nextLine().trim() : null;
    }

    private static void show(Optional<Reading> reading, PrintStream out) {
        out.println(reading.map(Reading::toString).orElse("No matching readings."));
    }

    private static void showHistory(List<Reading> readings, PrintStream out) {
        if (readings.isEmpty()) {
            out.println("No matching readings.");
            return;
        }
        double min = readings.stream().mapToDouble(Reading::averageTemperatureF).min().orElseThrow();
        double max = readings.stream().mapToDouble(Reading::averageTemperatureF).max().orElseThrow();
        out.println("Relative bars (shortest = minimum, longest = maximum for this selection):");
        readings.forEach(r -> {
            double fraction = max == min ? 1 : (r.averageTemperatureF() - min) / (max - min);
            int width = 1 + (int) Math.round(39 * fraction);
            out.printf(Locale.ROOT, "%s | %s %.1f °F%n", r.date(), "*".repeat(width), r.averageTemperatureF());
        });
    }
}
