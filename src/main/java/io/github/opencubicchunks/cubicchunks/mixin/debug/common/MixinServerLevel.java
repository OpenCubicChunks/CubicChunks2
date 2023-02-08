package io.github.opencubicchunks.cubicchunks.mixin.debug.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import io.github.opencubicchunks.cc_core.world.ColumnCubeMap;
import io.github.opencubicchunks.cc_core.world.ColumnCubeMapGetter;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerBranch;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerNode;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkMapAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LightHeightmapGetter;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.LightSurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerWrapper;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class MixinServerLevel {
    private static final boolean DEBUG_HEIGHTMAP_VERIFICATION_ENABLED = System.getProperty("cubicchunks.debug.heightmapverification", "false").equals("true");
    private static final int DEBUG_HEIGHTMAP_VERIFICATION_FREQ = (int) Math.max(1, 20 / Float.parseFloat(System.getProperty("cubicchunks.debug.heightmapverification.frequency", "1")));

    @Shadow @Final private ServerChunkCache chunkSource;

    private int ticksPassed = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void verifyHeightmapState(BooleanSupplier booleanSupplier, CallbackInfo ci) {
        if (!DEBUG_HEIGHTMAP_VERIFICATION_ENABLED) {
            return;
        }

        this.ticksPassed++;

        if (this.ticksPassed == DEBUG_HEIGHTMAP_VERIFICATION_FREQ) {
            ticksPassed = 0;

            Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap = ((ChunkMapAccess) this.chunkSource.chunkMap).getVisibleChunkMap();

            visibleChunkMap.forEach((chunkPosLong, holder) -> {
                ChunkAccess chunk = holder.getLastAvailable();

                //ProtoChunks only contain a global light heightmap
                if (chunk instanceof ProtoChunk protoChunk && !(chunk instanceof ImposterProtoChunk)) {
                    ColumnCubeMap cubeMap = protoChunk.getCubeMap();
                    LightSurfaceTrackerWrapper heightmap = protoChunk.getServerLightHeightmap();
                    if (heightmap != null) {
                        SurfaceTrackerBranch root = heightmap.getSurfaceTrackerSection();
                        verifyHeightmapTree(root, cubeMap);
                    }
                }
                if (chunk instanceof LevelChunk levelChunk) {
                    for (Map.Entry<Heightmap.Types, Heightmap> heightmap : levelChunk.getHeightmaps()) {
                        verifyHeightmapTree(((SurfaceTrackerWrapper) heightmap.getValue()).getSurfaceTrackerSection(), ((ColumnCubeMapGetter) levelChunk).getCubeMap());
                    }

                    verifyHeightmapTree(((LightHeightmapGetter) levelChunk).getServerLightHeightmap().getSurfaceTrackerSection(), ((ColumnCubeMapGetter) levelChunk).getCubeMap());
                }
            });
        }
    }

    /**
     * Rough duplicate of SurfaceTrackerNodesTest#verifyHeightmapTree, which is in the test module
     */
    private void verifyHeightmapTree(SurfaceTrackerBranch root, ColumnCubeMap cubeMap) {
        //Collect all leaves in the cubemap
        List<SurfaceTrackerLeaf> requiredLeaves = new ArrayList<>();
        for (Integer cubeY : cubeMap.getLoaded()) {
            SurfaceTrackerLeaf leaf = root.getLeaf(cubeY);
            if (leaf != null) {
                //Leaves can be null when a protocube is marked as loaded in the cubemap, but hasn't yet been added to the global heightmap
                requiredLeaves.add(leaf);
            }
        }

        LongSet requiredPositions = new LongOpenHashSet();
        //Root will not be added if there are no leaves in the tree, so we add it here
        requiredPositions.add(ChunkPos.asLong(root.getScale(), root.getScaledY()));
        //Collect all positions that are required to be loaded
        for (SurfaceTrackerLeaf leaf : requiredLeaves) {
            SurfaceTrackerNode node = leaf;
            while (node != null) {
                requiredPositions.add(ChunkPos.asLong(node.getScale(), node.getScaledY()));

                if (node instanceof SurfaceTrackerBranch branch) {
                    for (SurfaceTrackerNode child : branch.getChildren()) {
                        if (child != null) {
                            requiredPositions.add(ChunkPos.asLong(child.getScale(), child.getScaledY()));
                        }
                    }
                }

                SurfaceTrackerBranch parent = node.getParent();
                if (node.getScale() < SurfaceTrackerNode.MAX_SCALE && parent == null) {
                    throw new IllegalStateException("Detached heightmap branch exists?!");
                }
                node = parent;
            }
        }

        //Verify that heightmap meets requirements (all parents are loaded, and their direct children)
        verifyAllNodesInRequiredSet(root, requiredPositions);
    }

    private static void verifyAllNodesInRequiredSet(SurfaceTrackerBranch branch, LongSet requiredNodes) {
        for (SurfaceTrackerNode child : branch.getChildren()) {
            if (child != null) {
                if (!requiredNodes.contains(ChunkPos.asLong(child.getScale(), child.getScaledY()))) {
                    throw new IllegalStateException("Heightmap borken");
                }

                if (branch.getScale() != 1) {
                    verifyAllNodesInRequiredSet((SurfaceTrackerBranch) child, requiredNodes);
                }
            }
        }
    }
}
