package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListenerImpl_NoChunkLoad {
    /**
     * @author NotStirred
     * @reason Vanilla impl accesses blocks around the player (and adds tickets)
     */
    @Overwrite
    private boolean noBlocksAround(Entity entity) {
        return true;
    }
}
