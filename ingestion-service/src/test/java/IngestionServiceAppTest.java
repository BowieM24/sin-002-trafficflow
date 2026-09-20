package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class IngestionServiceAppTest {

    private static List<String[]> cleanedData;

    @BeforeAll
    public static void setup() throws Exception {
        // Load the CSV once for all tests in this class
        cleanedData = IngestionServiceApp.cleanIntersectionsCsv("intersections-legacy.csv");
    }

    @Test
    public void testDataLoadsSuccessfully() {
        assertNotNull(cleanedData, "The returned list should not be null");
        assertFalse(cleanedData.isEmpty(), "The cleaned data list should not be empty");
    }

    @Test
    public void testRowStructureIsCorrect() {
        String[] firstRow = cleanedData.get(0);
        assertEquals(4, firstRow.length, "Each row should contain exactly 4 columns");
    }

    @Test
    public void testIntersectionIdFormatting() {
        String[] firstRow = cleanedData.get(0);
        // Verify the ID is entirely uppercase and contains only valid characters
        assertTrue(firstRow[0].matches("^[A-Z0-9-]+$"), "Intersection ID should be uppercase");
    }

    @Test
    public void testActiveFlagFormatting() {
        String[] firstRow = cleanedData.get(0);
        // Verify the flag was correctly converted to a boolean string representation
        assertTrue(firstRow[3].equals("true") || firstRow[3].equals("false") || firstRow[3].isEmpty(), 
            "Active flag should be normalized to true, false, or empty");
    }
}