package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import io.github.opencubicchunks.cubicchunks.server.level.CubicTicketType;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DistanceManager.class)
public abstract class MixinDistanceManager_PreventPlayerTickets {
    @Dynamic @Inject(method = { "addTicket(JLnet/minecraft/server/level/Ticket;)V", "addCubeTicket(JLnet/minecraft/server/level/Ticket;)V" }, at = @At("HEAD"), cancellable = true)
    private void cancelPlayerTickets(long position, Ticket<?> ticket, CallbackInfo ci) {
        if (ticket.getType() == TicketType.PLAYER || ticket.getType() == CubicTicketType.PLAYER) {
            ci.cancel();
        }
    }
}
