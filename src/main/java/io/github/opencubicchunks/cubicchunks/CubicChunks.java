package io.github.opencubicchunks.cubicchunks;

import java.lang.reflect.InvocationTargetException;

import io.github.opencubicchunks.cc_core.config.EarlyConfig;
import io.github.opencubicchunks.cubicchunks.config.CommonConfig;
import io.github.opencubicchunks.cubicchunks.levelgen.biome.StripedBiomeSource;
import io.github.opencubicchunks.cubicchunks.levelgen.feature.CubicFeatures;
import io.github.opencubicchunks.cubicchunks.levelgen.placement.CubicFeatureDecorators;
import io.github.opencubicchunks.cubicchunks.network.PacketDispatcher;
import io.github.opencubicchunks.cubicchunks.server.level.CubeMap;
import net.fabricmc.api.ModInitializer;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkMap;

/**
 * Requires Mixin BootStrap in order to use in forge.
 */
// The value here should match an entry in the META-INF/mods.toml file
public class CubicChunks extends io.github.opencubicchunks.cc_core.CubicChunks implements ModInitializer {
    protected static final CommonConfig CONFIG = CommonConfig.getConfig();

    public CubicChunks() {
        if (!(CubeMap.class.isAssignableFrom(ChunkMap.class))) {
            throw new IllegalStateException("Mixin not applied!");
        }
        EarlyConfig.getDiameterInSections();

        if (System.getProperty("cubicchunks.debug", "false").equalsIgnoreCase("true")) {
            try {
                Class.forName("io.github.opencubicchunks.cubicchunks.debug.DebugVisualization").getMethod("enable").invoke(null);
                SharedConstants.IS_RUNNING_IN_IDE = true;
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
                LOGGER.catching(e);
            }
        }

        //Custom CC Features
        CubicFeatureDecorators.init();
        CubicFeatures.init();
    }

    @Override
    public void onInitialize() {
        PacketDispatcher.register();

        Registry.register(Registry.BIOME_SOURCE, new ResourceLocation(MODID, "stripes"), StripedBiomeSource.CODEC);
//        Registry.register(Registry.CHUNK_GENERATOR, new ResourceLocation(MODID, "generator"), CCNoiseBasedChunkGenerator.CODEC);
    }

    public static CommonConfig config() {
        return CONFIG;
    }
}