package io.github.opencubicchunks.cubicchunks.mixin.core.common;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.DasmRedirect;
import io.github.opencubicchunks.cubicchunks.mixin.TransformFrom;
import io.github.opencubicchunks.cubicchunks.mixin.TransformFrom.Signature;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkMap.class)
@DasmRedirect
public abstract class TestMixin {
    @TransformFrom(value = "getChunkDebugData", signature = @Signature(
        args = { ChunkPos.class },
        ret = String.class
    ))
    public abstract String getCubeDebugData(CubePos pos);

    @TransformFrom(value = "markPositionReplaceable(Lnet/minecraft/world/level/ChunkPos;)V")
    public abstract void markCubePositionReplaceable(CubePos pos);
}
