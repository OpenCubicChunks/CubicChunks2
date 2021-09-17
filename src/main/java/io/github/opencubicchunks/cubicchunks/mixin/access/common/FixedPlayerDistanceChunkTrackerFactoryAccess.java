package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DistanceManager.FixedPlayerDistanceChunkTracker.class)
public interface FixedPlayerDistanceChunkTrackerFactoryAccess {

    @Invoker("<init>")
    static DistanceManager.FixedPlayerDistanceChunkTracker construct(DistanceManager ticketManager, int i) {
        throw new Error("Mixin did not apply.");
    }
}