package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StructureManager.class)
public interface StructureManagerAccess {

    @Accessor WorldOptions getWorldOptions();
}
