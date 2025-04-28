package it.unibo.c2c.changes;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;

public interface RegrowthChanges extends Changes {
  IntList DATES_TO_SAMPLE = IntList.of(5);

  double indexRegrowth();

  default double recoveryIndicator() {
    return indexRegrowth() / magnitude();
  }

  double yearsToRegrowth(int percent);

  default double yearsToFullRegrowth() {
    return yearsToRegrowth(100);
  }

  @Override
  AllChanges withPost(double postMagnitude, double postDuration);

  static List<String> headers(String... prepend) {
    return headers(List.of(prepend));
  }

  static List<String> headers(List<String> prepend) {
    var result = new ArrayList<>(prepend);
    result.addAll(List.of("indexRegrowth", "recoveryIndicator", "y2r60", "y2r80", "y2r100"));
    return result;
  }
}
