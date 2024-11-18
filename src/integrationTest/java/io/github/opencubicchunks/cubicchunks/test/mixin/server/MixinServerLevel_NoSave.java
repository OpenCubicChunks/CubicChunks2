package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ServerLevel.class)
public class MixinServerLevel_NoSave {
    /**
     * @author NotStirred
     * @reason Integration tests shouldn't save worlds
     */
    @Overwrite
    public void save(@Nullable ProgressListener progress, boolean flush, boolean skipSave) {

    }
}
