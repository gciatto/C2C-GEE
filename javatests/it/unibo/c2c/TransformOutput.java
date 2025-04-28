package it.unibo.c2c;

import static it.unibo.c2c.DoubleLists.doubleListOf;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TransformOutput {
  private static final String DEFAULT_OUTPUT_FILE = "output-default.csv";
  private static final String TARGET_DIR = "src/test/resources/it/unibo/c2c";

  public static void main(String[] args) throws IOException {
    var input = Csv.vertical(TransformOutput.class.getResourceAsStream(DEFAULT_OUTPUT_FILE));
    var output = Csv.empty(input.headers());
    if (args.length == 0) {
      throw new IllegalArgumentException(
          "Please provide a task name as a command-line argument. "
              + "Available tasks: revert, filter");
    }
    var task = args[0].trim().toLowerCase();
    var newOutputFileName = "output-%sed.csv".formatted(task);
    switch (task) {
      case "revert" -> revertBand(input, output);
      case "filter" -> negativeMagnitudeOnly(input, output);
      case "default" -> doNothing(input, output);
      default -> throw new IllegalArgumentException("Unknown task: " + task);
    }
    var newOutputFile = new File(TARGET_DIR, newOutputFileName);
    try (var writer = new FileWriter(newOutputFile, /* append= */ false)) {
      output.writeTo(writer);
    } finally {
      System.out.println("Output written to " + newOutputFile.getPath());
    }
  }

  private static void doNothing(Csv input, Csv output) {
    // No transformation needed
    for (var i = 0; i < input.getRowsCount(); i++) {
      output.addRow(input.getRow(i));
    }
  }

  private static void revertBand(Csv input, Csv output) {
    // # +id, +year, -index, -magnitude, +duration, -rate, -postMagnitude, +postDuration, -postRate
    for (var i = 0; i < input.getRowsCount(); i++) {
      var row = doubleListOf(input.getRow(i));
      var fieldsToInvert =
          new int[] {
            input.headers().indexOf("index"),
            input.headers().indexOf("magnitude"),
            input.headers().indexOf("rate"),
            input.headers().indexOf("postMagnitude"),
            input.headers().indexOf("postRate"),
          };
      for (var field : fieldsToInvert) {
        row.set(field, -row.getDouble(field));
      }
      output.addRow(row);
    }
  }

  private static void negativeMagnitudeOnly(Csv input, Csv output) {
    for (var i = 0; i < input.getRowsCount(); i++) {
      var row = doubleListOf(input.getRow(i));
      var magnitudeIndex = input.headers().indexOf("magnitude");
      var magnitudeValue = row.getDouble(magnitudeIndex);
      if (!Double.isNaN(magnitudeValue) && magnitudeValue < 0) {
        output.addRow(row);
      }
    }
  }
}
