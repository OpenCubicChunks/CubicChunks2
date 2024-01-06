package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cubicchunks.MarkableAsCubic;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloPos;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ChunkTracker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkTracker.class)
public abstract class MixinChunkTracker extends DynamicGraphMinFixedPoint implements MarkableAsCubic {
    protected boolean cc_isCubic;
    private int cc_noChunkLevel;

    private Long2ObjectMap<IntSet> cc_existingCubesForCubeColumns;

    protected MixinChunkTracker() {
        super(0, 0, 0);
    }

    // TODO decide upon a standard way of marking objects as cubic
    @Override
    public void cc_setCubic() {
        cc_isCubic = true;
        cc_existingCubesForCubeColumns = new Long2ObjectLinkedOpenHashMap<>();
        // TODO do we ever need this to be anything else? in the original CloTracker impl there was a constructor that allowed setting noChunkLevel separately
        cc_noChunkLevel = levelCount-1;
    }

    @Redirect(method="*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/ChunkPos;INVALID_CHUNK_POS:J"))
    private long cc_sentinelValue() {
        if (cc_isCubic)
            return CloPos.INVALID_CLO_POS;
        return ChunkPos.INVALID_CHUNK_POS;
    }

    @ModifyConstant(method = "computeLevelFromNeighbor", constant = @Constant(intValue = 1))
    private int cc_dontIncrementLevelOnCubeChunkEdge(int constant, @Local(ordinal = 0, argsOnly = true) long fromPos, @Local(ordinal = 1, argsOnly = true) long toPos) {
        if (cc_isCubic && CloPos.isCube(fromPos) && CloPos.isColumn(toPos)) return 0;
        return constant;
    }

    @Inject(method="checkNeighborsAfterUpdate", at=@At("HEAD"), cancellable = true)
    private void cc_onCheckNeighborsAfterUpdate(long pos, int level, boolean isDecreasing, CallbackInfo ci) {
        if (cc_isCubic) {
            ci.cancel();
            CloPos.forEachNeighbor(pos, n -> this.checkNeighbor(pos, n, level, isDecreasing));
        }
    }

    @Inject(method = "getComputedLevel", at=@At("HEAD"), cancellable = true)
    private void cc_onGetComputedLevel(long pos, long excludedSourcePos, int level, CallbackInfoReturnable<Integer> cir) {
        if (!cc_isCubic) return;
        if (CloPos.isColumn(pos)) {
            int out = level;

            int x = CloPos.extractX(pos);
            int z = CloPos.extractZ(pos);
            for (int x2 = -1; x2 <= 1; ++x2) {
                for (int z2 = -1; z2 <= 1; ++z2) {
                    long neighbor = CloPos.asLong(x + x2, z + z2);
                    if (neighbor == pos) {
                        neighbor = CloPos.INVALID_CLO_POS;
                    }

                    if (neighbor != excludedSourcePos) {
                        int k1 = this.computeLevelFromNeighbor(neighbor, pos, this.getLevel(neighbor));
                        if (out > k1) {
                            out = k1;
                        }

                        if (out == 0) {
                            cir.setReturnValue(out);
                            return;
                        }
                    }
                }
            }
            IntSet neighborCubeYSet = this.cc_existingCubesForCubeColumns.get(CloPos.setRawY(pos, 0));
            if (neighborCubeYSet != null) {
                for (Integer cubeY : neighborCubeYSet) {
                    long neighbor = CloPos.setRawY(pos, cubeY);
                    assert neighbor != pos;
                    if (neighbor != excludedSourcePos) {
                        int k1 = this.computeLevelFromNeighbor(neighbor, pos, this.getLevel(neighbor));
                        if (out > k1) {
                            out = k1;
                        }

                        if (out == 0) {
                            cir.setReturnValue(out);
                            return;
                        }
                    }
                }
            }
            cir.setReturnValue(out);
        } else {
            int out = level;

            int x = CloPos.extractRawX(pos);
            int y = CloPos.extractRawY(pos);
            int z = CloPos.extractRawZ(pos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        long neighbor = CloPos.asLong(x + dx, y + dy, z + dz);
                        if (neighbor == pos) {
                            neighbor = CloPos.INVALID_CLO_POS;
                        }

                        if (neighbor != excludedSourcePos) {
                            int k1 = this.computeLevelFromNeighbor(neighbor, pos, this.getLevel(neighbor));
                            if (out > k1) {
                                out = k1;
                            }

                            if (out == 0) {
                                cir.setReturnValue(out);
                                return;
                            }
                        }
                    }
                }
            }
            cir.setReturnValue(out);
        }
    }

    // Should be called from each onSetLevel implementation (ChunkTracker is abstract and does not implement it)
    protected void cc_onSetLevel(long pos, int level) {
        if (cc_isCubic && CloPos.isCube(pos)) {
            long key = CloPos.setRawY(pos, 0);
            IntSet cubes = cc_existingCubesForCubeColumns.get(key);
            if (level >= cc_noChunkLevel) {
                if (cubes != null) {
                    cubes.remove(CloPos.extractRawY(pos));
                    if (cubes.isEmpty()) {
                        cc_existingCubesForCubeColumns.remove(key);
                    }
                }
            } else {
                if (cubes == null) {
                    cc_existingCubesForCubeColumns.put(key, cubes = new IntOpenHashSet());
                }
                cubes.add(CloPos.extractRawY(pos));
            }
        }
    }
}
