package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common;

import java.util.function.Function;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.levelgen.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.levelgen.biome.StripedBiomeSource;
import io.github.opencubicchunks.cubicchunks.levelgen.chunk.CubeGenerator;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.ProtoCube;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class MixinChunkGenerator implements CubeGenerator {

    @Mutable @Shadow @Final protected BiomeSource biomeSource;

    // TODO: move this to debug mixins
    @Inject(
        method = "<init>(Lnet/minecraft/world/level/biome/BiomeSource;Ljava/util/function/Function;)V",
        at = @At("RETURN")
    )
    private void switchBiomeSource(BiomeSource biomeSource, Function function, CallbackInfo ci) {
        if (true) {//(System.getProperty("cubicchunks.debug.biomes", "false").equalsIgnoreCase("true")) {
            this.biomeSource = new StripedBiomeSource(this.biomeSource.possibleBiomes());
        }
    }

    @Inject(
        method = "createStructures",
        at = @At("HEAD"),
        cancellable = true
    )
    private void createCubicStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunkAccess,
                                       StructureTemplateManager structureTemplateManager, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) chunkAccess).generates2DChunks()) {
            return;
        }
        if (!(chunkAccess instanceof CubeAccess cube)) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "createReferences", at = @At("HEAD"), cancellable = true)
    public void createReferences(WorldGenLevel worldGenLevel, StructureManager featureManager, ChunkAccess chunkAccess, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) chunkAccess).generates2DChunks()) {
            return;
        }
        if (!(chunkAccess instanceof CubeAccess cube)) {
            return;
        }
        ci.cancel();
    }

    @Override
    public void decorate(CubeWorldGenRegion region, StructureManager structureManager, ProtoCube cube) {
        for (int columnX = 0; columnX < CubicConstants.DIAMETER_IN_SECTIONS; columnX++) {
            for (int columnZ = 0; columnZ < CubicConstants.DIAMETER_IN_SECTIONS; columnZ++) {
                cube.moveColumns(columnX, columnZ);
            }
        }
    }
}
