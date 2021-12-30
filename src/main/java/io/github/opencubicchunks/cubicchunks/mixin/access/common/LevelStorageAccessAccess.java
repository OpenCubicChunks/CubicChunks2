package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import java.nio.file.Path;

import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelStorageSource.LevelStorageAccess.class)
public interface LevelStorageAccessAccess {
    @Accessor Path getLevelPath();
}
