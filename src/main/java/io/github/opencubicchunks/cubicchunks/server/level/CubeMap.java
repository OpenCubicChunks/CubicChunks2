package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cc_core.annotation.UsedFromASM;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeStatus;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LevelCube;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkStatus;

public interface CubeMap {
    @UsedFromASM int MAX_CUBE_DISTANCE = 33 + CubeStatus.maxDistance();

    // getTickingGenerated
    int getTickingGeneratedCubes();

    // size()
    int sizeCubes();

    // implemented by ASM in MainTransformer
    @Nullable
    ChunkHolder updateCubeScheduling(long cubePosIn, int newLevel, @Nullable ChunkHolder holder, int oldLevel);

    void setServerChunkCache(ServerChunkCache cache);

    // used from ASM
    void markCubePositionReplaceable(CubePos cubePos);

    // used from ASM
    byte markCubePosition(CubePos cubePos, ChunkStatus.ChunkType status);

    LongSet getCubesToDrop();

    // getUpdatingChunkIfPresent
    @Nullable
    ChunkHolder getUpdatingCubeIfPresent(long cubePosIn);

    // getVisibleChunkIfPresent
    @Nullable
    ChunkHolder getVisibleCubeIfPresent(long cubePosIn);

    // schedule
    CompletableFuture<Either<CubeAccess, ChunkHolder.ChunkLoadingFailure>> scheduleCube(ChunkHolder chunkHolderIn,
                                                                                        ChunkStatus chunkStatusIn);

    // prepareAccessibleChunk, implemented by ASM
    CompletableFuture<Either<LevelCube, ChunkHolder.ChunkLoadingFailure>> prepareAccessibleCube(ChunkHolder chunkHolder);

    // prepareTickingChunk
    CompletableFuture<Either<LevelCube, ChunkHolder.ChunkLoadingFailure>> prepareTickingCube(ChunkHolder chunkHolder);

    // getChunkRangeFuture
    CompletableFuture<Either<List<CubeAccess>, ChunkHolder.ChunkLoadingFailure>> getCubeRangeFuture(CubePos pos, int radius,
                                                                                                    IntFunction<ChunkStatus> getParentStatus);

    // prepareEntityTickingChunk, implemented by ASM
    CompletableFuture<Either<LevelCube, ChunkHolder.ChunkLoadingFailure>> prepareEntityTickingCube(CubePos pos);

    // getChunks
    Iterable<ChunkHolder> getCubes();

    // checkerboardDistance
    // replacement of checkerboardDistance, checks view distance instead of returning distance
    // because we also have vertical view distance
    static boolean isInViewDistance(CubePos pos, ServerPlayer player, boolean useCameraPosition, int hDistance, int vDistance) {
        int x;
        int y;
        int z;
        if (useCameraPosition) {
            SectionPos sectionpos = player.getLastSectionPos();
            x = Coords.sectionToCube(sectionpos.x());
            y = Coords.sectionToCube(sectionpos.y());
            z = Coords.sectionToCube(sectionpos.z());
        } else {
            x = Coords.getCubeXForEntity(player);
            y = Coords.getCubeYForEntity(player);
            z = Coords.getCubeZForEntity(player);
        }

        return isInCubeDistance(pos, x, y, z, hDistance, vDistance);
    }

    static boolean isInCubeDistance(CubePos pos, int x, int y, int z, int hDistance, int vDistance) {
        int dX = pos.getX() - x;
        int dY = pos.getY() - y;
        int dZ = pos.getZ() - z;
        int xzDistance = Math.max(Math.abs(dX), Math.abs(dZ));
        return xzDistance <= hDistance && Math.abs(dY) <= vDistance;
    }

    static int getCubeCheckerboardDistanceXZ(CubePos pos, ServerPlayer player, boolean useCameraPosition) {
        int x;
        int z;
        if (useCameraPosition) {
            SectionPos sectionpos = player.getLastSectionPos();
            x = Coords.sectionToCube(sectionpos.x());
            z = Coords.sectionToCube(sectionpos.z());
        } else {
            x = Coords.getCubeXForEntity(player);
            z = Coords.getCubeZForEntity(player);
        }

        return getCubeDistanceXZ(pos, x, z);
    }

    static int getCubeCheckerboardDistanceY(CubePos pos, ServerPlayer player, boolean useCameraPosition) {
        int y;
        if (useCameraPosition) {
            SectionPos sectionpos = player.getLastSectionPos();
            y = Coords.sectionToCube(sectionpos.y());
        } else {
            y = Coords.getCubeYForEntity(player);
        }
        return Math.abs(pos.getY() - y);
    }

    static int getCubeDistanceXZ(CubePos cubePosIn, int x, int z) {
        int dX = cubePosIn.getX() - x;
        int dZ = cubePosIn.getZ() - z;
        return Math.max(Math.abs(dX), Math.abs(dZ));
    }

    // getChunkQueueLevel, implemented by ASM
    IntSupplier getCubeQueueLevel(long cubePosIn);

    // releaseLightTicket
    void releaseCubeLightTicket(CubePos cubePos);

    // anyPlayerCloseEnoughForSpawning, implemented by ASM
    boolean anyPlayerCloseEnoughForSpawning(CubePos cubePos);

    // getPlayersCloseForSpawning, implemented by ASM
    List<ServerPlayer> getPlayersCloseForSpawning(CubePos cubePos);

    Long2ObjectLinkedOpenHashMap<ChunkHolder> getUpdatingCubeMap();
}