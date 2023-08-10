package io.github.opencubicchunks.cubicchunks.mixin.core.common.server;

import java.util.List;
import java.util.Optional;

import com.mojang.authlib.GameProfile;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.chunk.VerticalViewDistanceListener;
import io.github.opencubicchunks.cubicchunks.network.PacketCCLevelInfo;
import io.github.opencubicchunks.cubicchunks.network.PacketCubeCacheRadius;
import io.github.opencubicchunks.cubicchunks.network.PacketDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(PlayerList.class)
public abstract class MixinPlayerList implements VerticalViewDistanceListener {

    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private List<ServerPlayer> players;
    private int verticalViewDistance;
    private int incomingVerticalViewDistance;

    @Override public void setIncomingVerticalViewDistance(int verticalDistance) {
        this.incomingVerticalViewDistance = verticalDistance;
    }

    @Override public int getVerticalViewDistance() {
        return this.verticalViewDistance;
    }

    @Inject(method = "setViewDistance", at = @At("HEAD"))
    private void setVerticalViewDistance(int viewDistance, CallbackInfo ci) {
        this.verticalViewDistance = incomingVerticalViewDistance;

        PacketDispatcher.sendTo(new PacketCubeCacheRadius(viewDistance, verticalViewDistance), this.players);

        for (ServerLevel serverLevel : this.server.getAllLevels()) {
            if (serverLevel != null) {
                if (((CubicLevelHeightAccessor) serverLevel).isCubic()) {
                    ((VerticalViewDistanceListener) serverLevel.getChunkSource()).setIncomingVerticalViewDistance(this.verticalViewDistance);
                }
            }
        }
    }

    // ClientboundLoginPacket instantiates the ClientLevel on the client, so we send our packet just before that
    @Inject(method = "placeNewPlayer", locals = LocalCapture.CAPTURE_FAILHARD, at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundLoginPacket"))
    private void onPlaceNewPlayer(Connection connection, ServerPlayer player, CallbackInfo ci, GameProfile gameProfile,
                                  String string, CompoundTag compoundTag, ResourceKey resourceKey, ServerLevel possiblyNullLevel, ServerLevel level) {
        PacketDispatcher.sendTo(new PacketCCLevelInfo(((CubicLevelHeightAccessor) level).worldStyle()), player);
    }

    // ClientboundRespawnPacket instantiates the ClientLevel on the client, so we send our packet just before that
    @Inject(method = "respawn", locals = LocalCapture.CAPTURE_FAILHARD, at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundRespawnPacket"))
    private void onRespawn(ServerPlayer oldPlayer, boolean bl, CallbackInfoReturnable<ServerPlayer> cir, BlockPos blockPos, float f, boolean bl2, ServerLevel possiblyNullLevel,
                           Optional optional2, ServerLevel level, ServerPlayer newPlayer, boolean bl3, byte b, LevelData levelData) {
        PacketDispatcher.sendTo(new PacketCCLevelInfo(((CubicLevelHeightAccessor) level).worldStyle()), newPlayer);
    }
}
