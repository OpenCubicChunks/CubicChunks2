package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import java.util.concurrent.Executor;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.thread.ProcessorHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DistanceManager.class)
public interface DistanceManagerAccess {
    @Invoker void invokeAddTicket(long chunkPosIn, Ticket<?> ticketIn);

    @Invoker void invokeUpdatePlayerTickets(int viewDistance);

    @Invoker SortedArraySet<Ticket<?>> invokeGetTickets(long position);
    @Invoker void invokeRemoveTicket(long pos, Ticket<?> ticket);

    @Accessor ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> getTicketThrottlerInput();
    @Accessor Executor getMainThreadExecutor();
    @Accessor LongSet getTicketsToRelease();
    @Accessor ProcessorHandle<ChunkTaskPriorityQueueSorter.Release> getTicketThrottlerReleaser();
}