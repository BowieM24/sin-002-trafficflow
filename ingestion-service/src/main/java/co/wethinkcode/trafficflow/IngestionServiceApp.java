package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import java.io.*;
import java.util.*;

public class IngestionServiceApp {

    public static void main(String[] args) throws IOException {
        Javalin app = Javalin.create().start(7020);

        app.get("/health", ctx -> ctx.result("OK"));

        // Call the CSV cleaning method and make it avaliable for other services to consume
        List<String[]> cleanedData = cleanIntersectionsCsv("intersections-legacy.csv");

        for (String[] row : cleanedData) {
            System.out.println(String.join(",", row));
        }
    }

    /**
     * Reads a CSV file from the classpath, cleans the data and returns a list of strig arrays.
     * @param fileName the name of the CSV file located in src/main/resources
     * @return a list of cleaned rows (each row is an array of strings) 
     */
    public static List<String[]> cleanIntersectionsCsv(String fileName) throws IOException {
        // Create a list to hold cleaned rows
        List<String[]> cleanedRows = new ArrayList<>();
        
        // Use a Set to track duplicate rows (as strings) for removal
        Set<String> seenRows = new LinkedHashSet<>();

        // Track the index of the district column for capitalization of first letter
        int districtColumnIndex = -1;
        // Track the index of the intersection_id column for capitalization
        int intersectionIdColumnIndex = -1;

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
                for (int i=0; i <parts.length; i++) {
                    parts[i] = parts[i].trim();
                    if (parts[i].isEmpty()) {
                        parts[i] = null;
                    }
                }

                // Handle the header row to find the district column index
                if (isFirstLine) {
                    // Look through all the columns in the header
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i] != null) {
                            // Inside "intersection"
                            if (parts[i].equalsIgnoreCase("intersection_id")) {
                                intersectionIdColumnIndex = i;
                            } else if (parts[i].equalsIgnoreCase("district")) {
                                districtColumnIndex = i;
                            }
                        }
                    }
                    isFirstLine = false;
                } else {
                    // Apply district 1st letter capitalization
                    if (districtColumnIndex >= 0 && districtColumnIndex < parts.length && parts[districtColumnIndex] != null) {
                        // Capitalize 1st letter of district column
                        parts[districtColumnIndex] = capitalizeFirstLetter(parts[districtColumnIndex]);
                        // Apply UpperCase to intersection_id
                        if (intersectionIdColumnIndex >= 0 && intersectionIdColumnIndex < parts.length && parts[intersectionIdColumnIndex] != null) {
                            parts[intersectionIdColumnIndex] = capitalizeIntersectionId(parts[intersectionIdColumnIndex]);
                        }
                    }

                    // Skip rows that are completely null after cleaning
                    boolean allNull = true;
                    for (String part : parts) {
                        if (part != null) {
                            allNull = false;
                            break;
                        }
                    }
            
                    if (allNull) {
                        continue;
                    }

                    // Convert the row to a single string for duplicate detection
                    String rowKey = String.join("\uFEFF", parts);

                    if (seenRows.add(rowKey)) {
                        // If the row is unique, add it to the cleaned rows list
                        cleanedRows.add(parts);
                    }
                } // Close while loop
            } // Close try-with-resources block
        } catch (IOException e) {
            // Handle I/O errors
            System.err.println("Error reading csv file: " + e.getMessage());
        }

        return cleanedRows;
    }

    /**
     * Capitalizes the first letter of a string and makes the rest lowercase.
     * @param input the string to capitalize
     * @return the string with first letter capitalized
     */
    private static String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }
    /**
     * Capitalizes the entire string to uppercase.
     * @param input the string to convert to uppercase
     * @return the string in uppercase
     */
    private static String capitalizeIntersectionId(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.toUpperCase();
    }
}

