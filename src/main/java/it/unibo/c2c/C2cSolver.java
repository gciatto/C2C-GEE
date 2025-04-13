package it.unibo.c2c;

import com.google.earthengine.api.base.ArgsBase;
import it.unibo.c2c.changes.Changes;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Solver for the BottomUp Segmentation algorithm.
 */
public class C2cSolver {

    public static class Args extends ArgsBase {
        @Doc(help = "Maximum error (RMSE) allowed to remove points and construct segments.")
        @Optional
        public double maxError = 75;

        @Doc(help = "Maximum number of segments to be fitted on the time series.")
        @Optional
        public int maxSegments = 6;

        @Doc(help = "Year of the first image in the output image collection.")
        @Optional
        public int startYear = 1984;

        @Doc(help = "Year of the last image in the output image collection.")
        @Optional
        public int endYear = 2019;

        @Doc(help = "Whether to apply the pre-infill process.")
        @Optional
        public boolean infill = true;

        @Doc(help = "Tolerance of spikes in the time series. A value of 1 indicates no spike removal.")
        @Optional
        public double spikesTolerance = 0.85;

        @Doc(help = "Whether to invert the sign of the band.")
        @Optional
        public boolean revertBand = false;

        @Doc(help = "Whether filter out changes having a non-negative magnitude.")
        @Optional
        public boolean negativeMagnitudeOnly = false;

        @Override
        public String toString() {
            return "maxError=" + maxError +
                    ", maxSegments=" + maxSegments +
                    ", startYear=" + startYear +
                    ", endYear=" + endYear +
                    ", infill=" + infill +
                    ", spikesTolerance=" + spikesTolerance +
                    ", revertBand=" + revertBand +
                    ", negativeMagnitudeOnly=" + negativeMagnitudeOnly;
        }
    }

    public final Args args;

    public C2cSolver(Args args) {
        this.args = Objects.requireNonNull(args);
    }

    public C2cSolver() {
        this(new Args());
    }

    public @Nullable List<Changes> c2cBottomUp(DoubleList dates, DoubleList values) {
        return c2cBottomUp(dates, values, args);
    }

    public @Nullable List<Changes> c2cBottomUp(DoubleList dates, DoubleList values, Args args) {
        if (values.doubleStream().filter(v -> v != 0).count() < 3) {
            return null;
        }
        // Revert band if requested.
        if (args.revertBand) {
            revert(values);
        }
        // Preprocess as requested.
        if (args.infill) {
            fillValues(values);
        }
        if (args.spikesTolerance < 1) {
            despikeTimeLine(values, args.spikesTolerance);
        }
        // Start segmentation.
        var result = Segmentator.segment(dates, values, args.maxError, args.maxSegments);
        if (args.negativeMagnitudeOnly) {
            filterOutNonNegativeChanges(result);
        }
        return result;
    }

    public Csv c2cBottomUp(Csv inputs) {
        return c2cBottomUp(inputs, args);
    }

    public Csv c2cBottomUp(Csv inputs, C2cSolver.Args args) {
        Csv result = Csv.empty(Changes.headers("id"));
        var years = inputs.getHeadersAsDoubles();
        for (int i = 0; i < inputs.getRowsCount(); i++) {
            DoubleList timeline = inputs.getRow(i, /* skip= */ 1);
            List<Changes> changes = c2cBottomUp(years, timeline, args);
            if (changes != null) {
                result.addRows(changesToCsv(i, changes));
            }
        }
        return result;
    }

    public Csv changesToCsv(double id, List<Changes> changes) {
        Csv result = Csv.empty(Changes.headers("id"));
        for (Changes change : changes) {
            DoubleList row = change.toDoubleList(id);
            result.addRow(row);
        }
        return result;
    }

    private static void revert(DoubleList values) {
        for (int i = 0; i < values.size(); i++) {
            values.set(i, -values.getDouble(i));
        }
    }

    private static boolean filterOutNonNegativeChanges(List<Changes> changes) {
        return changes.removeIf(c -> Double.isNaN(c.magnitude()) || c.magnitude() >= 0);
    }

    private static void fillValues(DoubleList values) {
        // Infill missing data
        for (int i = 0; i < values.size(); i++) {
            if (values.getDouble(i) != 0) {
                continue;
            }
            // Find the first two valid observations in timeLine before and after i
            int left1 = findValid(values, i, -1);
            int left2 = findValid(values, left1, -1);
            int right1 = findValid(values, i, 1);
            int right2 = findValid(values, right1, 1);
            if (left2 == -1) {
                values.set(i, values.getDouble(right1));
            } else if (right2 == -1) {
                if (right1 != -1) {
                    values.set(i, values.getDouble(right1));
                } else {
                    values.set(i, values.getDouble(left1));
                }
            } else {
                double leftDif = Math.abs(values.getDouble(left1) - values.getDouble(left2));
                double rightDif = Math.abs(values.getDouble(right1) - values.getDouble(right2));
                // Fill using value with smaller difference
                if (leftDif < rightDif) {
                    values.set(i, values.getDouble(left1));
                } else {
                    values.set(i, values.getDouble(right1));
                }
            }
        }
        int size = values.size();
        double lastValue = values.getDouble(size - 1);
        double lastValueL = values.getDouble(size - 2);
        double lastValueLL = values.getDouble(size - 3);
        double lastDif = Math.abs(lastValue - lastValueL);
        double secondLastDif = Math.abs(lastValueL - lastValueLL);
        if (lastDif >= secondLastDif) {
            values.set(size - 1, lastValueL);
        }
    }

    /**
     * Find the first non-zero from `start` in the direction of `dir`. Returns -1 if none found.
     */
    private static int findValid(DoubleList list, int start, int dir) {
        if (start == -1) {
            return -1;
        }
        int limit = dir == 1 ? list.size() : -1;
        for (int i = start + dir; i != limit; i += dir) {
            if (list.getDouble(i) != 0) {
                return i;
            }
        }
        return -1;
    }

    private static void despikeTimeLine(DoubleList values, double spikesTolerance) {
        for (int i = 1; i < values.size() - 1; i++) {
            double left = values.getDouble(i - 1);
            double center = values.getDouble(i);
            double right = values.getDouble(i + 1);
            double fitted = (left + right) / 2;
            double delta = Math.abs(left - right);
            double spikeValue = Math.abs(fitted - center);
            double despikeProportion = delta / spikeValue;
            //      despike conditions
            //      #1# The value of the spike is greater than 100
            //      #2# The difference between spectral values on either side of the spike
            //      is less than 1-despike desawtooth proportion of the spike itself" (Landtrendr)
            if (spikeValue > 100 && despikeProportion < (1 - spikesTolerance)) {
                // double leftValueOfT = values.getDouble(i - 1);
                // double rightValueOfT = values.getDouble(i + 1);
                // double centerValueFittedOfT = (leftValueOfT + rightValueOfT) / 2;
                values.set(i, fitted);
            }
        }
    }
}
