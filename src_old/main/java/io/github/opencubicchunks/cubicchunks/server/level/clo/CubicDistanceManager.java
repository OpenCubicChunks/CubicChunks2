package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.Set;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

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

    // implemented by ASM unless specified otherwise
    void purgeStaleCubeTickets();

    // isChunkToRemove
    boolean isCubeToRemove(long sectionPos);

    @Nullable
    ChunkHolder getCube(long cubePos);

    @Nullable
    ChunkHolder updateCubeScheduling(long cubePosIn, int newLevel, @Nullable ChunkHolder holder, int oldLevel);

    boolean runAllUpdatesCubic(ChunkMap chunkManager);

    void addCubeTicket(long chunkPosIn, Ticket<?> ticketIn);

    void removeCubeTicket(long chunkPosIn, Ticket<?> ticketIn);

    <T> void addCubeTicket(TicketType<T> type, CubePos pos, int level, T value);

    <T> void removeCubeTicket(TicketType<T> type, CubePos pos, int level, T value);

    <T> void addCubeRegionTicket(TicketType<T> type, CubePos pos, int distance, T value);

    <T> void removeCubeRegionTicket(TicketType<T> type, CubePos pos, int distance, T value);

    SortedArraySet<Ticket<?>> getCubeTickets(long cubePosLong);

    void updateCubeForced(CubePos pos, boolean add);

    void addCubePlayer(SectionPos sectionPos, ServerPlayer player);

    void removeCubePlayer(SectionPos sectionPos, ServerPlayer player);

    boolean isEntityTickingRangeCube(long cubePos);

    boolean isBlockTickingRangeCube(long cubePos);

    // updatePlayerTickets, implemented manually - horizontal+vertical distance
    void updatePlayerCubeTickets(int horizontalViewDistance, int verticalViewDistance);

    int getNaturalSpawnCubeCount();

    boolean hasPlayersNearbyClo(long cloPosIn);

    void removeCubeTicketsOnClosing();


    // accessors implemented manually

    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> getCubeTickets();

    Long2ObjectMap<ObjectSet<ServerPlayer>> getPlayersPerCube();

    ProcessorHandle<CloTaskPriorityQueueSorter.Message<Runnable>> getCubeTicketThrottlerInput();

    ProcessorHandle<CloTaskPriorityQueueSorter.Release> getCubeTicketThrottlerReleaser();

    LongSet getCubeTicketsToRelease();

    Set<ChunkHolder> getCubesToUpdateFutures();

    Executor getMainThreadExecutor();

    CloTaskPriorityQueueSorter getCubeTicketThrottler();

    //TODO: Is there a better way of figuring out if this world is generating chunks or cubes?!
    void initCubic(boolean world);
}