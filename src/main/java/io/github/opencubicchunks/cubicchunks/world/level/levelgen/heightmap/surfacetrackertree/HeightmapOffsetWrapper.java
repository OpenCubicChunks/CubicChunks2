package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree;

import static io.github.opencubicchunks.cc_core.utils.Coords.cubeToMinBlock;
import static io.github.opencubicchunks.cc_core.utils.Coords.sectionToCube;
import static io.github.opencubicchunks.cc_core.utils.Coords.sectionToMinBlock;

import io.github.opencubicchunks.cubicchunks.mixin.access.common.HeightmapAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

public class HeightmapOffsetWrapper extends Heightmap {
    public static final Heightmap.Types[] HEIGHTMAP_TYPES = Heightmap.Types.values();

    protected final BigSurfaceTrackerWrapper surfaceTracker;
    /** bigChunk-local x of min block in column */
    protected final int dx;
    /** bigChunk-local z of min block in column */
    protected final int dz;

    public HeightmapOffsetWrapper(ChunkAccess chunkAccess, Types types, BigSurfaceTrackerWrapper surfaceTracker) {
        super(chunkAccess, types);
        //noinspection ConstantConditions
        ((HeightmapAccess) this).setIsOpaque(null);
        this.surfaceTracker = surfaceTracker;

        // TODO maybe introduce more functions in Coords to make this less cursed?
        this.dx = sectionToMinBlock(chunkAccess.getPos().x) - cubeToMinBlock(sectionToCube(chunkAccess.getPos().x));
        this.dz = sectionToMinBlock(chunkAccess.getPos().z) - cubeToMinBlock(sectionToCube(chunkAccess.getPos().z));
    }

    /**
     *
     * @param columnLocalX column-local x
     * @param globalY global y
     * @param columnLocalZ column-local z
     * @param blockState unused.
     * @return currently unused; always false
     */
    @Override
    public boolean update(int columnLocalX, int globalY, int columnLocalZ, BlockState blockState) {
        return surfaceTracker.update(dx+columnLocalX, globalY, dx+columnLocalZ, blockState);
    }

    @Override
    public int getFirstAvailable(int columnLocalX, int columnLocalZ) {
        return surfaceTracker.getFirstAvailable(columnLocalX + dx, columnLocalZ + dz);
    }

    @Override
    public void setRawData(ChunkAccess clv, Types a, long[] ls) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long[] getRawData() {
        return surfaceTracker.getRawData(dx, dz);
    }
}
