package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.util.thread.ProcessorHandle;

public class CubeTaskPriorityQueueSorter implements AutoCloseable, CubeHolderLevelChangeListener {

    public CubeTaskPriorityQueueSorter(List<ProcessorHandle<?>> taskExecutors, Executor executor, int p_i50713_3_) {
        throw new Error("ASM didn't apply");
    }

    public static ChunkTaskPriorityQueueSorter.Message<Runnable> message(Runnable runnable, long pos, IntSupplier intSupplier) {
        throw new Error("ASM didn't apply");
    }

    public static ChunkTaskPriorityQueueSorter.Message<Runnable> message(ChunkHolder holder, Runnable runnable) {
        throw new Error("ASM didn't apply");
    }

    public static ChunkTaskPriorityQueueSorter.Release release(Runnable runnable, long pos, boolean flag) {
        throw new Error("ASM didn't apply");
    }

    public <T> ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<T>> getProcessor(ProcessorHandle<T> processorHandle, boolean flag) {
        throw new Error("ASM didn't apply");
    }

    // func_219091_a, getReleaseProcessor
    public ProcessorHandle<ChunkTaskPriorityQueueSorter.Release> getReleaseProcessor(ProcessorHandle<Runnable> processorHandle) {
        throw new Error("ASM didn't apply");
    }

    // func_219066_a, onLevelChange
    @Override
    public void onCubeLevelChange(CubePos pos, IntSupplier getLevel, int level, IntConsumer setLevel) {
        throw new Error("ASM didn't apply");
    }

    @Override public void close() {
        throw new Error("ASM didn't apply");
    }
}