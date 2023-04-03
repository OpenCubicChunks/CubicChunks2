package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import io.github.opencubicchunks.cc_core.annotation.UsedFromASM;
import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.ProcessorHandle;

@UsedFromASM
public class CubeTaskPriorityQueueSorter implements AutoCloseable, CubeHolderLevelChangeListener {

    public CubeTaskPriorityQueueSorter(List<ProcessorHandle<?>> taskExecutors, Executor executor, int p_i50713_3_) {
        throw new Error("ASM didn't apply");
    }

    @UsedFromASM
    public static Message<Runnable> message(Runnable runnable, long pos, IntSupplier intSupplier) {
        throw new Error("ASM didn't apply");
    }

    @UsedFromASM
    public static Message<Runnable> message(ChunkHolder holder, Runnable runnable) {
        throw new Error("ASM didn't apply");
    }

    @UsedFromASM
    public static Release release(Runnable runnable, long pos, boolean flag) {
        throw new Error("ASM didn't apply");
    }

    @UsedFromASM
    public <T> ProcessorHandle<Message<T>> getProcessor(ProcessorHandle<T> processorHandle, boolean flag) {
        throw new Error("ASM didn't apply");
    }

    // func_219091_a, getReleaseProcessor
    @UsedFromASM
    public ProcessorHandle<Release> getReleaseProcessor(ProcessorHandle<Runnable> processorHandle) {
        throw new Error("ASM didn't apply");
    }

    // func_219066_a, onLevelChange
    @Override
    @UsedFromASM
    public void onCubeLevelChange(CubePos pos, IntSupplier getLevel, int level, IntConsumer setLevel) {
        throw new Error("ASM didn't apply");
    }

    @Override public void close() {
        throw new Error("ASM didn't apply");
    }

    @UsedFromASM
    public static final class Message<T> {
        @UsedFromASM final Function<ProcessorHandle<Unit>, T> task;
        @UsedFromASM final long pos;
        @UsedFromASM final IntSupplier level;

        Message(Function<ProcessorHandle<Unit>, T> function, long l, IntSupplier intSupplier) {
            this.task = function;
            this.pos = l;
            this.level = intSupplier;
        }
    }

    @UsedFromASM
    public static final class Release {
        @UsedFromASM final Runnable task;
        @UsedFromASM final long pos;
        @UsedFromASM final boolean clearQueue;

        Release(Runnable runnable, long l, boolean bl) {
            this.task = runnable;
            this.pos = l;
            this.clearQueue = bl;
        }
    }
}