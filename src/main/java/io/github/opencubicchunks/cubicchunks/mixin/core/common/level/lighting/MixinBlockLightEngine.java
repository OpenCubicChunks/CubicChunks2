package io.github.opencubicchunks.cubicchunks.mixin.core.common.level.lighting;

import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;

@SuppressWarnings("rawtypes")
@Mixin(BlockLightEngine.class)
public abstract class MixinBlockLightEngine extends MixinLightEngine {

}
