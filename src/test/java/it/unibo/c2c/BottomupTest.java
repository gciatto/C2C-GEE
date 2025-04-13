package it.unibo.c2c;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;

@RunWith(JUnit4.class)
public class BottomupTest {

    private static final String SAMPLES_FILE = "input.csv";
    private static final String EXPECTED_FILE_DEFAULT = "output-default.csv";
    private static final String EXPECTED_FILE_REVERT = "output-reverted.csv";
    private static final String EXPECTED_FILE_FILTER = "output-filtered.csv";

    private static Csv inputs, expectedDefault, expectedRevert, expectedFilter;

    @Rule
    public TestName testName = new TestName();

    public int lastID;

    @BeforeClass
    public static void loadFiles() {
        // Read input file.  It has dates as column headers and each row is a full timeline.
        inputs = Csv.vertical(BottomupTest.class.getResourceAsStream(SAMPLES_FILE));
        // Read expected results files
        expectedDefault = Csv.vertical(BottomupTest.class.getResourceAsStream(EXPECTED_FILE_DEFAULT));
        expectedRevert = Csv.vertical(BottomupTest.class.getResourceAsStream(EXPECTED_FILE_REVERT));
        expectedFilter = Csv.vertical(BottomupTest.class.getResourceAsStream(EXPECTED_FILE_FILTER));
    }

    @Test
    public void testC2cBottomUpWithDefaultArgs() {
        testC2cBottomUpWithArgs(new C2cSolver.Args(), expectedDefault);
    }

    @Test
    public void testC2cBottomUpWithRevertBand() {
        var args = new C2cSolver.Args();
        args.revertBand = true;
        testC2cBottomUpWithArgs(args, expectedRevert);
    }

    @Test
    public void testC2cBottomUpWithNegativeMagnitudeOnly() {
        var args = new C2cSolver.Args();
        args.negativeMagnitudeOnly = true;
        testC2cBottomUpWithArgs(args, expectedFilter);
    }

    private void testC2cBottomUpWithArgs(C2cSolver.Args args, Csv expected) {
        var dates = inputs.getHeadersAsDoubles();
        //  Split expectedById results file by plot ID.
        Map<Double, Csv> expectedById = expected.groupByColumn("id");
//        assertEquals(inputs.getRowsCount(), expectedById.size());
        // Apply the Main function on each timeLine.
        int nullCount = 0;
        C2cSolver solver = new C2cSolver(args);
        for (int i = 0; i < inputs.getRowsCount(); i++) {
            // The inputs have a plot ID in the first column that isn't used in the timeline.  Skip it.
            DoubleList timeline = inputs.getRow(i, /* skip= */ 1);
            List<Changes> result = solver.c2cBottomUp(dates, timeline);
            Double id = Double.valueOf(i);
            Csv expectedCsv = expectedById.getOrDefault(id, Csv.empty(expected.headers()));
            if (result != null) {
                verify(i, result, expectedCsv);
            } else {
                nullCount++;
                System.out.printf("Null result for row: %d\n", i);
                assertFalse(expectedById.containsKey(id));
                verify(i, List.of(), expectedCsv);
            }
        }
        // There are 3 inputs that don't have enough points.
        assertEquals(3, nullCount);
    }

    /**
     * Verify that the changes match the expected values.
     */
    private void verify(int id, List<Changes> actual, Csv expected) {
        this.lastID = id;
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
        Assert.assertEquals(
                "Failed equality assertion in %s, row with ID %d: %s != %s".formatted(
                        testName.getMethodName(),
                        lastID,
                        expected,
                        actual
                ),
                expected,
                actual,
                1e-9
        );
    }
}
