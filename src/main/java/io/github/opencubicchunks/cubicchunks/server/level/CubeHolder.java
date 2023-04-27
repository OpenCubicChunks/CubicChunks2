package io.github.opencubicchunks.cubicchunks.server.level;

import static io.github.opencubicchunks.cc_core.utils.Utils.unsafeCast;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cc_core.annotation.UsedFromASM;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkHolderAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeStatus;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.ImposterProtoCube;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LevelCube;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkStatus;

public interface CubeHolder {
    // TODO: all of their usages should be replaced with ASM
    @Deprecated
    Either<CubeAccess, ChunkHolder.ChunkLoadingFailure> UNLOADED_CUBE = unsafeCast(ChunkHolder.UNLOADED_CHUNK);
    @Deprecated
    CompletableFuture<Either<CubeAccess, ChunkHolder.ChunkLoadingFailure>> UNLOADED_CUBE_FUTURE = unsafeCast(ChunkHolder.UNLOADED_CHUNK_FUTURE);
    @Deprecated
    Either<LevelCube, ChunkHolder.ChunkLoadingFailure> UNLOADED_LEVEL_CUBE = unsafeCast(ChunkHolder.UNLOADED_LEVEL_CHUNK);
    @Deprecated
    CompletableFuture<Either<LevelCube, ChunkHolder.ChunkLoadingFailure>> UNLOADED_LEVEL_CUBE_FUTURE = unsafeCast(ChunkHolderAccess.getUnloadedLevelChunkFuture());

    static ChunkStatus getCubeStatusFromLevel(int cubeLevel) {
        return cubeLevel < 33 ? ChunkStatus.FULL : CubeStatus.getStatus(cubeLevel - 33);
    }

    // getTickingChunk
    @Nullable
    LevelCube getTickingCube();

    @UsedFromASM
    CubePos getCubePos();

    // getOrScheduleFuture
    CompletableFuture<Either<CubeAccess, ChunkHolder.ChunkLoadingFailure>> getOrScheduleCubeFuture(ChunkStatus chunkStatus, ChunkMap chunkManager);

    // getFutureIfPresentUnchecked
    CompletableFuture<Either<CubeAccess, ChunkHolder.ChunkLoadingFailure>> getCubeFutureIfPresentUnchecked(ChunkStatus chunkStatus);

    // getEntityTickingChunkFuture
    CompletableFuture<Either<LevelCube, ChunkHolder.ChunkLoadingFailure>> getCubeEntityTickingFuture();

    // replaceProtoChunk
    void replaceProtoCube(ImposterProtoCube primer);

    // getFutureIfPresent
    CompletableFuture<Either<CubeAccess, ChunkHolder.ChunkLoadingFailure>> getCubeFutureIfPresent(ChunkStatus chunkStatus);

    void addCubeStageListener(ChunkStatus status, BiConsumer<Either<CubeAccess, ChunkHolder.ChunkLoadingFailure>, Throwable> consumer, ChunkMap chunkManager);

    void broadcastChanges(LevelCube cube);

    // getChunkToSave
    CompletableFuture<CubeAccess> getCubeToSave();

    // added with ASM, can't be shadow because mixin validates shadows before preApply runs
    void updateCubeFutures(ChunkMap chunkManagerIn, Executor executor);

    @UsedFromASM
    class CubeLoadingError implements ChunkHolder.ChunkLoadingFailure {
        private final ChunkHolder holder;

        public CubeLoadingError(ChunkHolder holder) {
            this.holder = holder;
        }

        @Override public String toString() {
            return "Unloaded ticket level " + ((CubeHolder) holder).getCubePos();
        }
    }
}