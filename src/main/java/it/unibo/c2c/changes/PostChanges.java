package it.unibo.c2c.changes;

import java.util.ArrayList;
import java.util.List;

public interface PostChanges extends Changes {

    /**
     * Difference between the value of the next vertex and the value of the current vertex.
     */
    double postMagnitude();

    /**
     * Difference between the date of the next vertex and the date of the current vertex.
     */
    double postDuration();

    default double postRate() {
        return postMagnitude() / postDuration();
    }

    static List<String> headers(String... prepend) {
        return headers(List.of(prepend));
    }

    static List<String> headers(List<String> prepend) {
        var result = new ArrayList<>(prepend);
        result.addAll(List.of("postMagnitude", "postDuration", "postRate"));
        return result;
    }

    static PostChanges of(
            double date,
            double value,
            double magnitude,
            double duration,
            double postMagnitude,
            double postDuration
    ) {
        return new PostChangesImpl(date, value, magnitude, duration, postMagnitude, postDuration);
    }

    static PostChanges postOnly(
            double date,
            double value,
            double postMagnitude,
            double postDuration
    ) {
        return of(date, value, Double.NaN, Double.NaN, postMagnitude, postDuration);
    }
}
