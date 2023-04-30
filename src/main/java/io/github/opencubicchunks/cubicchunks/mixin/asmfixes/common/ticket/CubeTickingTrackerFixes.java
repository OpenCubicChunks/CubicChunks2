package io.github.opencubicchunks.cubicchunks.mixin.asmfixes.common.ticket;

import io.github.opencubicchunks.cubicchunks.server.level.CubeTickingTracker;
import io.github.opencubicchunks.cubicchunks.server.level.CubicTicketType;
import net.minecraft.server.level.TicketType;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CubeTickingTracker.class)
public class CubeTickingTrackerFixes {
    @Dynamic @Redirect(method = "replacePlayerTicketsLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/TicketType;PLAYER_CC_TO_REDIRECT:Lnet/minecraft/server/level/TicketType;"))
    private TicketType<?> cubicPlayerTicket() {
        return CubicTicketType.PLAYER;
    }
}
