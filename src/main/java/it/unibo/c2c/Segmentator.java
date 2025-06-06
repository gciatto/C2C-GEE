package it.unibo.c2c;

import static it.unibo.c2c.DoubleLists.argMin;

import it.unibo.c2c.changes.Changes;
import it.unibo.c2c.changes.RegrowthChanges;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.ArrayList;
import java.util.List;

/**
 * This segmentation algorithm consist in a modification of the bottom up algorithm
 * (https://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.23.6570&rep=rep1&type=pdf) proposed by
 * Hermosilla et al. (2015)
 */
public class Segmentator {

  private Segmentator() {}

  private record Segment(int start, int finish) {
    public Segment changeFinish(int newFinish) {
      return new Segment(start, newFinish);
    }

    public Segment copyFinishFrom(Segment other) {
      return changeFinish(other.finish);
    }
  }

  public static List<Changes> segment(DoubleList dates, DoubleList values, C2cSolver.Args args) {
    List<Segment> segments = new ArrayList<>();
    List<Double> mergeCost = new ArrayList<>();
    // initial segments
    for (var i = 0; i < values.size() - 1; i++) {
      segments.add(new Segment(i, i + 1));
    }
    // merging cost of initial segments
    for (var i = 0; i < segments.size() - 1; i++) {
      var left = segments.get(i);
      var right = segments.get(i + 1);
      mergeCost.add(calculateError(dates, values, left.start, right.finish));
    }
    // minimum merging cost
    var index = argMin(mergeCost);
    double min = mergeCost.get(index);
    while (min < args.maxError || segments.size() > args.maxSegments) {
      // merge the adjacent segments with the smaller cost
      var segment = segments.get(index);
      var segment2 = segments.get(index + 1);
      segments.set(index, segment.copyFinishFrom(segment2));
      // update segments
      segments.remove(index + 1);
      mergeCost.remove(index);
      if (mergeCost.isEmpty()) {
        break;
      }
      if (index + 1 < segments.size()) {
        var left = segments.get(index);
        var right = segments.get(index + 1);
        mergeCost.set(index, calculateError(dates, values, left.start, right.finish));
      }
      if (index - 1 >= 0) {
        var left = segments.get(index - 1);
        var right = segments.get(index);
        mergeCost.set(index - 1, calculateError(dates, values, left.start, right.finish));
      }
      index = argMin(mergeCost);
      min = mergeCost.get(index);
    }
    List<Changes> segmented = new ArrayList<>();
    var leftIndex = 999;
    var rightIndex = 999;
    int centralIndex;
    for (var i = 0; i < segments.size(); i++) {
      centralIndex = segments.get(i).start;
      if (i == 0) {
        rightIndex = segments.get(i).finish;
      } else {
        leftIndex = segments.get(i - 1).start;
        rightIndex = segments.get(i).finish;
      }
      var c = changeMetricsCalculator(dates, values, leftIndex, centralIndex, rightIndex, args);
      segmented.add(c);
    }
    // add last change
    centralIndex = segments.getLast().finish;
    leftIndex = segments.getLast().start;
    var c = changeMetricsCalculator(dates, values, leftIndex, centralIndex, rightIndex, args);
    segmented.add(c);
    return segmented;
  }

  private static double calculateError(DoubleList dates, DoubleList values, int start, int finish) {
    // linearInterpolation
    var y1 = values.getDouble(start);
    var y2 = values.getDouble(finish);
    var x1 = dates.getDouble(start);
    var x2 = dates.getDouble(finish);
    var timeWindow = x2 - x1;
    double error = 0;
    for (var i = start; i <= finish; i++) {
      var xFraction = (dates.getDouble(i) - x1) / timeWindow;
      var interpolated = lerp(y1, y2, xFraction);
      var diff = values.getDouble(i) - interpolated;
      error += diff * diff;
    }
    return Math.sqrt(error / (finish - start));
  }

  private static Changes changeMetricsCalculator(
      DoubleList dates,
      DoubleList values,
      int preIndex,
      int currIndex,
      int postIndex,
      C2cSolver.Args args) {
    var currDate = dates.getDouble(currIndex);
    var currValue = values.getDouble(currIndex);
    var isLast = currIndex == values.size() - 1;
    var isFirst = currIndex == 0;
    var magnitude = isFirst ? Double.NaN : currValue - values.getDouble(preIndex);
    var duration = isFirst ? Double.NaN : currDate - dates.getDouble(preIndex);
    var change = Changes.of(currDate, currValue, magnitude, duration);
    if (args.postMetrics) {
      var postMagnitude = isLast ? Double.NaN : values.getDouble(postIndex) - currValue;
      var postDuration = isLast ? Double.NaN : dates.getDouble(postIndex) - currDate;
      change = change.withPost(postMagnitude, postDuration);
    }
    if (args.regrowthMetrics) {
      if (change.hasNegativeMagnitude()) {
        change = extendWithRegrowthMetrics(dates, values, change, currIndex);
      } else {
        change = change.withoutRegrowth();
      }
    }
    return change;
  }

  static void extendAllWithRegrowthMetrics(
      List<Changes> changes, DoubleList dates, DoubleList values) {
    for (int i = 0, j = 0; i < dates.size() && j < changes.size(); i++) {
      var currentDate = dates.getDouble(i);
      var currentChange = changes.get(j);
      if (currentDate == currentChange.date()) {
        currentChange = extendWithRegrowthMetrics(dates, values, currentChange, i);
        changes.set(j++, currentChange);
      }
    }
  }

  private static final int MIN_SAMPLES =
      RegrowthChanges.DATES_TO_SAMPLE.intStream().max().getAsInt();

  private static Changes extendWithRegrowthMetrics(
      DoubleList dates, DoubleList values, Changes changes, int currentIndex) {
    try {
      int nextIndex;
      var hasRegrown = false;
      for (var i = 1; (nextIndex = currentIndex + i) < values.size(); i++) {
        var nextValue = values.getDouble(nextIndex);
        if (!hasRegrown && percent(changes, nextValue) >= 1.0) {
          hasRegrown = true;
        }
        if (i >= MIN_SAMPLES && hasRegrown) {
          nextIndex++;
          break;
        }
      }
      return changes.withRegrowth(
          dates.subList(currentIndex + 1, nextIndex), values.subList(currentIndex + 1, nextIndex));
    } catch (ArrayIndexOutOfBoundsException e) {
      return changes.withoutRegrowth();
    }
  }

  private static double percent(Changes changes, double value) {
    var target = Math.abs(changes.magnitude());
    var current = Math.abs(value - changes.value());
    return current / target;
  }

  private static double lerp(double y1, double y2, double x) {
    return y1 * (1 - x) + y2 * x;
  }
}
