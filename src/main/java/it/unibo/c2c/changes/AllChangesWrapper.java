package it.unibo.c2c.changes;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

import static it.unibo.c2c.DoubleLists.doubleListOf;

record AllChangesWrapper(
        PostChanges changes,
        double previousValue,
        DoubleList nextYearsValues
) implements AllChanges {
    @Override
    public double postRate() {
        return changes.postRate();
    }

    @Override
    public double postDuration() {
        return changes.postDuration();
    }

    @Override
    public double postMagnitude() {
        return changes.postMagnitude();
    }

    @Override
    public double date() {
        return changes.date();
    }

    @Override
    public double value() {
        return changes.value();
    }

    @Override
    public double magnitude() {
        return changes.magnitude();
    }

    @Override
    public double duration() {
        return changes.duration();
    }

    @Override
    public double rate() {
        return changes.rate();
    }

    @Override
    public DoubleList toDoubleList(List<Double> prepend) {
        var result = changes.toDoubleList(prepend);
        result.add(indexRegrowth());
        result.add(recoveryIndicator());
        result.add(yearsToRegrowth(60));
        result.add(yearsToRegrowth(80));
        result.add(yearsToFullRegrowth());
        return result;
    }

    private double getValueAfterYears(int years) {
        return nextYearsValues.getDouble(years - 1);
    }

    private static final IntList YEARS_TO_SAMPLE = IntList.of(4, 5, 6);

    @Override
    public double indexRegrowth() {
        try {
            var average = YEARS_TO_SAMPLE.intStream()
                    .mapToDouble(this::getValueAfterYears)
                    .average()
                    .orElseGet(() -> Double.NaN);
            return average - value();
        } catch (IndexOutOfBoundsException e) {
            return Double.NaN;
        }
    }

    @Override
    public double yearsToRegrowth(int percent) {
        if (percent < 1 || percent > 100) {
            throw new IllegalArgumentException("Percent must be between 1 and 100");
        }
        double target = percent / 100.0;
        double threshold = previousValue * target;
        var values = new DoubleArrayList(nextYearsValues);
        values.addFirst(value());
        for (int i = 0; i < values.size(); i++) {
            var value = values.getDouble(i);
            if (value >= threshold) {
                return i;
            }
        }
        throw new IllegalStateException("This should never happen. " +
                "If this happens, there must be a bug in the computation of yearsToRegrowth, " +
                "or the assignment of previousValue, or nextYearsValues.");
    }

    @Override
    public AllChanges withRegrowth(double previousValue, List<Double> nextYearsValues) {
        return new AllChangesWrapper(
                changes,
                previousValue,
                doubleListOf(nextYearsValues)
        );
    }
}
