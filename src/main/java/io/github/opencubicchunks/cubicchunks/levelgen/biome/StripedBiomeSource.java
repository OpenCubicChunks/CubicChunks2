package io.github.opencubicchunks.cubicchunks.levelgen.biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.ListCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

public class StripedBiomeSource extends BiomeSource {
    public static final Codec<StripedBiomeSource> CODEC = RecordCodecBuilder.create(
        (instance) -> instance.group(
            new ListCodec<>(Biome.CODEC).stable().fieldOf("biomes").forGetter(s -> s.biomes)
        ).apply(instance, instance.stable(StripedBiomeSource::new))
    );

    private final List<Holder<Biome>> biomes;

    public StripedBiomeSource(Collection<Holder<Biome>> biomes) {
        super(Stream.of());
        this.biomes = new ArrayList<>(biomes);
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public BiomeSource withSeed(long l) {
        return new StripedBiomeSource(this.biomes);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return biomes.get(Math.floorMod(Math.floorDiv(x, 160), biomes.size()));
    }
}
