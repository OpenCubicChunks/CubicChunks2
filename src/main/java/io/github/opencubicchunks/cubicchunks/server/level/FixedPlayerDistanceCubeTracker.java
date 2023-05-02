package io.github.opencubicchunks.cubicchunks.server.level;

import net.minecraft.server.level.DistanceManager;

public class FixedPlayerDistanceCubeTracker extends CubeTracker {

    public FixedPlayerDistanceCubeTracker(DistanceManager cubicDistanceManager, int i) {
        super(0, 0, 0);
        throw new Error("ASM didn't apply");
    }

    @Override protected int getLevelFromSource(long pos) {
        throw new Error("ASM didn't apply");
    }

    @Override protected int getLevel(long sectionPos) {
        throw new Error("ASM didn't apply");
    }

    @Override protected void setLevel(long sectionPos, int level) {
        throw new Error("ASM didn't apply");
    }
}