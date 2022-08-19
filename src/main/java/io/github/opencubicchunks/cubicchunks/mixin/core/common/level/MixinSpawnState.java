package io.github.opencubicchunks.cubicchunks.mixin.core.common.level;

import io.github.opencubicchunks.cubicchunks.world.CubicLocalMobCapCalculator;
import io.github.opencubicchunks.cubicchunks.world.CubicNaturalSpawner;
import io.github.opencubicchunks.cubicchunks.world.level.CubePos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NaturalSpawner.SpawnState.class)
public class MixinSpawnState implements CubicNaturalSpawner.CubicSpawnState {
    @Shadow @Final private LocalMobCapCalculator localMobCapCalculator;

    @Override
    public boolean canSpawnForCategory(MobCategory mobCategory, CubePos cubePos) {
        return ((CubicLocalMobCapCalculator) localMobCapCalculator).canSpawn(mobCategory, cubePos);
    }

    @Redirect(
        method = "afterSpawn",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LocalMobCapCalculator;addMob(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/entity/MobCategory;)V")
    )
    private void addToCubic(LocalMobCapCalculator instance, ChunkPos chunkPos, MobCategory mobCategory, Mob mob, ChunkAccess chunkAccess) {
        CubicLocalMobCapCalculator cubicLocalMobCapCalculator = (CubicLocalMobCapCalculator) instance;

        if (cubicLocalMobCapCalculator.isCubic()) {
            cubicLocalMobCapCalculator.addMob(new CubePos(mob.blockPosition()), mobCategory);
        } else {
            instance.addMob(chunkPos, mobCategory);
        }
    }
}
