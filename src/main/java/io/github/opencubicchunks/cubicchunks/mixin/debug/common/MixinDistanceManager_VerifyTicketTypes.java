package io.github.opencubicchunks.cubicchunks.mixin.debug.common;

import io.github.opencubicchunks.cc_core.api.CubePos;
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
public class MixinDistanceManager_VerifyTicketTypes {
    @Dynamic @Inject(method = {
        "addCubeTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V",
        "addCubeRegionTicket"
    }, at = @At("HEAD"))
    private void checkAddingTicketTypes(TicketType<?> type, CubePos pos, int level, Object value, CallbackInfo ci) {
        if (type != CubicTicketType.PLAYER && type != CubicTicketType.LIGHT && type != CubicTicketType.COLUMN && type != CubicTicketType.UNKNOWN && type != CubicTicketType.FORCED && type != TicketType.START) {
            throw new IllegalArgumentException("Invalid ticket type " + type);
        }
    }

    @Dynamic @Inject(method = "addCubeTicket(JLnet/minecraft/server/level/Ticket;)V", at = @At("HEAD"))
    private void checkAddingTicketTypes2(long chunkPosIn, Ticket<?> ticketIn, CallbackInfo ci) {
        TicketType<?> type = ticketIn.getType();
        if (type != CubicTicketType.PLAYER && type != CubicTicketType.LIGHT && type != CubicTicketType.COLUMN && type != CubicTicketType.UNKNOWN && type != CubicTicketType.FORCED && type != TicketType.START) {
            throw new IllegalArgumentException("Invalid ticket type " + type);
        }
    }
}
