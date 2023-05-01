package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import java.util.List;

import com.google.common.collect.ImmutableList;
import io.github.opencubicchunks.cc_core.config.EarlyConfig;
import io.github.opencubicchunks.cc_core.utils.Coords;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.Util;
import net.minecraft.world.level.chunk.ChunkStatus;

public class CubeStatus {
    private static final Object2IntOpenHashMap<ChunkStatus> CUBE_TASK_RANGE_XZ = new Object2IntOpenHashMap<>();

    static {
        for (ChunkStatus chunkStatus : ChunkStatus.getStatusList()) {
            int r = Coords.sectionToCubeCeil(chunkStatus.getRange());
            CUBE_TASK_RANGE_XZ.put(chunkStatus, r);
        }
    }

    private static final List<ChunkStatus> STATUS_BY_RANGE_16 = ImmutableList.of(
        ChunkStatus.FULL,
        ChunkStatus.FEATURES,
        ChunkStatus.LIQUID_CARVERS,
        ChunkStatus.BIOMES,
        ChunkStatus.STRUCTURE_STARTS,
        ChunkStatus.STRUCTURE_STARTS,
        ChunkStatus.STRUCTURE_STARTS,
        ChunkStatus.STRUCTURE_STARTS,
        ChunkStatus.STRUCTURE_STARTS,
        ChunkStatus.STRUCTURE_STARTS,
        ChunkStatus.STRUCTURE_STARTS,
        ChunkStatus.STRUCTURE_STARTS
    );

    private static final List<ChunkStatus> STATUS_BY_RANGE_32 = ImmutableList.of(
        ChunkStatus.FULL,
        ChunkStatus.FEATURES,
        ChunkStatus.LIQUID_CARVERS,
        ChunkStatus.BIOMES,
        ChunkStatus.STRUCTURE_STARTS,
        ChunkStatus.STRUCTURE_STARTS,
        ChunkStatus.STRUCTURE_STARTS,
        ChunkStatus.STRUCTURE_STARTS
    );

    private static final List<ChunkStatus> STATUS_BY_RANGE_64 = ImmutableList.of(
        ChunkStatus.FULL,
        ChunkStatus.FEATURES,
        ChunkStatus.LIQUID_CARVERS,
        ChunkStatus.BIOMES,
        ChunkStatus.STRUCTURE_STARTS,
        ChunkStatus.STRUCTURE_STARTS
    );

    private static final List<ChunkStatus> STATUS_BY_RANGE_128 = ImmutableList.of(
        ChunkStatus.FULL,
        ChunkStatus.FEATURES,
        ChunkStatus.LIQUID_CARVERS,
        ChunkStatus.BIOMES,
        ChunkStatus.STRUCTURE_STARTS
    );

    private static final List<ChunkStatus> STATUS_BY_RANGE = getStatusByRange();

    private static final IntList RANGE_BY_STATUS = Util.make(new IntArrayList(ChunkStatus.getStatusList().size()), (rangeByStatus) -> {
        int range = 0;

        for (int status = ChunkStatus.getStatusList().size() - 1; status >= 0; --status) {
            while (range + 1 < STATUS_BY_RANGE.size() && status <= STATUS_BY_RANGE.get(range + 1).getIndex()) {
                ++range;
            }
            rangeByStatus.add(0, range);
        }
    });

    private static List<ChunkStatus> getStatusByRange() {
        int cubeDiameter = EarlyConfig.getDiameterInSections();
        switch (cubeDiameter) {
            case 1:
                return STATUS_BY_RANGE_16;
            case 2:
                return STATUS_BY_RANGE_32;
            case 4:
                return STATUS_BY_RANGE_64;
            case 8:
                return STATUS_BY_RANGE_128;
            default:
                throw new UnsupportedOperationException("Unsupported cube size " + cubeDiameter);
        }
    }

    public static int maxDistance() {
        return STATUS_BY_RANGE.size();
    }

    public static int getDistance(ChunkStatus status) {
        return RANGE_BY_STATUS.getInt(status.getIndex());
    }

    public static int getCubeTaskRange(ChunkStatus chunkStatusIn) {
        return CUBE_TASK_RANGE_XZ.getInt(chunkStatusIn);
    }

    public static ChunkStatus getStatusAroundFullCube(int radius) {
        if (radius >= STATUS_BY_RANGE.size()) {
            return ChunkStatus.EMPTY;
        }
        if (radius < 0) {
            return ChunkStatus.FULL;
        }
        return STATUS_BY_RANGE.get(radius);
    }

    public static int cubeToChunkLevel(int cubeLevel) {
        return cubeLevel < 33 ? cubeLevel : ChunkStatus.getDistance(CubeStatus.getStatusAroundFullCube(cubeLevel - 33)) + 33;
    }
}