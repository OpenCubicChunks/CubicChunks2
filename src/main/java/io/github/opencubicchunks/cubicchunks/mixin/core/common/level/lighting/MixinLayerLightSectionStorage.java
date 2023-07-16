package io.github.opencubicchunks.cubicchunks.mixin.core.common.level.lighting;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLayerLightSectionStorage;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.Direction;
import net.minecraft.server.level.SectionTracker;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerLightSectionStorage.class)
public abstract class MixinLayerLightSectionStorage<M extends DataLayerStorageMap<M>> extends SectionTracker implements CubicLayerLightSectionStorage {
    @Shadow @Final private static Direction[] DIRECTIONS;

    @Shadow @Final protected Long2ObjectMap<DataLayer> queuedSections;

    @Shadow @Final protected M updatingSectionData;

    @Shadow @Final protected LongSet changedSections;

    @Shadow protected volatile boolean hasToRemove;

    @Shadow @Final private LongSet toRemove;

    @Shadow @Final private LightChunkGetter chunkSource;

    @Shadow @Final private LongSet untrustedSections;

    private final LongSet cubesToRetain = new LongOpenHashSet();

    protected MixinLayerLightSectionStorage(int i, int j, int k) {
        super(i, j, k);
    }

    @Shadow protected abstract void clearQueuedSectionBlocks(LightEngine<?, ?> engine, long sectionPosIn);

    @Shadow protected abstract void onNodeRemoved(long pos);

    @Shadow protected abstract boolean storingLightForSection(long sectionPosIn);

    @Shadow protected abstract boolean hasInconsistencies();

    @Shadow protected abstract void checkEdgesForSection(LightEngine<M, ?> layerLightEngine, long l);

    @Override
    public void retainCubeData(long cubeSectionPos, boolean retain) {
        if (retain) {
            this.cubesToRetain.add(cubeSectionPos);
        } else {
            this.cubesToRetain.remove(cubeSectionPos);
        }
    }

    /**
     * @author NotStirred
     * @reason entire method was chunk based
     */
    @Inject(method = "markNewInconsistencies", at = @At("HEAD"), cancellable = true)
    protected void markNewInconsistenciesForCube(LightEngine<M, ?> engine, boolean updateSkyLight, boolean updateBlockLight, CallbackInfo ci) {
        if (this.chunkSource.getLevel() == null || !((CubicLevelHeightAccessor) this.chunkSource.getLevel()).isCubic()) {
            return;
        }
        ci.cancel();

        if (this.hasInconsistencies() || !this.queuedSections.isEmpty()) {
            for (long noLightPos : this.toRemove) {
                this.clearQueuedSectionBlocks(engine, noLightPos);
                DataLayer nibblearray = this.queuedSections.remove(noLightPos);
                DataLayer nibblearray1 = this.updatingSectionData.removeLayer(noLightPos);
                if (this.cubesToRetain.contains(CubePos.sectionToCubeSectionLong(noLightPos))) {
                    if (nibblearray != null) {
                        this.queuedSections.put(noLightPos, nibblearray);
                    } else if (nibblearray1 != null) {
                        this.queuedSections.put(noLightPos, nibblearray1);
                    }
                }
            }

            this.updatingSectionData.clearCache();

            for (long section : this.toRemove) {
                //TODO: implement this for CC
                this.onNodeRemoved(section);
            }

            this.toRemove.clear();
            this.hasToRemove = false;

            this.queuedSections.forEach((entryPos, dataLayer) -> {
                if (this.storingLightForSection(entryPos)) {
                    if (this.updatingSectionData.getLayer(entryPos) != dataLayer) {
                        this.clearQueuedSectionBlocks(engine, entryPos);
                        this.updatingSectionData.setLayer(entryPos, dataLayer);
                        this.changedSections.add(entryPos);
                    }
                }
            });

            this.updatingSectionData.clearCache();
            if (!updateBlockLight) {
                for (long newArray : this.queuedSections.keySet()) {
                    checkEdgesForSection(engine, newArray);
                }
            } else {
                for (long newArray : this.untrustedSections) {
                    checkEdgesForSection(engine, newArray);
                }
            }

            ObjectIterator<Long2ObjectMap.Entry<DataLayer>> objectiterator = this.queuedSections.long2ObjectEntrySet().iterator();

            while (objectiterator.hasNext()) {
                Long2ObjectMap.Entry<DataLayer> entry1 = objectiterator.next();
                long k2 = entry1.getLongKey();
                if (this.storingLightForSection(k2)) {
                    objectiterator.remove();
                }
            }
        }
    }
}