package io.github.opencubicchunks.cubicchunks.mixin.core.server.chunk;

import java.util.Map;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.ColumnCubeGetter;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LevelCube;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// TODO: Maybe Resolve redirect conflict with fabric-lifecycle-events-v1.mixins.json:client .WorldChunkMixin->@Redirect::onRemoveBlockEntity(Fabric API). We implement their events
@Environment(EnvType.SERVER)
@Mixin(value = LevelChunk.class, priority = 0) // Priority 0 to always ensure our redirects are on top. Should also prevent fabric api crashes that have occur(ed) here. See removeTileEntity
public abstract class MixinLevelChunk {

    @Shadow @Final private Map<BlockPos, BlockEntity> blockEntities;

    @Shadow @Final private Map<BlockPos, CompoundTag> pendingBlockEntities;

    @Shadow public abstract Level getLevel();

    // TODO: handle it better, no redirects on all map access
    @SuppressWarnings({ "rawtypes", "UnresolvedMixinReference" })
    @Redirect(method = "*",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object getTileEntity(Map map, Object key) {
        if (map == this.blockEntities) {
            if (!((CubicLevelHeightAccessor) this).isCubic()) {
                return map.get(key);
            }
            LevelCube cube = (LevelCube) ((ColumnCubeGetter) this).getCube(Coords.blockToSection(((BlockPos) key).getY()));
            return cube.getTileEntityMap().get(key);
        } else if (map == this.pendingBlockEntities) {
            if (!((CubicLevelHeightAccessor) this).isCubic()) {
                return map.get(key);
            }
            LevelCube cube = (LevelCube) ((ColumnCubeGetter) this).getCube(Coords.blockToSection(((BlockPos) key).getY()));
            return cube.getPendingBlockEntities().get(key);
        }
        return map.get(key);
    }

    @SuppressWarnings({ "rawtypes", "UnresolvedMixinReference" }) @Nullable
    @Redirect(
        method = "*",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object removeTileEntity(Map map,
                                    Object key) {
        // to respect our priority over theirs.

        if (map == this.blockEntities) {
            if (!((CubicLevelHeightAccessor) this).isCubic()) {
                @Nullable
                Object removed = map.remove(key);

                if (this.getLevel() instanceof ServerLevel) {
                    ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload((BlockEntity) removed, (ServerLevel) this.getLevel());
                }
                return removed;
            }
            LevelCube cube = (LevelCube) ((ColumnCubeGetter) this).getCube(Coords.blockToSection(((BlockPos) key).getY()));

            @Nullable
            BlockEntity removed = cube.getTileEntityMap().remove(key);

            if (this.getLevel() instanceof ServerLevel) {
                ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(removed, (ServerLevel) this.getLevel());
            }
            return removed;
        } else if (map == this.pendingBlockEntities) {
            if (!((CubicLevelHeightAccessor) this).isCubic()) {
                return map.remove(key);
            }
            LevelCube cube = (LevelCube) ((ColumnCubeGetter) this).getCube(Coords.blockToSection(((BlockPos) key).getY()));
            return cube.getPendingBlockEntities().remove(key);
        }
        return map.remove(key);
    }

    @SuppressWarnings({ "rawtypes", "unchecked", "UnresolvedMixinReference" }) @Nullable
    @Redirect(method = "*",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object putTileEntity(Map map, Object key, Object value) {
        if (map == this.blockEntities) {
            if (this.getLevel() instanceof ServerLevel) {
                ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.invoker().onLoad((BlockEntity) value, (ServerLevel) this.getLevel());
            }

            if (!((CubicLevelHeightAccessor) this).isCubic()) {
                return map.put(key, value);
            }
            LevelCube cube = (LevelCube) ((ColumnCubeGetter) this).getCube(Coords.blockToSection(((BlockPos) key).getY()));
            return cube.getTileEntityMap().put((BlockPos) key, (BlockEntity) value);
        } else if (map == this.pendingBlockEntities) {
            if (!((CubicLevelHeightAccessor) this).isCubic()) {
                return map.put(key, value);
            }
            LevelCube cube = (LevelCube) ((ColumnCubeGetter) this).getCube(Coords.blockToSection(((BlockPos) key).getY()));
            return cube.getPendingBlockEntities().put((BlockPos) key, (CompoundTag) value);
        }
        return map.put(key, value);
    }

}
