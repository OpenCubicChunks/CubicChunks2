package io.github.opencubicchunks.cubicchunks.mixin.asmfixes.common.ticket;

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
        at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/TicketType;PLAYER:Lnet/minecraft/server/level/TicketType;"),
        require = 1
    )
    private static TicketType<?> getPlayerTicketTypeCC() {
        return CubicTicketType.PLAYER;
    }
}
