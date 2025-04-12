package it.unibo.c2c;

import it.unimi.dsi.fastutil.doubles.DoubleList;

import java.util.ArrayList;
import java.util.List;

import static it.unibo.c2c.DoubleLists.argMin;

/**
 * This segmentation algorithm consist in a modification of the bottom up algorithm
 * (https://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.23.6570&rep=rep1&type=pdf) proposed by
 * Hermosilla et al. (2015)
 */
public class Segmentator {

    private static final double NODATA = Double.NaN;

    private Segmentator() {
    }

    private record Segment(int start, int finish) {
        public Segment changeFinish(int newFinish) {
            return new Segment(start, newFinish);
        }

        public Segment copyFinishFrom(Segment other) {
            return changeFinish(other.finish);
        }
    }

    public static List<Changes> segment(DoubleList dates, DoubleList values, double maxError, int maxSegm) {
        List<Segment> segments = new ArrayList<>();
        List<Double> mergeCost = new ArrayList<>();
        // initial segments
        for (int i = 0; i < values.size() - 1; i++) {
            segments.add(new Segment(i, i + 1));
        }
        // merging cost of initial segments
        for (int i = 0; i < segments.size() - 1; i++) {
            Segment left = segments.get(i);
            Segment right = segments.get(i + 1);
            mergeCost.add(calculateError(dates, values, left.start, right.finish));
        }
        // minimum merging cost
        int index = argMin(mergeCost);
        double min = mergeCost.get(index);
        while (min < maxError || segments.size() > maxSegm) {
            // merge the adjacent segments with the smaller cost
            Segment segment = segments.get(index);
            Segment segment2 = segments.get(index + 1);
            segments.set(index, segment.copyFinishFrom(segment2));
            // update segments
            segments.remove(index + 1);
            mergeCost.remove(index);
            if (mergeCost.isEmpty()) {
                break;
            }
            if (index + 1 < segments.size()) {
                Segment left = segments.get(index);
                Segment right = segments.get(index + 1);
                mergeCost.set(index, calculateError(dates, values, left.start, right.finish));
            }
            if (index - 1 >= 0) {
                Segment left = segments.get(index - 1);
                Segment right = segments.get(index);
                mergeCost.set(index - 1, calculateError(dates, values, left.start, right.finish));
            }
            index = argMin(mergeCost);
            min = mergeCost.get(index);
        }
        List<Changes> segmented = new ArrayList<>();
        int leftIndex = 999;
        int rightIndex = 999;
        int centralIndex;
        for (int i = 0; i < segments.size(); i++) {
            centralIndex = segments.get(i).start;
            if (i == 0) {
                rightIndex = segments.get(i).finish;
            } else {
                leftIndex = segments.get(i - 1).start;
                rightIndex = segments.get(i).finish;
            }
            Changes c = changeMetricsCalculator(dates, values, leftIndex, centralIndex, rightIndex);
            segmented.add(c);
        }
        // add last change
        centralIndex = segments.getLast().finish;
        leftIndex = segments.getLast().start;
        Changes c = changeMetricsCalculator(dates, values, leftIndex, centralIndex, rightIndex);
        segmented.add(c);
        return segmented;
    }

    private static double calculateError(DoubleList dates, DoubleList values, int start, int finish) {
        // linearInterpolation
        double y1 = values.getDouble(start);
        double y2 = values.getDouble(finish);
        double x1 = dates.getDouble(start);
        double x2 = dates.getDouble(finish);
        double timeWindow = x2 - x1;
        double error = 0;
        for (int i = start; i <= finish; i++) {
            double xFraction = (dates.getDouble(i) - x1) / timeWindow;
            double interpolated = lerp(y1, y2, xFraction);
            double diff = values.getDouble(i) - interpolated;
            error += diff * diff;
        }
        return Math.sqrt(error / (finish - start));
    }

    private static Changes changeMetricsCalculator(DoubleList dates, DoubleList values, int preIndex, int currIndex, int postIndex) {
        double currDate = dates.getDouble(currIndex);
        double currValue = values.getDouble(currIndex);
        Changes change;
        if (currIndex == 0) {
            double postMagnitude = values.getDouble(postIndex) - currValue;
            double postDuration = dates.getDouble(postIndex) - currDate;
            change = new Changes(currDate, currValue, NODATA, NODATA, postMagnitude, postDuration);
        } else if (currIndex == values.size() - 1) {
            double magnitude = currValue - values.getDouble(preIndex);
            double duration = currDate - dates.getDouble(preIndex);
            change = new Changes(currDate, currValue, magnitude, duration, NODATA, NODATA);
        } else {
            double magnitude = currValue - values.getDouble(preIndex);
            double duration = currDate - dates.getDouble(preIndex);
            double postMagnitude = values.getDouble(postIndex) - currValue;
            double postDuration = dates.getDouble(postIndex) - currDate;
            change = new Changes(currDate, currValue, magnitude, duration, postMagnitude, postDuration);
        }
        return change;
    }

    private static double lerp(double y1, double y2, double x) {
        return y1 * (1 - x) + y2 * x;
    }
}
