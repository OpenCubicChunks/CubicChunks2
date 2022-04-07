package io.github.opencubicchunks.cubicchunks.levelgen.placement;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

public class CubicLakePlacementModifier extends PlacementModifier {
    public static final Codec<CubicLakePlacementModifier> CODEC = RecordCodecBuilder.create((instance) ->
        instance.group(
            UserFunction.CODEC.fieldOf("surface_probability").forGetter((config) -> config.surfaceProbability),
            UserFunction.CODEC.fieldOf("main_probability").forGetter((config) -> config.mainProbability)
        ).apply(instance, CubicLakePlacementModifier::new));
    private static final PlacementModifierType<CubicLakePlacementModifier> TYPE;

    private final UserFunction surfaceProbability;
    private final UserFunction mainProbability;

    public CubicLakePlacementModifier(UserFunction surfaceProbability, UserFunction mainProbability) {
        this.surfaceProbability = surfaceProbability;
        this.mainProbability = mainProbability;
    }

    public UserFunction getSurfaceProbability() {
        return surfaceProbability;
    }

    public UserFunction getMainProbability() {
        return mainProbability;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext placementContext, Random random, BlockPos blockPos) {
        List<BlockPos> positions = new ArrayList<>();

        for (int i = 0; i < CubeAccess.DIAMETER_IN_SECTIONS; i++) {

            int x = blockPos.getX() + random.nextInt(16);
            int y = blockPos.getY() + random.nextInt(CubeAccess.DIAMETER_IN_BLOCKS);
            int z = blockPos.getZ() + random.nextInt(16);
            int surfaceHeight = placementContext.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);

            if (surfaceHeight < blockPos.getY()) {
                continue;
            }

            if (surfaceHeight >= blockPos.getY() + CubeAccess.DIAMETER_IN_BLOCKS) {
                float probability = this.surfaceProbability.getValue(surfaceHeight);
                if (random.nextFloat() < probability) {
                    positions.add(new BlockPos(x, surfaceHeight, z));
                }
            } else {
                float probability = this.mainProbability.getValue(surfaceHeight);
                if (random.nextFloat() < probability) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions.stream();
    }

    @Override
    public PlacementModifierType<?> type() {
        return TYPE;
    }

    static {
        TYPE = Registry.register(Registry.PLACEMENT_MODIFIERS, new ResourceLocation(CubicChunks.MODID, "cubic_lake"), () -> CODEC);
    }
}
