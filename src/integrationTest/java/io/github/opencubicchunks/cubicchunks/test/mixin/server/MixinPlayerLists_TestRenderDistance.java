package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public class MixinPlayerLists_TestRenderDistance {
    private static final int CLIENT_RENDER_DISTANCE = 64;
    
    @Redirect(method = "placeNewPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/server/players/PlayerList;viewDistance:I"))
    private int testClientRenderDistance(PlayerList instance) {
        return CLIENT_RENDER_DISTANCE;
    }

    @Redirect(method = "placeNewPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/server/players/PlayerList;simulationDistance:I"))
    private int testClientSimulationDistance(PlayerList instance) {
        return CLIENT_RENDER_DISTANCE;
    }
}
