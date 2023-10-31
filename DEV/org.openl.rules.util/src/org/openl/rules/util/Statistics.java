package org.openl.rules.util;

/**
 * A set of function for statistical analyze.
 */
public final class Statistics {

    private Statistics() {
        // Utility class
    }

    /**
     * Returns the greatest of values. If values are equal, the first instance will return.
     */
    public static <T extends Comparable<T>> T max(T... values) {
        return process(values, new Result<T, T>() {
            @Override
            public void processNonNull(T value) {
                if (result == null || result.compareTo(value) < 0) {
                    result = value;
                }
            }
        });
    }

    /**
     * Returns the smallest of values. If values are equal, the first instance will return.
     */
    public static <T extends Comparable<T>> T min(T... values) {
        return process(values, new Result<T, T>() {
            @Override
            public void processNonNull(T value) {
                if (result == null || result.compareTo(value) > 0) {
                    result = value;
                }
            }
        });
    }

    public static <T extends Number> Double standardPopulationDeviation(T... values) {
        Double avg = Avg.avg(values);
        return process(values, new Result<T, Double>() {
            @Override
            public void processNonNull(T value) {
                double doubleValue = value.doubleValue();
                result = result == null ? Math.pow((doubleValue-avg), 2) : (result + Math.pow((doubleValue-avg), 2));
            }

            @Override
            public Double result() {
                return result == null ? null : Math.sqrt(result / counter);
            }
        });
    }

    static <V, R> R process(V[] values, Processor<V, R> processor) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (V value : values) {
            processor.process(value);
        }
        return processor.result();
    }

    interface Processor<V, R> {
        void process(V value);

        R result();
    }

    abstract static class Result<V, R> implements Processor<V, R> {
        R result;
        int counter;

        void processNonNull(V value) {
        }

        @Override
        public void process(V value) {
            if (value != null) {
                processNonNull(value);
                counter++;
            }
        }

        @Override
        public R result() {
            return result;
        }
    }

}
