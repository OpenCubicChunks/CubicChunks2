package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class MixinPlayerLists_SetSpectatorOnJoin {
    @Inject(method = "placeNewPlayer", at = @At(value = ("RETURN")))
    private void setSpectator(Connection netManager, ServerPlayer player, CallbackInfo ci) {
        player.setGameMode(GameType.SPECTATOR);
    }
}
