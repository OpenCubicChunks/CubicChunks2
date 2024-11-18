package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer_NoTick extends Player {
    public MixinServerPlayer_NoTick(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    /**
     * @author NotStirred
     * @reason Vanilla ticks players on packet received from the client. We do the minimum possible ticking.
     */
    @Overwrite
    public void doTick() {
        this.noPhysics = true;
        this.onGround = false;
    }
}
