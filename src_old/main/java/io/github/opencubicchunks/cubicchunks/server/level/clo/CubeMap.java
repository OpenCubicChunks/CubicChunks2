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
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeStatus;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloPos;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.LevelClo;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
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
    int sizeClo();

    // implemented by ASM in MainTransformer
    @Nullable
    ChunkHolder updateCloScheduling(long cloPosIn, int newLevel, @Nullable ChunkHolder holder, int oldLevel);

    void setServerChunkCache(ServerChunkCache cache);

    // implemented by ASM
    void markCloPositionReplaceable(CloPos cubePos);

    // implemented by ASM
    byte markCloPosition(CloPos cubePos, ChunkStatus.ChunkType status);

    // getUpdatingChunkIfPresent, implemented by ASM
    @Nullable
    ChunkHolder getUpdatingCloIfPresent(long cubePosIn);

    // getVisibleChunkIfPresent, implemented by ASM
    @Nullable
    ChunkHolder getVisibleCloIfPresent(long cubePosIn);

    // schedule
    CompletableFuture<Either<CloAccess, ChunkHolder.ChunkLoadingFailure>> scheduleClo(ChunkHolder chunkHolderIn, ChunkStatus chunkStatusIn);

    // prepareAccessibleChunk, implemented by ASM
    CompletableFuture<Either<LevelClo, ChunkHolder.ChunkLoadingFailure>> prepareAccessibleClo(ChunkHolder chunkHolder);

    // prepareTickingChunk
    CompletableFuture<Either<LevelClo, ChunkHolder.ChunkLoadingFailure>> prepareTickingClo(ChunkHolder chunkHolder);

    // getChunkRangeFuture
    CompletableFuture<Either<List<CloAccess>, ChunkHolder.ChunkLoadingFailure>> getCloRangeFuture(CloPos pos, int radius,
                                                                                                  IntFunction<ChunkStatus> getParentStatus);

    // prepareEntityTickingChunk, implemented by ASM
    CompletableFuture<Either<LevelClo, ChunkHolder.ChunkLoadingFailure>> prepareEntityTickingClo(CloPos pos);

    // getChunks, implemented by ASM
    Iterable<ChunkHolder> getCubesAndColumns();

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
    IntSupplier getCloQueueLevel(long cloPos);

    // releaseLightTicket, implemented by ASM
    void releaseCloLightTicket(CloPos cubePos);

    // anyPlayerCloseEnoughForSpawning, implemented by ASM
    boolean anyPlayerCloseEnoughForSpawning(CloPos cubePos);

    // getPlayersCloseForSpawning, implemented by ASM
    List<ServerPlayer> getPlayersCloseForSpawning(CloPos cubePos);

    Long2ObjectLinkedOpenHashMap<ChunkHolder> getUpdatingCloMap();
}