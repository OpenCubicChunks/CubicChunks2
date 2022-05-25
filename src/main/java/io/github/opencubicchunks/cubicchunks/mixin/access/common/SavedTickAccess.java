package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.world.ticks.SavedTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SavedTick.class)
public interface SavedTickAccess {
    @Accessor
    static String getTAG_ID() {
        throw new Error("Mixin not applied!");
    }

    @Accessor
    static String getTAG_X() {
        throw new Error("Mixin not applied!");
    }

    @Accessor
    static String getTAG_Y() {
        throw new Error("Mixin not applied!");
    }

    @Accessor
    static String getTAG_Z() {
        throw new Error("Mixin not applied!");
    }

    @Accessor
    static String getTAG_DELAY() {
        throw new Error("Mixin not applied!");
    }

    @Accessor
    static String getTAG_PRIORITY() {
        throw new Error("Mixin not applied!");
    }
}
