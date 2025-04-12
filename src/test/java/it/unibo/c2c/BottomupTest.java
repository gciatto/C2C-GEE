package it.unibo.c2c;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

import static it.unibo.c2c.DoubleLists.doubleListOf;

@RunWith(JUnit4.class)
public class BottomupTest {

    private static final String SAMPLES_FILE = "input.csv";
    private static final String EXPECTED_FILE = "output.csv";

    private static Csv inputs;
    private static Csv expected;

    @BeforeClass
    public static void loadFiles() {
        // Read input file.  It has dates as column headers and each row is a full timeline.
        inputs = Csv.vertical(BottomupTest.class.getResourceAsStream(SAMPLES_FILE));
        // Read expected results file
        expected = Csv.vertical(BottomupTest.class.getResourceAsStream(EXPECTED_FILE));
    }

    @Test
    public void testGoldens() throws Exception {
        var dates = doubleListOf(inputs.headers().stream().skip(1).mapToDouble(Double::parseDouble));
        int numberOfInputs = inputs.values().getFirst().size();
        //  Split expected results file by plot ID.
        List<Csv> expected = BottomupTest.expected.groupByColumn("id");
        assertEquals(numberOfInputs, expected.size());
        // Apply the Main function on each timeLine.
        int nullCount = 0;
        C2cSolver solver = new C2cSolver();
        for (int i = 0; i < numberOfInputs; i++) {
            // The inputs have a plot ID in the first column that isn't used in the timeline.  Skip it.
            DoubleList timeline = inputs.getRow(i, /* skip= */ 1);
            List<Changes> result = solver.c2cBottomUp(dates, timeline);
            if (result != null) {
                verify(result, expected.get(i));
            } else {
                nullCount++;
            }
        }
        // There are 3 inputs that don't have enough points.
        assertEquals(3, nullCount);
    }

    /**
     * Verify that the changes match the expected values.
     */
    private void verify(List<Changes> actual, Csv expected) {
        List<DoubleList> values = expected.values();
        assertEquals(actual.size(), values.getFirst().size());
        for (int j = 0; j < actual.size(); j++) {
            Changes c = actual.get(j);
            assertEquals(c.date(), expected.getColumn("year").getDouble(j));
            assertEquals(c.value(), expected.getColumn("index").getDouble(j));
            assertEquals(c.duration(), expected.getColumn("duration").getDouble(j));
            assertEquals(c.magnitude(), expected.getColumn("magnitude").getDouble(j));
            assertEquals(c.postMagnitude(), expected.getColumn("postMagnitude").getDouble(j));
            assertEquals(c.postDuration(), expected.getColumn("postDuration").getDouble(j));
            assertEquals(c.postRate(), expected.getColumn("postRate").getDouble(j));
            assertEquals(c.rate(), expected.getColumn("rate").getDouble(j));
        }
    }

    private void assertEquals(double actual, double expected) {
        Assert.assertEquals(expected, actual, 1e-9);
    }
}
