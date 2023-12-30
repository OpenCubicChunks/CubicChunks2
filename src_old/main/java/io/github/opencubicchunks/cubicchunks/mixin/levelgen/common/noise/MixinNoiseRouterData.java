package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common.noise;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NoiseRouterData.class)
public class MixinNoiseRouterData {
    private static final int NORMAL_PACKED_Y_LENGTH = 64 - 2 * Mth.log2(Mth.smallestEncompassingPowerOfTwo(30000000));
    private static final int NORMAL_Y_SIZE = (1 << NORMAL_PACKED_Y_LENGTH) - 32;
    private static final int NORMAL_MAX_Y = (NORMAL_Y_SIZE >> 1) - 1;
    private static final int NORMAL_MIN_Y = NORMAL_MAX_Y - NORMAL_Y_SIZE + 1;

    //Use normal Y values for noise so that default generators don't get messed up
    @Redirect(method = "bootstrap", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/dimension/DimensionType;MAX_Y:I"))
    private static int getMaxY() {
        return NORMAL_MAX_Y;
    }

    @Redirect(method = "bootstrap", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/dimension/DimensionType;MIN_Y:I"))
    private static int getMinY() {
        return NORMAL_MIN_Y;
    }
}
