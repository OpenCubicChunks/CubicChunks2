package io.github.opencubicchunks.cubicchunks.mixin.core.common.level;

import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LevelReader.class)
public class MixinLevelReader {
    @Overwrite(aliases = "getNoiseBiome")
    private
}
