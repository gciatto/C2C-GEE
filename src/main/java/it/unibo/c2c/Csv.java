package it.unibo.c2c;

import com.google.common.base.Splitter;
import it.unimi.dsi.fastutil.doubles.DoubleList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static it.unibo.c2c.DoubleLists.doubleListOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;

/**
 * Read a resource file as a CSV into a {@link List<DoubleList>} Data is stored as a list of
 * columns with a list of string headers.
 *
 * <p>NOTE: This class does not handle quoted strings and always assumes the separator is a comma.
 */
public record Csv(List<String> headers, List<DoubleList> values) {

    public Csv(List<String> headers, List<DoubleList> values) {
        this.headers = Objects.requireNonNull(headers);
        this.values = Objects.requireNonNull(values);
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("headers cannot be empty");
        }
        if (values.size() != headers().size()) {
            throw new IllegalArgumentException("The number of headers must match the number of values");
        }
    }

    /**
     * A transposed csv. Each "column" is actually a row, with the column header as the first item of
     * the row.
     */
    public static Csv horizontal(InputStream stream) {
        try (var reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            List<String> headers = new ArrayList<>();
            List<DoubleList> values = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                headers.add(parts[0]);
                double[] doubles = stream(parts).skip(1).mapToDouble(Double::parseDouble).toArray();
                values.add(doubleListOf(doubles));
            }
            return new Csv(headers, values);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    /**
     * Regular column-oriented file with a header line on top.
     */
    public static Csv vertical(InputStream stream) {
        try (var reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            List<String> headers = Arrays.asList(reader.readLine().split(","));
            List<DoubleList> values = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                values.add(doubleListOf());
            }
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> parts = Splitter.on(',').splitToList(line);
                for (int i = 0; i < parts.size(); i++) {
                    values.get(i).add(Double.parseDouble(parts.get(i)));
                }
            }
            return new Csv(headers, values);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Get a column by name.
     */
    public DoubleList getColumn(String name) {
        return values.get(headers.indexOf(name));
    }

    /**
     * Get one row of the CSV as a DoubleList, skipping the first `skip` elements.
     */
    public DoubleList getRow(int row, int skip) {
        DoubleList result = doubleListOf();
        for (int col = skip; col < values.size(); col++) {
            result.add(values.get(col).getDouble(row));
        }
        return result;
    }

    /**
     * Get one row of the CSV as a DoubleList
     */
    public DoubleList getRow(int row) {
        return getRow(row, 0);
    }

    /**
     * Split the Csv based on the value of the 'id' column. Assumes the rows are sorted and grouped
     * together.
     */
    public List<Csv> groupByColumn(String id) {
        DoubleList groupColumn = getColumn(id);
        List<Csv> result = new ArrayList<>();
        int startRow = 0;
        double lastGroup = groupColumn.getDouble(0);
        for (int i = 0; i < groupColumn.size(); i++) {
            if (groupColumn.getDouble(i) != lastGroup) {
                result.add(subset(startRow, i - 1));
                startRow = i;
            }
            lastGroup = groupColumn.getDouble(i);
        }
        result.add(subset(startRow, groupColumn.size() - 1));
        return result;
    }

    /**
     * Extract a subset of rows as if it were another Csv
     */
    public Csv subset(int start, int end) {
        List<DoubleList> copies = new ArrayList<>();
        int len = end - start + 1;
        for (DoubleList d : values) {
            double[] copy = new double[len];
            d.getElements(start, copy, 0, len);
            copies.add(doubleListOf(copy));
        }
        return new Csv(headers, copies);
    }
}
