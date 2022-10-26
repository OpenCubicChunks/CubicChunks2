package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree;

import static io.github.opencubicchunks.cc_core.utils.Coords.blockToCube;
import static io.github.opencubicchunks.cc_core.utils.Coords.blockToSection;
import static io.github.opencubicchunks.cc_core.utils.Coords.cubeToMinBlock;
import static io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerNode.MAX_SCALE;

import java.util.function.IntPredicate;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.world.heightmap.HeightmapSource;
import io.github.opencubicchunks.cc_core.world.heightmap.HeightmapStorage;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerBranch;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerNode;
import io.github.opencubicchunks.cubicchunks.world.BigChunk;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.commons.lang3.NotImplementedException;

// TODO some things in this class can be cleaned up now that we don't extend Heightmap
public class BigSurfaceTrackerWrapper {
    public static final Heightmap.Types[] HEIGHTMAP_TYPES = Heightmap.Types.values();

    protected final SurfaceTrackerBranch surfaceTracker;
    /** global x of min block in column */
    protected final int dx;
    /** global z of min block in column */
    protected final int dz;

    protected final BigChunk bigChunk;

    public BigSurfaceTrackerWrapper(BigChunk bigChunk, Heightmap.Types types, HeightmapStorage storage) {
        this.bigChunk = bigChunk;
        this.surfaceTracker = loadOrCreateRoot(bigChunk.getPos().getX(), bigChunk.getPos().getZ(), (byte) types.ordinal(), storage);
        this.dx = cubeToMinBlock(bigChunk.getPos().getX());
        this.dz = cubeToMinBlock(bigChunk.getPos().getZ());
    }

    protected BigSurfaceTrackerWrapper(BigChunk bigChunk, SurfaceTrackerBranch root) {
        this.bigChunk = bigChunk;
        this.surfaceTracker = root;
        this.dx = cubeToMinBlock(bigChunk.getPos().getX());
        this.dz = cubeToMinBlock(bigChunk.getPos().getZ());
    }

    /**
     *
     * @param columnLocalX column-local x
     * @param globalY global y
     * @param columnLocalZ column-local z
     * @param blockState unused.
     * @return currently unused; always false
     */
    public boolean update(int columnLocalX, int globalY, int columnLocalZ, BlockState blockState) {
        surfaceTracker.getLeaf(blockToCube(globalY)).onSetBlock(dx + columnLocalX, globalY, dz + columnLocalZ, opaquePredicateForState(blockState));
        // We always return false, because the result is never used anywhere anyway (by either vanilla or us)
        return false;
    }

    public int getFirstAvailable(int columnLocalX, int columnLocalZ) {
        return surfaceTracker.getHeight(columnLocalX + dx, columnLocalZ + dz) + 1;
    }

    long[] getRawData(int columnDx, int columnDz) {
        var heightBits = Mth.ceillog2(bigChunk.getHeightAccessor().getHeight() + 1);
        var data = new SimpleBitStorage(heightBits, 256);
        surfaceTracker.writeDataForClient(dx+columnDx, dz+columnDz, data, bigChunk.getHeightAccessor().getMinBuildHeight());
        return data.getRaw();
    }

    public synchronized void loadCube(HeightmapStorage storage, HeightmapSource source) {
        this.surfaceTracker.loadSource(blockToSection(dx), blockToSection(dz), storage, source);
    }

    public void saveAll(HeightmapStorage storage) {
        int globalSectionX = blockToSection(this.dx);
        int globalSectionZ = blockToSection(this.dz);
        saveAllChildren(globalSectionX, globalSectionZ, storage, this.surfaceTracker);
    }

    private void saveAllChildren(int globalSectionX, int globalSectionZ, HeightmapStorage storage, SurfaceTrackerNode node) {
        node.save(globalSectionX, globalSectionZ, storage);
        if (node.getScale() > 0) {
            for (SurfaceTrackerNode child : ((SurfaceTrackerBranch) node).getChildren()) {
                if (child == null) {
                    continue;
                }
                saveAllChildren(globalSectionX, globalSectionZ, storage, child);
            }
        }
    }

    @Nullable
    public SurfaceTrackerLeaf getLeaf(int nodeY) {
        return surfaceTracker.getLeaf(nodeY);
    }

    public SurfaceTrackerBranch getSurfaceTrackerSection() {
        return this.surfaceTracker;
    }

    public static IntPredicate opaquePredicateForState(BlockState blockState) {
        return heightmapType -> {
            if (heightmapType == -1) {
                throw new NotImplementedException("Currently we are always marking light as dirty, so this should never be reached");
            } else {
                return HEIGHTMAP_TYPES[heightmapType].isOpaque().test(blockState);
            }
        };
    }

    protected static SurfaceTrackerBranch loadOrCreateRoot(int globalSectionX, int globalSectionZ, byte type, HeightmapStorage storage) {
        SurfaceTrackerNode loadedNode = storage.loadNode(globalSectionX, globalSectionZ, null, type, MAX_SCALE, 0);
        if (loadedNode != null) {
            return (SurfaceTrackerBranch) loadedNode;
        }
        return new SurfaceTrackerBranch(MAX_SCALE, 0, null, type);
    }
}
