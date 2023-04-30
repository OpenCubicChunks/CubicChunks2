package io.github.opencubicchunks.cubicchunks.mixin.core.common.ticket;

import java.util.Set;
import java.util.concurrent.Executor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.utils.MathUtil;
import io.github.opencubicchunks.cubicchunks.chunk.VerticalViewDistanceListener;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.TicketAccess;
import io.github.opencubicchunks.cubicchunks.server.level.CubeTaskPriorityQueueSorter;
import io.github.opencubicchunks.cubicchunks.server.level.CubeTicketTracker;
import io.github.opencubicchunks.cubicchunks.server.level.CubeTickingTracker;
import io.github.opencubicchunks.cubicchunks.server.level.CubicDistanceManager;
import io.github.opencubicchunks.cubicchunks.server.level.CubicPlayerTicketTracker;
import io.github.opencubicchunks.cubicchunks.server.level.CubicTicketType;
import io.github.opencubicchunks.cubicchunks.server.level.FixedPlayerDistanceCubeTracker;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeStatus;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.thread.ProcessorHandle;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DistanceManager.class)
public abstract class MixinDistanceManager implements CubicDistanceManager, VerticalViewDistanceListener {

    @Final @Shadow Executor mainThreadExecutor;

    private boolean isCubic;

    // fields below used from ASM
    final Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerCube = new Long2ObjectOpenHashMap<>();
    final Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> cubeTickets = new Long2ObjectOpenHashMap<>();
    private final CubeTicketTracker cubeTicketTracker = new CubeTicketTracker((DistanceManager) (Object) this);

    private final FixedPlayerDistanceCubeTracker naturalSpawnCubeCounter = new FixedPlayerDistanceCubeTracker(this, 8 / CubicConstants.DIAMETER_IN_SECTIONS);
    private final CubeTickingTracker tickingCubeTicketsTracker = new CubeTickingTracker();
    private final CubicPlayerTicketTracker cubicPlayerTicketManager = new CubicPlayerTicketTracker(this, MathUtil.ceilDiv(33, CubicConstants.DIAMETER_IN_SECTIONS));
    public final Set<ChunkHolder> cubesToUpdateFutures = Sets.newHashSet();
    public CubeTaskPriorityQueueSorter cubeTicketThrottler;
    public ProcessorHandle<CubeTaskPriorityQueueSorter.Message<Runnable>> cubeTicketThrottlerInput;
    public ProcessorHandle<CubeTaskPriorityQueueSorter.Release> cubeTicketThrottlerReleaser;
    public final LongSet cubeTicketsToRelease = new LongOpenHashSet();

    private long cubeTicketTickCounter;

    @Shadow abstract void addTicket(long position, Ticket<?> ticket);

    @Shadow @Final private Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void init(Executor backgroundExecutor, Executor mainThreadExecutor_, CallbackInfo ci) {
        ProcessorHandle<Runnable> mainThreadHandle = ProcessorHandle.of("player ticket throttler", mainThreadExecutor_::execute);
        CubeTaskPriorityQueueSorter throttler = new CubeTaskPriorityQueueSorter(ImmutableList.of(mainThreadHandle), backgroundExecutor, 16);
        this.cubeTicketThrottler = throttler;
        this.cubeTicketThrottlerInput = throttler.getProcessor(mainThreadHandle, true);
        this.cubeTicketThrottlerReleaser = throttler.getReleaseProcessor(mainThreadHandle);
    }

    @Inject(method = "purgeStaleTickets", at = @At("RETURN"))
    protected void purgeStaleCubeTickets(CallbackInfo ci) {
        if (!isCubic) {
            return;
        }
        purgeStaleCubeTickets();
    }

    @Inject(method = "runAllUpdates", at = @At("RETURN"), cancellable = true)
    public void runAllUpdatesCubicChunks(ChunkMap chunkManager, CallbackInfoReturnable<Boolean> cir) {
        if (!isCubic) {
            return;
        }
        boolean anyUpdated = runAllUpdatesCubic(chunkManager);
        cir.setReturnValue(anyUpdated | cir.getReturnValueZ());
    }

