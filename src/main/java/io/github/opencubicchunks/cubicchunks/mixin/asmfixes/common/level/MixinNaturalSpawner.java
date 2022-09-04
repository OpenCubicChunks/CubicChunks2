package io.github.opencubicchunks.cubicchunks.mixin.asmfixes.common.level;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NaturalSpawner.class)
public class MixinNaturalSpawner {
    @Dynamic @Redirect(method = "isRightDistanceToPlayerAndSpawnPointForCube", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getCubePos()"
        + "Lio/github/opencubicchunks/cc_core/api/CubePos;"))
    private static CubePos getCubePosChunkToCube(ChunkAccess cube) {
        return ((CubeAccess) cube).getCubePos();
    }
}
