package io.github.opencubicchunks.cubicchunks.world.level;

public interface CubicLevelReader {
    default boolean isCubic() {
        return false;
    }
}