    /**
     * Add chunk tickets for the cube. These are removed on cube unload
     */
    @Dynamic @Inject(method = "addCubeTicket(JLnet/minecraft/server/level/Ticket;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/SortedArraySet;addOrGet(Ljava/lang/Object;)Ljava/lang/Object;"))
    private void onCubeTicketAdded(long cubePosIn, Ticket<?> ticketIn, CallbackInfo ci) {
        // force a ticket on the cube's columns
        CubePos cubePos = CubePos.from(cubePosIn);
        int chunkTicketLevel = CubeStatus.cubeToChunkLevel(ticketIn.getTicketLevel());
        for (int localX = 0; localX < CubicConstants.DIAMETER_IN_SECTIONS; localX++) {
            for (int localZ = 0; localZ < CubicConstants.DIAMETER_IN_SECTIONS; localZ++) {
                //do not need to handle region tickets due to the additional CCColumn tickets added in cube generation stages
                addTicket(CubePos.asChunkPosLong(cubePosIn, localX, localZ), TicketAccess.createNew(CubicTicketType.COLUMN, chunkTicketLevel, cubePos));
            }
        }
    }

    // No need to do anything on removing cube tickets, as chunks must always be loaded if a cube is loaded.
    // Chunk tickets are instead removed once a cube has been unloaded

    @Inject(method = "addPlayer", at = @At("RETURN"))
    public void addCubePlayer(SectionPos sectionPos, ServerPlayer player, CallbackInfo ci) {
        if (!isCubic) {
            return;
        }
        addCubePlayer(sectionPos, player);
    }

    @Inject(method = "removePlayer", at = @At("RETURN"))
    public void removeCubePlayer(SectionPos sectionPos, ServerPlayer player, CallbackInfo ci) {
        if (!isCubic) {
            return;
        }
        removeCubePlayer(sectionPos, player);
    }

    @Override public void updatePlayerCubeTickets(int horizontalDistance, int verticalDistance) {
        this.cubicPlayerTicketManager.updateCubeViewDistance(Coords.sectionToCubeRenderDistance(horizontalDistance), Coords.sectionToCubeRenderDistance(verticalDistance));
    }

    // COLUMN tickets shouldn't be removed, as they are removed only on cube unload
    @Redirect(method = "removeTicketsOnClosing", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableSet;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)"
        + "Lcom/google/common/collect/ImmutableSet;"))
    private ImmutableSet<?> modifyTicketTypesToIgnore(Object t1, Object t2, Object t3) {
        return ImmutableSet.of(t1, t2, t3, CubicTicketType.COLUMN);
    }

    @Dynamic @Redirect(method = "removeCubeTicketsOnClosing()V",
        at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableSet;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableSet;"))
    private ImmutableSet<?> modifyTicketTypesToIgnoreCC(Object t1, Object t2, Object t3) {
        return ImmutableSet.of(CubicTicketType.LIGHT, CubicTicketType.UNKNOWN, t1, t2, t3);
    }

    @Inject(method = "removeTicketsOnClosing", at = @At("HEAD"))
    private void onRemoveTicketsOnClosing(CallbackInfo ci) {
        if (isCubic) {
            removeCubeTicketsOnClosing();
        }
    }


    @Override
    public Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> getCubeTickets() {
        return cubeTickets;
    }

    @Override
    public ProcessorHandle<CubeTaskPriorityQueueSorter.Message<Runnable>> getCubeTicketThrottlerInput() {
        return cubeTicketThrottlerInput;
    }

    @Override
    public ProcessorHandle<CubeTaskPriorityQueueSorter.Release> getCubeTicketThrottlerReleaser() {
        return cubeTicketThrottlerReleaser;
    }

    @Override
    public LongSet getCubeTicketsToRelease() {
        return cubeTicketsToRelease;
    }

    @Override
    public Set<ChunkHolder> getCubesToUpdateFutures() {
        return this.cubesToUpdateFutures;
    }

    @Override
    public Executor getMainThreadExecutor() {
        return mainThreadExecutor;
    }

    @Override
    public CubeTaskPriorityQueueSorter getCubeTicketThrottler() {
        return cubeTicketThrottler;
    }

    @Override
    public Long2ObjectMap<ObjectSet<ServerPlayer>> getPlayersPerCube() {
        return this.playersPerCube;
    }

    @Override public void initCubic(boolean newIsCubic) {
        this.isCubic = newIsCubic;
    }


}