package io.github.opencubicchunks.cubicchunks.mixin.asmfixes.common.ticket;

import com.google.common.collect.ImmutableSet;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.server.level.CubicTicketType;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TicketType;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DistanceManager.class)
public class DistanceManagerFixes {
    @Dynamic @Redirect(method = {"addCubePlayer", "removeCubePlayer"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;cube__ReplaceWithMixin()Lio/github/opencubicchunks/cc_core/api/CubePos;"))
    private CubePos sectionPosToCubePos(SectionPos sectionPos) {
        return CubePos.from(sectionPos);
    }

    @Dynamic @Redirect(
        method = { "dasm$redirect$lambda$runAllUpdates$1", "dasm$redirect$method_14040" },
        at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/TicketType;PLAYER_CC_TO_REDIRECT:Lnet/minecraft/server/level/TicketType;"),
        require = 1
    )
    private static TicketType<?> getPlayerTicketTypeCC() {
        return CubicTicketType.PLAYER;
    }

    @Dynamic
    @Redirect(method = {
        "Lnet/minecraft/server/level/DistanceManager;addCubePlayer(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V",
        "Lnet/minecraft/server/level/DistanceManager;removeCubePlayer(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V"
    }, at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/TicketType;PLAYER_CC_TO_REDIRECT:Lnet/minecraft/server/level/TicketType;"))
    private TicketType<?> getPlayerCubicTicketType() {
        return CubicTicketType.PLAYER;
    }

    @Dynamic @Redirect(method = "removeCubeTicketsOnClosing", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/TicketType;LIGHT_CC_TO_REDIRECT:Lnet/minecraft/server/level/TicketType;"))
    private TicketType<?> ticketsToRemove1() {
        return CubicTicketType.LIGHT;
    }

    // TODO: we never actually add cubic post-teleport tickets
    //@Dynamic @Redirect(method = "removeCubeTicketsOnClosing", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/TicketType;POST_TELEPORT_CC_TO_REDIRECT:Lnet/minecraft/server/level/TicketType;"))
    //private TicketType<?> ticketsToRemove2() {
    //    return CubicTicketType.POST_TELEPORT;
    //}

    @Dynamic @Redirect(method = "removeCubeTicketsOnClosing", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/TicketType;UNKNOWN_CC_TO_REDIRECT:Lnet/minecraft/server/level/TicketType;"))
    private TicketType<?> ticketsToRemove3() {
        return CubicTicketType.UNKNOWN;
    }

    @Dynamic @Redirect(method = "removeCubeTicketsOnClosing()V",
        at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableSet;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableSet;"))
    private ImmutableSet<?> modifyTicketTypesToIgnoreCC(Object t1, Object t2, Object t3) {
        return ImmutableSet.of(CubicTicketType.LIGHT, CubicTicketType.UNKNOWN);
    }
}
