package it.unibo.c2c.changes;

import it.unimi.dsi.fastutil.doubles.DoubleList;

import java.util.ArrayList;
import java.util.List;

import static it.unibo.c2c.DoubleLists.doubleListOf;

/**
 * Just a POD to record a change segment.
 */
public record Changes(
        double date,
        double value,
        double magnitude,
        double duration,
        double postMagnitude,
        double postDuration
) {
    public static Changes post(double date, double value, double postMagnitude, double postDuration) {
        return new Changes(date, value, Double.NaN, Double.NaN, postMagnitude, postDuration);
    }

    public static Changes pre(double date, double value, double magnitude, double duration) {
        return new Changes(date, value, magnitude, duration, Double.NaN, Double.NaN);
    }

    public static List<String> headers(String... prepend) {
        var result = new ArrayList<>(List.of(prepend));
        var list = List.of("year", "index", "magnitude", "duration", "rate", "postMagnitude", "postDuration", "postRate");
        result.addAll(list);
        return result;
    }

    public double rate() {
        return magnitude / duration;
    }

    public double postRate() {
        return postMagnitude / postDuration;
    }

    public DoubleList toDoubleList(double... prepend) {
        var result = doubleListOf(prepend);
        var list = doubleListOf(date, value, magnitude, duration, rate(), postMagnitude, postDuration(), postRate());
        result.addAll(list);
        return result;
    }
}
