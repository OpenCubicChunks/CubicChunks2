package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ServerPlayer.class)
public class MixinServerPlayer_NoChunkLoad {
    /**
     * @author NotStirred
     * @reason In integration tests we never check this (internally it checks blocks, and loads chunks)
     */
    @Overwrite
    public void doCheckFallDamage(double y, boolean onGround) {
    }
}
