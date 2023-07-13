package io.github.opencubicchunks.cubicchunks.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkStatus;

public final class LightingIntegrationTest {
    public final String testName;
    public final long seed;

    public final Consumer<TestContext> setup;
    public final Consumer<TestContext> tick;
    public final Consumer<TestContext> teardown;

    private IntegrationTests.TestState state = IntegrationTests.TestState.NONE;
    private boolean finished = false;
    private BlockPos failurePos = null;
    private Throwable throwable = null;

    public LightingIntegrationTest(
        String testName, long seed,
        Consumer<TestContext> setup,
        Consumer<TestContext> tick,
        Consumer<TestContext> teardown
    ) {
        this.testName = testName;
        this.seed = seed;
        this.setup = setup;
        this.tick = tick;
        this.teardown = teardown;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public IntegrationTests.TestState getState() {
        return this.state;
    }

    public Optional<BlockPos> getFailurePos() {
        return Optional.ofNullable(this.failurePos);
    }

    public Optional<Throwable> getThrown() {
        return Optional.ofNullable(this.throwable);
    }

    @Override
    public String toString() {
        return "LightingIntegrationTest[" +
            "setup=" + setup + ", " +
            "test=" + tick + ", " +
            "teardown=" + teardown + ']';
    }

    public final class TestContext {
        private final ServerLevel level;

        public TestContext(ServerLevel level) {
            this.level = level;
        }

        public void fail(Throwable t) {
            finished = true;
            state = IntegrationTests.TestState.FAIL;
            throwable = t;
        }

        public void pass() {
            if (!finished) {
                finished = true;
                state = IntegrationTests.TestState.PASS;
            }
        }

        public ServerLevel level() {
            return level;
        }

        public void setFailurePos(@Nullable BlockPos pos) {
            failurePos = pos;
        }

        public CompletableFuture<Void> getCubeLoadFuturesInVolume(CubePos minPos, CubePos maxPos, ChunkStatus requiredStatus) {
            var serverChunkCache = ((CubeLoadUnload) level.getChunkSource());
            List<CompletableFuture<Either<CubeAccess, ChunkHolder.ChunkLoadingFailure>>> futures = new ArrayList<>();
            // for each cube: get the cube future
            for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                    for (int y = minPos.getY(); y <= minPos.getY(); y++) {
                        futures.add(serverChunkCache.getCubeFuture(CubePos.of(x, y, z), requiredStatus));
                    }
                }
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        }

        public CompletableFuture<Void> getCubeUnloadFuturesInVolume(CubePos minPos, CubePos maxPos, ChunkStatus requiredStatus) {
            var serverChunkCache = ((CubeLoadUnload) level.getChunkSource());
            List<CompletableFuture<CubeAccess>> futures = new ArrayList<>();
            // for each cube: get the cube future
            for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                    for (int y = minPos.getY(); y <= minPos.getY(); y++) {
                        futures.add(serverChunkCache.getCubeUnloadFuture(CubePos.of(x, y, z), requiredStatus));
                    }
                }
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        }

        public interface CubeLoadUnload {
            CompletableFuture<Either<CubeAccess, ChunkHolder.ChunkLoadingFailure>> getCubeFuture(CubePos pos, ChunkStatus requiredStatus);

            CompletableFuture<CubeAccess> getCubeUnloadFuture(CubePos pos, ChunkStatus requiredStatus);
        }
    }
}
