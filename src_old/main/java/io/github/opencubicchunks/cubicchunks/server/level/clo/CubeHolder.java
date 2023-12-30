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
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloPos;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.LevelClo;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkStatus;

public interface CubeHolder {

    @UsedFromASM static ChunkStatus getCubeStatusFromLevel(int cubeLevel) {
        return cubeLevel < 33 ? ChunkStatus.FULL : CubeStatus.getStatusAroundFullCube(cubeLevel - 33);
    }

    // getTickingChunk
    @Nullable
    LevelCube getTickingCube();

    boolean isClo();

    CloPos getCloPos();

    // getOrScheduleFuture
    CompletableFuture<Either<CloAccess, ChunkHolder.ChunkLoadingFailure>> getOrScheduleCloFuture(ChunkStatus chunkStatus, ChunkMap chunkManager);

    // getFutureIfPresentUnchecked
    CompletableFuture<Either<CloAccess, ChunkHolder.ChunkLoadingFailure>> getCloFutureIfPresentUnchecked(ChunkStatus chunkStatus);

    // getEntityTickingChunkFuture
    CompletableFuture<Either<LevelClo, ChunkHolder.ChunkLoadingFailure>> getCloEntityTickingFuture();

    // replaceProtoChunk
    void replaceProtoCube(ImposterProtoCube primer);

    // getFutureIfPresent
    CompletableFuture<Either<CloAccess, ChunkHolder.ChunkLoadingFailure>> getCloFutureIfPresent(ChunkStatus chunkStatus);

    void addCloStageListener(ChunkStatus status, BiConsumer<Either<CloAccess, ChunkHolder.ChunkLoadingFailure>, Throwable> consumer, ChunkMap chunkManager);

    void broadcastChanges(LevelClo cube);

    // getChunkToSave
    CompletableFuture<CloAccess> getCloToSave();

    // added with ASM, can't be shadow because mixin validates shadows before preApply runs
    void updateCloFutures(ChunkMap chunkManagerIn, Executor executor);

    class CloLoadingError implements ChunkHolder.ChunkLoadingFailure {
        private final ChunkHolder holder;

        public CloLoadingError(ChunkHolder holder) {
            this.holder = holder;
        }

        @Override public String toString() {
            return "Unloaded CLO ticket level " + ((CubeHolder) holder).getCloPos();
        }
    }
}