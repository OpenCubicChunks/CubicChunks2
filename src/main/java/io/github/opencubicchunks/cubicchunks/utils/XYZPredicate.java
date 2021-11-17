package io.github.opencubicchunks.cubicchunks.utils;

@FunctionalInterface
public interface XYZPredicate {
    boolean test(int x, int y, int z);
}
