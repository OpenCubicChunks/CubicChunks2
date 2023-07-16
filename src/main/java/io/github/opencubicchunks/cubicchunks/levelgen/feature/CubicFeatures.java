package io.github.opencubicchunks.cubicchunks.levelgen.feature;

import java.util.List;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.levelgen.placement.CubicLakePlacementModifier;
import io.github.opencubicchunks.cubicchunks.levelgen.placement.UserFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

public class CubicFeatures {
    //Water lake was removed in 1.18, it is now made with aquifers

    public static final Holder<ConfiguredFeature<?, ?>> CC_LAVA_LAKE_FEATURE = createConfiguredFeature(
        "lake_lava",
        new ConfiguredFeature<>(
            Feature.LAKE,
            new LakeFeature.Configuration(BlockStateProvider.simple(Blocks.LAVA), BlockStateProvider.simple(Blocks.STONE))
        )
    );

    public static final Holder<ConfiguredFeature<?, ?>> CC_WATER_LAKE_FEATURE = createConfiguredFeature(
        "lake_water",
        new ConfiguredFeature<>(
            Feature.LAKE,
            new LakeFeature.Configuration(BlockStateProvider.simple(Blocks.WATER), BlockStateProvider.simple(Blocks.STONE))
        )
    );

    //TODO: These probabilities are from before 1.18
    public static final Holder<PlacedFeature> CC_LAVA_LAKE = createPlacedFeature(
        "lake_lava",
        CC_LAVA_LAKE_FEATURE,
        new CubicLakePlacementModifier(
            UserFunction.builder()
                // same as vanilla for y0-127, probabilities near y=256 are very low, so don't use them
                .point(0, 4 / 263f)
                .point(7, 4 / 263f)
                .point(8, 247 / 16306f)
                .point(62, 193 / 16306f)
                .point(63, 48 / 40765f)
                .point(127, 32 / 40765f)
                .point(128, 32 / 40765f)
                .build(),
            UserFunction.builder()
                // sample vanilla probabilities at y=0, 31, 63, 95, 127
                .point(-1, 19921 / 326120f)
                .point(0, 19921 / 326120f)
                .point(31, 1332 / 40765f)
                .point(63, 579 / 81530f)
                .point(95, 161 / 32612f)
                .point(127, 129 / 40765f)
                .point(128, 129 / 40765f)
                .build()
        )
    );

    public static final Holder<PlacedFeature> CC_WATER_LAKE = createPlacedFeature(
        "lake_water",
        CC_WATER_LAKE_FEATURE,
        new CubicLakePlacementModifier(
            UserFunction.builder()
                // same as vanilla
                .point(0, 1 / 64f)
                .build(),
            UserFunction.builder()
                // same as vanilla for y=0-128, probabilities get too low at 2xx heights so dont use them
                .point(-1, 0.25f)
                .point(0, 0.25f)
                .point(128, 0.125f)
                .point(129, 0.125f)
                .build()
        )
    );

    public static final Holder<ConfiguredFeature<?, ?>> LAVA_LEAK_FIX_FEATURE = createConfiguredFeature("lava_leak_fix",
        new ConfiguredFeature<>(
            CubicFeature.LAVA_LEAK_FIX,
            new NoneFeatureConfiguration()
        )
    );

    public static final Holder<PlacedFeature> LAVA_LEAK_FIX = createPlacedFeature(
        "lava_leak_fix",
        LAVA_LEAK_FIX_FEATURE
    );

    public static void init() {
    }

    public static <FC extends FeatureConfiguration, F extends Feature<FC>, CF extends ConfiguredFeature<FC, F>> Holder<ConfiguredFeature<?, ?>> createConfiguredFeature(String id,
                                                                                                                                                                   CF configuredFeature) {
        ResourceLocation resourceLocation = new ResourceLocation(CubicChunks.MODID, id);
        if (BuiltInRegistries.CONFIGURED_FEATURE.keySet().contains(resourceLocation)) {
            throw new IllegalStateException("Configured Feature ID: \"" + resourceLocation + "\" already exists in the Configured Features registry!");
        }

        return BuiltInRegistries.register(BuiltInRegistries.CONFIGURED_FEATURE, resourceLocation, configuredFeature);
    }

    public static Holder<PlacedFeature> createPlacedFeature(String id, Holder<ConfiguredFeature<?, ?>> feature, PlacementModifier... modifiers) {
        return createPlacedFeature(id, feature, List.of(modifiers));
    }

    private static Holder<PlacedFeature> createPlacedFeature(String id, Holder<ConfiguredFeature<?, ?>> feature, List<PlacementModifier> modifiers) {
        ResourceLocation loc = new ResourceLocation(CubicChunks.MODID, id);

        if (BuiltInRegistries.PLACED_FEATURE.containsKey(loc)) {
            throw new IllegalStateException("Placed Feature ID: \"" + loc + "\" already exists in the Placed Features registry!");
        }

        return BuiltInRegistries.register(
            BuiltInRegistries.PLACED_FEATURE,
            loc,
            new PlacedFeature(Holder.hackyErase(feature), List.copyOf(modifiers))
        );
    }
}
