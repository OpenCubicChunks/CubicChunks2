package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DynamicGraphMinFixedPoint.class)
public interface DynamicGraphMinFixedPointAccess {
    @Invoker void invokeCheckEdge(long fromPos, long toPos, int newLevel, boolean isDecreasing);
    @Invoker int invokeComputeLevelFromNeighbor(long startPos, long endPos, int startLevel);
    @Invoker int invokeGetLevel(long sectionPosIn);

    //3-int variants
    /*void invokeCheckEdge(int fromPos_x, int fromPos_y, int fromPos_z, int toPos_x, int toPos_y, int toPos_z, int newLevel, boolean isDecreasing);
    int invokeComputeLevelFromNeighbor(int startPos_x, int startPos_y, int startPos_z, int endPos_x, int endPos_y, int endPos_z, int startLevel);
    int invokeGetLevel(int sectionPos_x, int sectionPos_y, int sectionPos_z);*/
}