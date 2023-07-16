package io.github.opencubicchunks.cubicchunks.utils;

public interface Int3Iterator {
    boolean hasNext();

    int getNextX();
    int getNextY();
    int getNextZ();
}
