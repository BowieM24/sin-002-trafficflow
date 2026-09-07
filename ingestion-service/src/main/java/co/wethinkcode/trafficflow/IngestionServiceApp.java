package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import java.io.*;
import java.util.*;

public class IngestionServiceApp {

    // Define standard headers
    private static final String[] HEADERS = {"intersection_id", "district", "signal_Type", "active_flag"};

    public static void main(String[] args) throws IOException {
        Javalin app = Javalin.create().start(7020);

        // Call the CSV cleaning method and make it avaliable for other services to consume
        List<String[]> cleanedData = cleanIntersectionsCsv("intersections-legacy.csv");

        // Print representation to console
        String csvOutput = formatAsCsv(HEADERS, cleanedData);
        System.out.println(csvOutput);

        app.get("/intersections", ctx -> {
            ctx.contentType("text/csv");
            ctx.result(csvOutput);
        });
        app.get("/health", ctx -> ctx.result("OK"));
    }

    /**
     * Reads a CSV file from the classpath, cleans the data and returns a list
     * of maps matching the target JSON schema.
     *
     * @param fileName the name of the CSV file located in src/main/resources
     * @return a list of cleaned rows respresented as maps
     */
    public static List<String[]> cleanIntersectionsCsv(String fileName) throws IOException {
        // Create a list to hold cleaned rows
        List<String[]> cleanedRows = new ArrayList<>();

        // Use a Set to track duplicate rows (as strings) for removal
        Set<String> seenRows = new LinkedHashSet<>();

        // Obtain the input stream from the classpath
        InputStream inputStream = IngestionServiceApp.class.getClassLoader().getResourceAsStream(fileName);

        // Check if the file was found
        if (inputStream == null) {
            // If the file is not found, throw a FileNotFoundException
            System.err.println("file not found: " + fileName);
            /// Return an empty list if the file is not found
            return cleanedRows;
        }

        // Try-with-resources: the BufferedReader will be closed automatically 
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            boolean isFirstLine = true;

            // Track the index of the district column for capitalization of first letter
            int districtIdColumnIndex = -1;
            // Track the index of the intersection_id column for capitalization
            int intersectionIdColumnIndex = -1;
            // Track the index of signalType column for lower case
            int signalTypeColumnIndex = -1;
            // Track the index of active column
            int activeColumnIndex = -1;

            // Read each line from the CSV file
            while ((line = reader.readLine()) != null) {
                // Remove BOM (Byte Order Mark) if present at the beginning of the file
                if (isFirstLine && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                // Trim leading/trailing whitespace
                line = line.trim();

                // Skip empty lines
                if (line.isEmpty()) {
                    continue;
                }

                // Split the line by comma, -1 keeps trailing empty strings
                String[] parts = line.split(",", -1);

                // Clean each field: trim whitespace and replace empty strings with null
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i].trim();
                }

                // Handle the header row to find the district column index
                if (isFirstLine) {
                    // Look through all the columns in the header
                    for (int i = 0; i < parts.length; i++) {
                        String header = parts[i].toLowerCase();
                        // Inside "intersection"
                        if (header.equals("intersection_id") || header.equals("id")) {
                            intersectionIdColumnIndex = i;
                            // Inside "district"
                        } else if (header.equals("district")) {
                            districtIdColumnIndex = i;
                            // Inside "signal_type"
                        } else if (header.equals("signal_type") || header.equals("signaltype")) {
                            signalTypeColumnIndex = i;
                            // Inside active
                        } else if (header.equals("active") || header.equals("active_flag")) {
                            activeColumnIndex = i;
                        }
                    }
                    isFirstLine = false;
                    continue;
                }
                // Extract and clean intersection_Id (Uppercase)
                String id = getField(parts, intersectionIdColumnIndex);
                if (id != null) {
                    id = id.toUpperCase();
                }

                // De-duplicate based on unique intersection ID
                if (id == null || !seenRows.add(id)) {
                    continue;
                }
                // Extract and format Distract (Title Case)
                String district = capitalizeFirstLetter(getField(parts, districtIdColumnIndex));

                // Extract and format signalType (lower Case)
                String signalType = getField(parts, signalTypeColumnIndex);
                if (signalType != null) {
                    signalType = signalType.toLowerCase();
                }

                // Clean Active flag
                Boolean activeBool = parseActiveFlag(getField(parts, activeColumnIndex));
                String active = activeBool != null ? activeBool.toString() : null;

                // Align directly with the HEADERS array order:
                // [id, district, signal_Type, active]
                cleanedRows.add(new String[]{
                    id != null ? id : "",
                    district != null ? district : "",
                    signalType != null ? signalType : "",
                    active != null ? active : ""
                });
            }
        }
        return cleanedRows;
    }

    private static String formatAsCsv(String[] headers, List<String[]> rows) {
        StringBuilder csv = new StringBuilder();
        // Append headers
        csv.append(String.join(",", headers));
        csv.append("\n");
        // Append rows
        for (String[] row : rows) {
            csv.append(String.join(",", row));
            csv.append("\n");
        }
        return csv.toString();
    }

    // Helper Method
    private static String getField(String[] parts, int index) {
        if (index < 0 || index >= parts.length || parts[index].isEmpty()) {
            return null;
        }
        return parts[index];
    }
 
    private static String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    private static Boolean parseActiveFlag(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        String normalized = input.trim().toLowerCase();

        return normalized.equals("1") || normalized.equals("y") || normalized.equals("yes") || normalized.equals("true") || normalized != null && normalized.equals("false");
    }
}
