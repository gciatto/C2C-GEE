package it.unibo.c2c;

import com.google.earthengine.api.base.ArgsBase;
import it.unibo.c2c.changes.Changes;
import it.unibo.c2c.changes.PostChanges;
import it.unibo.c2c.changes.RegrowthChanges;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Solver for the BottomUp Segmentation algorithm. */
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

    @Doc(
        help =
            "Whether include post metrics in the output (postMagnitude, postDuration, postRate).")
    @Optional
    public boolean postMetrics = true;

    @Doc(
        help =
            "Whether include regrowth metrics in the output (indexRegrowth, recoveryIndicator, y2r60, y2r80, y2r100).")
    @Optional
    public boolean regrowthMetrics = false;

    @Doc(help = "Whether to interpolate the time series before computing metrics.")
    @Optional
    public boolean interpolate = false;

    @Doc(help = "Whether to log additional information while processing.")
    @Optional
    public boolean logs = false;

    public void log(String format, Object... args) {
      if (logs) {
        System.out.print("# ");
        System.out.printf(format, args);
        System.out.println();
        System.out.flush();
      }
    }

    public Args copy() {
      var args = new Args();
      args.maxError = maxError;
      args.maxSegments = maxSegments;
      args.startYear = startYear;
      args.endYear = endYear;
      args.infill = infill;
      args.spikesTolerance = spikesTolerance;
      args.revertBand = revertBand;
      args.negativeMagnitudeOnly = negativeMagnitudeOnly;
      args.postMetrics = postMetrics;
      args.regrowthMetrics = regrowthMetrics;
      args.interpolate = interpolate;
      return args;
    }

    @Override
    public String toString() {
      return "maxError="
          + maxError
          + ", maxSegments="
          + maxSegments
          + ", startYear="
          + startYear
          + ", endYear="
          + endYear
          + ", infill="
          + infill
          + ", spikesTolerance="
          + spikesTolerance
          + ", revertBand="
          + revertBand
          + ", negativeMagnitudeOnly="
          + negativeMagnitudeOnly
          + ", postMetrics="
          + postMetrics
          + ", regrowthMetrics="
          + regrowthMetrics
          + ", interpolate="
          + interpolate
          + ", logs="
          + logs;
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
    List<Changes> vertices;
    if (args.interpolate) {
      var simpleArgs = args.copy();
      simpleArgs.regrowthMetrics = false;
      // Compute segmentation with simple args (not wasting time to compute additional metrics).
      vertices = Segmentator.segment(dates, values, simpleArgs);
      // Interpolate the time series w.r.t. to the vertices.
      interpolate(dates, values, vertices);
      // Re-compute segmentation with the original args.
      Segmentator.extendAllWithRegrowthMetrics(vertices, dates, values);
    } else {
      // Start segmentation.
      vertices = Segmentator.segment(dates, values, args);
    }
    if (args.negativeMagnitudeOnly) {
      filterOutNonNegativeChanges(vertices);
    }
    return vertices;
  }

  private void interpolate(DoubleList dates, DoubleList values, List<Changes> vertices) {
    for (var vertexIndex = 0; vertexIndex < vertices.size() - 1; vertexIndex++) {
      var vertex = vertices.get(vertexIndex);
      var nextVertex = vertices.get(vertexIndex + 1);
      var x1 = vertex.date();
      var x2 = nextVertex.date();
      var y1 = vertex.value();
      var y2 = nextVertex.value();
      for (var i = 0; i < dates.size(); i++) {
        var x = dates.getDouble(i);
        if (x <= x1) {
          continue;
        } else if (x >= x2) {
          break;
        } else {
          var y = y1 + (y2 - y1) * (x - x1) / (x2 - x1);
          values.set(i, y);
        }
      }
    }
  }

  public Csv c2cBottomUp(Csv inputs) {
    return c2cBottomUp(inputs, args);
  }

  private static List<String> headers(C2cSolver.Args args) {
    var result = Changes.headers("id", "index");
    if (args.postMetrics) {
      result = PostChanges.headers(result);
    }
    if (args.regrowthMetrics) {
      result = RegrowthChanges.headers(result);
    }
    return result;
  }

  public Csv c2cBottomUp(Csv inputs, C2cSolver.Args args) {
    var headers = headers(args);
    var result = Csv.empty(headers);
    var years = inputs.getHeadersAsDoubles();
    for (var i = 0; i < inputs.getRowsCount(); i++) {
      var timeline = inputs.getRow(i);
      double id = timeline.removeFirst();
      var changes = c2cBottomUp(years, timeline, args);
      if (args.interpolate) {
        args.log(
            "Interpolate line %d, %s",
            (int) id,
            timeline.doubleStream().mapToObj(Double::toString).collect(Collectors.joining(", ")));
      }
      if (changes != null) {
        result.addRows(changesToCsv(id, i, changes, headers));
      }
    }
    return result;
  }

  private Csv changesToCsv(double id, double index, List<Changes> changes, List<String> headers) {
    var result = Csv.empty(headers);
    for (var change : changes) {
      var row = change.toDoubleList(id, index);
      result.addRow(row);
    }
    return result;
  }

  private static void revert(DoubleList values) {
    for (var i = 0; i < values.size(); i++) {
      values.set(i, -values.getDouble(i));
    }
  }

  private static boolean filterOutNonNegativeChanges(List<Changes> changes) {
    return changes.removeIf(c -> !c.hasNegativeMagnitude());
  }

  private static void fillValues(DoubleList values) {
    // Infill missing data
    for (var i = 0; i < values.size(); i++) {
      if (values.getDouble(i) != 0) {
        continue;
      }
      // Find the first two valid observations in timeLine before and after i
      var left1 = findValid(values, i, -1);
      var left2 = findValid(values, left1, -1);
      var right1 = findValid(values, i, 1);
      var right2 = findValid(values, right1, 1);
      if (left2 == -1) {
        values.set(i, values.getDouble(right1));
      } else if (right2 == -1) {
        if (right1 != -1) {
          values.set(i, values.getDouble(right1));
        } else {
          values.set(i, values.getDouble(left1));
        }
      } else {
        var leftDif = Math.abs(values.getDouble(left1) - values.getDouble(left2));
        var rightDif = Math.abs(values.getDouble(right1) - values.getDouble(right2));
        // Fill using value with smaller difference
        if (leftDif < rightDif) {
          values.set(i, values.getDouble(left1));
        } else {
          values.set(i, values.getDouble(right1));
        }
      }
    }
    var size = values.size();
    var lastValue = values.getDouble(size - 1);
    var lastValueL = values.getDouble(size - 2);
    var lastValueLL = values.getDouble(size - 3);
    var lastDif = Math.abs(lastValue - lastValueL);
    var secondLastDif = Math.abs(lastValueL - lastValueLL);
    if (lastDif >= secondLastDif) {
      values.set(size - 1, lastValueL);
    }
  }

  /** Find the first non-zero from `start` in the direction of `dir`. Returns -1 if none found. */
  private static int findValid(DoubleList list, int start, int dir) {
    if (start == -1) {
      return -1;
    }
    var limit = dir == 1 ? list.size() : -1;
    for (var i = start + dir; i != limit; i += dir) {
      if (list.getDouble(i) != 0) {
        return i;
      }
    }
    return -1;
  }

  private static void despikeTimeLine(DoubleList values, double spikesTolerance) {
    for (var i = 1; i < values.size() - 1; i++) {
      var left = values.getDouble(i - 1);
      var center = values.getDouble(i);
      var right = values.getDouble(i + 1);
      var fitted = (left + right) / 2;
      var delta = Math.abs(left - right);
      var spikeValue = Math.abs(fitted - center);
      var despikeProportion = delta / spikeValue;
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
