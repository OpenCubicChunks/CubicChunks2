package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import io.github.opencubicchunks.cubicchunks.mixin.DasmRedirect;
import io.github.opencubicchunks.cubicchunks.mixin.TransformFrom;
import io.github.opencubicchunks.cubicchunks.server.level.CubicTickingTracker;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloPos;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@DasmRedirect
@Mixin(TickingTracker.class)
public abstract class MixinTickingTracker extends MixinChunkTracker implements CubicTickingTracker {
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void cc_onSetLevel(long pos, int level, CallbackInfo ci) {
        super.cc_onSetLevel(pos, level);
    }

    // TODO we don't want assertion mixins like this outside of dev - put them in mixin.debug instead?
    @Inject(method = "getLevel(Lnet/minecraft/world/level/ChunkPos;)I", at = @At("HEAD"))
    private void cc_onChunkGetLevel(ChunkPos p_184162_, CallbackInfoReturnable<Integer> cir) {
        assert !cc_isCubic;
    }

    @WrapWithCondition(method = "replacePlayerTicketsLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/TickingTracker;addTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"))
    private <T> boolean cc_onReplacePlayerTicketsLevel(TickingTracker instance, TicketType<T> ticketType, ChunkPos pos, int a, T b) {
        if (!cc_isCubic) return true;
        // if isCubic then we expect tickets to be TicketType<CloPos> not TicketType<ChunkPos>
        var cloPos = CloPos.fromLong(pos.toLong());
        this.addTicket((TicketType<CloPos>) ticketType, cloPos, a, cloPos);
        return false;
    }

    @TransformFrom("addTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V")
    public abstract <T> void addTicket(TicketType<T> p_184155_, CloPos p_184156_, int p_184157_, T p_184158_);

    @TransformFrom("removeTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V")
    public abstract <T> void removeTicket(TicketType<T> p_184169_, CloPos p_184170_, int p_184171_, T p_184172_);
}
