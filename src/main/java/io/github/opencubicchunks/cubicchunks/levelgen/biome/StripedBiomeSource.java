package io.github.opencubicchunks.cubicchunks.levelgen.biome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.ListCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
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
