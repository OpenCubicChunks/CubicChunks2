package io.github.opencubicchunks.cubicchunks.mixin.core.common.level;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.levelgen.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.world.CubicNaturalSpawner;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LevelCube;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathComputationType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NaturalSpawner.class, priority = 0)// Assume absolute priority because of Y checks found here, we should always WANT to run first
public abstract class MixinNaturalSpawner {

    private static final boolean USE_HAS_CEILING_SPAWN_LOGIC = false;

    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void cancelSpawnForChunk(ServerLevel serverLevel, LevelChunk levelChunk, NaturalSpawner.SpawnState spawnState, boolean bl,
                                            boolean bl2, boolean bl3, CallbackInfo ci) {
        if (!serverLevel.isCubic()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "isSpawnPositionOk", at = @At(value = "HEAD"), cancellable = true)
    private static void isSpawnPositionOkForCubeWorldGenRegion(SpawnPlacements.Type location, LevelReader reader, BlockPos pos, @Nullable EntityType<?> entityType,
                                                               CallbackInfoReturnable<Boolean> cir) {
        if (!reader.isCubic()) {
            return;
        }
        if (reader instanceof CubeWorldGenRegion) {
            CubeWorldGenRegion level = (CubeWorldGenRegion) reader;
            int lowestAllowedY = Coords.cubeToMinBlock(level.getMainCubeY());
            int highestAllowedY = Coords.cubeToMaxBlock(level.getMainCubeY());
            if (pos.getY() < lowestAllowedY + 1 || pos.getY() > highestAllowedY - 1) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "getTopNonCollidingPos", at = @At("HEAD"), cancellable = true)
    private static void returnOnBrokenPosition(LevelReader reader, EntityType<?> entityType, int x, int z, CallbackInfoReturnable<BlockPos> cir) {
        if (!reader.isCubic()) {
            return;
        }
        if (reader instanceof CubeWorldGenRegion) {
            CubeWorldGenRegion level = (CubeWorldGenRegion) reader;
            BlockPos.MutableBlockPos newPos = new BlockPos.MutableBlockPos(x, level.getHeight(SpawnPlacements.getHeightmapType(entityType), x, z), z);
            int lowestAllowedY = Coords.cubeToMinBlock(level.getMainCubeY());
            int highestAllowedY = Coords.cubeToMaxBlock(level.getMainCubeY());

            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos().set(newPos);

            if (level.dimensionType().hasCeiling() && USE_HAS_CEILING_SPAWN_LOGIC) {
                cir.setReturnValue(ceilingLogic(entityType, level, newPos, lowestAllowedY, highestAllowedY, mutableBlockPos));
                return;
            }

            if (newPos.getY() < lowestAllowedY + 1 || newPos.getY() > highestAllowedY - 1) {
                cir.setReturnValue(newPos);
            }
        }
    }

    // TODO: Is there a better way of doing this using the mixins commented out below?! We need the height checks before the air checks to ensure we don't throw out of bounds :/
    private static BlockPos ceilingLogic(EntityType<?> entityType, CubeWorldGenRegion region, BlockPos.MutableBlockPos newPos, int lowestAllowedY,
                                         int highestAllowedY, BlockPos.MutableBlockPos mutableBlockPos) {
        do {
            mutableBlockPos.move(Direction.DOWN);
        } while (mutableBlockPos.getY() > Coords.cubeToMinBlock(region.getMainCubeY()) + 1 && !region.getBlockState(mutableBlockPos).isAir());

        do {
            mutableBlockPos.move(Direction.DOWN);
        } while (mutableBlockPos.getY() > Coords.cubeToMinBlock(region.getMainCubeY()) + 1 && region.getBlockState(mutableBlockPos).isAir());

        if (SpawnPlacements.getPlacementType(entityType) == SpawnPlacements.Type.ON_GROUND) {
            BlockPos blockPos = mutableBlockPos.below();
            if (blockPos.getY() < lowestAllowedY + 1 || blockPos.getY() > highestAllowedY - 1) {
                return newPos;
            }

            if (region.getBlockState(mutableBlockPos).isPathfindable(region, mutableBlockPos, PathComputationType.LAND)) {
                return mutableBlockPos;
            }
        }
        return mutableBlockPos.immutable();
    }

    @Redirect(method = "getTopNonCollidingPos", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/DimensionType;hasCeiling()Z"))
    private static boolean useOverWorldLogic(DimensionType dimensionType, LevelReader level, EntityType<?> entityType, int x, int z) {
        if (!level.isCubic()) {
            return dimensionType.hasCeiling();
        }
        return false;
    }

    //Called from ASM
    private static BlockPos getRandomPosWithinCube(Level level, LevelCube cubeAccess) {
        CubePos pos = cubeAccess.getCubePos();
        int blockX = pos.minCubeX() + level.random.nextInt(CubicConstants.DIAMETER_IN_BLOCKS);
        int blockZ = pos.minCubeZ() + level.random.nextInt(CubicConstants.DIAMETER_IN_BLOCKS);

        int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) + 1; //This is wrong, we need to use the one from the BigCube(ChunkAccess)

        int minY = pos.minCubeY();
        if (minY > height) {
            return new BlockPos(blockX, Integer.MIN_VALUE, blockZ);
        }

        if (pos.maxCubeY() <= height) {
            int blockY = minY + level.random.nextInt(CubicConstants.DIAMETER_IN_BLOCKS);
            return new BlockPos(blockX, blockY, blockZ);
        }

        return new BlockPos(blockX, Mth.randomBetweenInclusive(level.random, minY, height), blockZ);
    }


    @Inject(method = "isRightDistanceToPlayerAndSpawnPoint", at = @At("HEAD"), cancellable = true)
    private static void useCubePos(ServerLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
        if (!level.isCubic()) {
            return;
        }
        cir.setReturnValue(CubicNaturalSpawner.isRightDistanceToPlayerAndSpawnPoint(level, chunk, pos, squaredDistance));
    }
}
