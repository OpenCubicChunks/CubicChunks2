package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import java.util.Optional;

import io.github.opencubicchunks.cubicchunks.test.ServerTestRunner;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer_SpawnLocationTest extends Entity {
    public MixinServerPlayer_SpawnLocationTest(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow public abstract void moveTo(double x, double y, double z);

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;fudgeSpawnLocation(Lnet/minecraft/server/level/ServerLevel;)V"))
    private void spawnAtTestLocation(ServerPlayer instance, ServerLevel serverLevel) {
        Pair<ServerLevel, Optional<BlockPos>> spawnLocation = ((ServerTestRunner) serverLevel.getServer()).firstErrorLocation();

        if (spawnLocation.second().isPresent()) {
            this.moveTo(spawnLocation.second().get(), 0.0f, 0.0f);
            this.setPos(this.getX(), this.getY() + 1.0, this.getZ());
        } else {
            this.moveTo(new BlockPos(0, 0, 0), 0.0f, 0.0f);
            this.setPos(this.getX(), this.getY() + 1.0, this.getZ());
        }
    }
}
