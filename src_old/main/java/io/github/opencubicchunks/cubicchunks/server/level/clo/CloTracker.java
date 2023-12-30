package io.github.opencubicchunks.cubicchunks.server.level;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloPos;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;

public abstract class CloTracker extends DynamicGraphMinFixedPoint {

    private final Long2ObjectMap<IntSet> existingCubesForCubeColumns = new Long2ObjectLinkedOpenHashMap<>();
    private final int noChunkLevel;

    protected CloTracker(int levelCount, int expectedUpdatesByLevel, int expectedPropagationLevels, int noChunkLevel) {
        super(levelCount, expectedUpdatesByLevel, expectedPropagationLevels);
        this.noChunkLevel = noChunkLevel;
    }

    protected CloTracker(int levelCount, int expectedUpdatesByLevel, int expectedPropagationLevels) {
        this(levelCount, expectedUpdatesByLevel, expectedPropagationLevels, levelCount - 1);
    }

    @Override protected boolean isSource(long pos) {
        return pos == Long.MAX_VALUE;
    }

    @Override protected void checkNeighborsAfterUpdate(long pos, int level, boolean isDecreasing) {
        CloPos.forEachNeighbor(pos, n -> this.checkNeighbor(pos, n, level, isDecreasing));
    }

    /**
     * Computes level propagated from neighbors of specified position with given existing level, excluding the given source position.
     */
    @Override protected int getComputedLevel(long pos, long excludedSourcePos, int level) {
        if (CloPos.isColumn(pos)) {
            int out = level;

            int x = CloPos.extractX(pos);
            int z = CloPos.extractZ(pos);
            for (int x2 = -1; x2 <= 1; ++x2) {
                for (int z2 = -1; z2 <= 1; ++z2) {
                    long neighbor = CloPos.asLong(x + x2, z + z2);
                    if (neighbor == pos) {
                        neighbor = Long.MAX_VALUE;
                    }

                    if (neighbor != excludedSourcePos) {
                        int k1 = this.computeLevelFromNeighbor(neighbor, pos, this.getLevel(neighbor));
                        if (out > k1) {
                            out = k1;
                        }

                        if (out == 0) {
                            return out;
                        }
                    }
                }
            }
            IntSet neighborCubeYSet = this.existingCubesForCubeColumns.get(CloPos.setRawY(pos, 0));
            if (neighborCubeYSet != null) {
                for (Integer cubeY : neighborCubeYSet) {
                    long neighbor = CloPos.setRawY(pos, cubeY);
                    assert neighbor != pos;
                    if (neighbor != excludedSourcePos) {
                        int k1 = this.computeLevelFromNeighbor(neighbor, pos, this.getLevel(neighbor));
                        if (out > k1) {
                            out = k1;
                        }

                        if (out == 0) {
                            return out;
                        }
                    }
                }
            }
            return out;
        } else {
            int out = level;

            int x = CloPos.extractRawX(pos);
            int y = CloPos.extractRawY(pos);
            int z = CloPos.extractRawZ(pos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        long neighbor = CloPos.asLong(x + dx, y + dy, z + dz);
                        if (neighbor == pos) {
                            neighbor = Long.MAX_VALUE;
                        }

                        if (neighbor != excludedSourcePos) {
                            int k1 = this.computeLevelFromNeighbor(neighbor, pos, this.getLevel(neighbor));
                            if (out > k1) {
                                out = k1;
                            }

                            if (out == 0) {
                                return out;
                            }
                        }
                    }
                }
            }
            for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
                for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                    int markY = CloPos.packedColumnYMarker(x + dx, z + dz);
                    long neighbor = CloPos.setRawY(pos, markY);
                    if (neighbor == pos) {
                        neighbor = Long.MAX_VALUE;
                    }

                    if (neighbor != excludedSourcePos) {
                        int k1 = this.computeLevelFromNeighbor(neighbor, pos, this.getLevel(neighbor));
                        if (out > k1) {
                            out = k1;
                        }

                        if (out == 0) {
                            return out;
                        }
                    }
                }
            }
            return out;
        }
    }

    /**
     * Returns level propagated from start position with specified level to the neighboring end position.
     */
    @Override protected int computeLevelFromNeighbor(long startPos, long endPos, int startLevel) {
        return startPos == Long.MAX_VALUE ? this.getLevelFromSource(endPos) : startLevel + 1;
    }

    @Override
    protected void setLevel(long cloPos, int level) {
        if (CloPos.isColumn(cloPos)) {
            this.onLevelChange(cloPos, level);
            return;
        }
        long key = CloPos.setRawY(cloPos, 0);
        IntSet cubes = this.existingCubesForCubeColumns.get(key);
        if (level >= noChunkLevel) {
            if (cubes != null) {
                cubes.remove(CloPos.extractRawY(cloPos));
                if (cubes.isEmpty()) {
                    this.existingCubesForCubeColumns.remove(key);
                }
            }
        } else {
            if (cubes == null) {
                this.existingCubesForCubeColumns.put(key, cubes = new IntOpenHashSet());
            }
            cubes.add(CloPos.extractRawY(cloPos));
        }
        this.onLevelChange(cloPos, level);
    }

    protected abstract void onLevelChange(long cloPos, int level);

    protected abstract int getLevelFromSource(long pos);

    public void update(long pos, int level, boolean isDecreasing) {
        this.checkEdge(Long.MAX_VALUE, pos, level, isDecreasing);
    }
}