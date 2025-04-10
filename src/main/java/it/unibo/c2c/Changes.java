package it.unibo.c2c;

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
    public double rate() {
        return magnitude / duration;
    }

    public double postRate() {
        return postMagnitude / postDuration;
    }
}
