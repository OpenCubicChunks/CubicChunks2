package io.github.opencubicchunks.cubicchunks.mixin.core.common.entity;

import com.mojang.authlib.GameProfile;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.network.PacketCCLevelInfo;
import io.github.opencubicchunks.cubicchunks.network.PacketDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer extends Player {
    public MixinServerPlayer(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @Shadow public abstract ServerLevel getLevel();

    @Redirect(method = "trackChunk",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 0))
    public void onSendChunkLoad(ServerGamePacketListenerImpl packetListener, Packet<?> packet) {
        if (!((CubicLevelHeightAccessor) this.getLevel()).isCubic()) {
            packetListener.send(packet);
        }
    }

    // ClientboundRespawnPacket instantiates the ClientLevel on the client, so we send our packet just before that
    @Inject(method = "changeDimension", at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundRespawnPacket"))
    private void onChangeDimension(ServerLevel serverLevel, CallbackInfoReturnable<Entity> cir) {
        PacketDispatcher.sendTo(new PacketCCLevelInfo(((CubicLevelHeightAccessor) serverLevel).worldStyle()), (ServerPlayer) (Object) this);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundRespawnPacket"))
    private void onTeleportTo(ServerLevel serverLevel, double d, double e, double f, float g, float h, CallbackInfo ci) {
        PacketDispatcher.sendTo(new PacketCCLevelInfo(((CubicLevelHeightAccessor) serverLevel).worldStyle()), (ServerPlayer) (Object) this);
    }
}
