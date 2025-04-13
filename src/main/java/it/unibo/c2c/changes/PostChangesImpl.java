package it.unibo.c2c.changes;

import it.unimi.dsi.fastutil.doubles.DoubleList;

import java.util.List;

import static it.unibo.c2c.DoubleLists.doubleListOf;

/**
 * Just a POD to record a change segment.
 */
record PostChangesImpl(
        double date,
        double value,
        double magnitude,
        double duration,
        double postMagnitude,
        double postDuration
) implements PostChanges {

    public double rate() {
        return magnitude / duration;
    }

    public double postRate() {
        return postMagnitude / postDuration;
    }

    @Override
    public DoubleList toDoubleList(List<Double> prepend) {
        var result = doubleListOf(prepend);
        var list = doubleListOf(date, value, magnitude, duration, rate(), postMagnitude, postDuration(), postRate());
        result.addAll(list);
        return result;
    }

    @Override
    public AllChanges withRegrowth(double previousValue, List<Double> nextYearsValues) {
        return new AllChangesWrapper(
                this,
                previousValue,
                doubleListOf(nextYearsValues)
        );
    }
}
