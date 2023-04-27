package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.Set;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.annotation.UsedFromASM;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeStatus;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.chunk.ChunkStatus;

/**
 * A majority of this class is generated with DASM
 */
public interface CubicDistanceManager {
    int PLAYER_CUBE_TICKET_LEVEL = 33 + CubeStatus.getDistance(ChunkStatus.FULL) - 2;

    @UsedFromASM void purgeStaleCubeTickets();

    // isChunkToRemove
    boolean isCubeToRemove(long sectionPos);

    @Nullable
    ChunkHolder getCube(long cubePos);

    @Nullable
    ChunkHolder updateCubeScheduling(long cubePosIn, int newLevel, @Nullable ChunkHolder holder, int oldLevel);

    @UsedFromASM boolean runAllUpdatesCubic(ChunkMap chunkManager);

    @UsedFromASM void addCubeTicket(long chunkPosIn, Ticket<?> ticketIn);

    @UsedFromASM void removeCubeTicket(long chunkPosIn, Ticket<?> ticketIn);

    @UsedFromASM <T> void addCubeTicket(TicketType<T> type, CubePos pos, int level, T value);

    @UsedFromASM <T> void removeCubeTicket(TicketType<T> type, CubePos pos, int level, T value);

    @UsedFromASM <T> void addCubeRegionTicket(TicketType<T> type, CubePos pos, int distance, T value);

    @UsedFromASM <T> void removeCubeRegionTicket(TicketType<T> type, CubePos pos, int distance, T value);

    @UsedFromASM SortedArraySet<Ticket<?>> getCubeTickets(long cubePosLong);

    @UsedFromASM void updateCubeForced(CubePos pos, boolean add);

    @UsedFromASM void addCubePlayer(SectionPos sectionPos, ServerPlayer player);

    @UsedFromASM void removeCubePlayer(SectionPos sectionPos, ServerPlayer player);

    @UsedFromASM boolean isEntityTickingRangeCube(long cubePos);

    @UsedFromASM boolean isBlockTickingRangeCube(long cubePos);

    // updatePlayerTickets, implemented manually - horizontal+vertical distance
    void updatePlayerCubeTickets(int horizontalViewDistance, int verticalViewDistance);

    @UsedFromASM int getNaturalSpawnCubeCount();

    @UsedFromASM boolean hasPlayersNearbyCube(long cubePosIn);

    @UsedFromASM void removeCubeTicketsOnClosing();

    // accessors implemented manually

    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> getCubeTickets();

    Long2ObjectMap<ObjectSet<ServerPlayer>> getPlayersPerCube();

    ProcessorHandle<CubeTaskPriorityQueueSorter.Message<Runnable>> getCubeTicketThrottlerInput();

    ProcessorHandle<CubeTaskPriorityQueueSorter.Release> getCubeTicketThrottlerReleaser();

    LongSet getCubeTicketsToRelease();

    Set<ChunkHolder> getCubesToUpdateFutures();

    Executor getMainThreadExecutor();

    CubeTaskPriorityQueueSorter getCubeTicketThrottler();

    //TODO: Is there a better way of figuring out if this world is generating chunks or cubes?!
    void initCubic(boolean world);
}