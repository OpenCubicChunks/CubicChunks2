package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BiomeGenerationSettings.class)
public interface BiomeGenerationSettingsAccess {

    @Accessor
    List<HolderSet<PlacedFeature>> getFeatures();

    @Mutable @Accessor
    void setFeatures(List<HolderSet<PlacedFeature>> features);
}
