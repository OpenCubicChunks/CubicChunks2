package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloPos;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DistanceManager.PlayerTicketTracker.class)
public abstract class MixinPlayerTicketTracker extends MixinFixedPlayerDistanceChunkTracker {
    @WrapOperation(method = "onLevelChange(JIZZ)V", at = @At(value = "NEW",
        target = "(Lnet/minecraft/server/level/TicketType;ILjava/lang/Object;)Lnet/minecraft/server/level/Ticket;"))
    private Ticket<?> cc_onTicketConstruct(TicketType<?> ttype, int a, Object pos, Operation<Ticket> original) {
        if (!cc_isCubic)
            return original.call(ttype, a, pos);
        return original.call(ttype, a, CloPos.fromLong(((ChunkPos) pos).toLong()));
    }
    @WrapWithCondition(method = "runAllUpdates", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ChunkTaskPriorityQueueSorter;onLevelChange(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/IntSupplier;ILjava/util/function/IntConsumer;)V"))
    private boolean cc_onTicketThrottlerLevelChange(ChunkTaskPriorityQueueSorter instance, ChunkPos pos, IntSupplier supplier, int i, IntConsumer consumer) {
        if (!cc_isCubic) return true;
        // TODO convert pos to CloPos and call cubic variant of method
        return false;
    }
}
