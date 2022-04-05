package io.github.opencubicchunks.cubicchunks.levelgen.biome;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
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
            RegistryOps.retrieveRegistry(Registry.BIOME_REGISTRY).forGetter(s -> null)
        ).apply(instance, instance.stable(StripedBiomeSource::new))
    );

    private final Registry<Biome> biomeRegistry;
    private final Holder<Biome>[] biomeArray;

    public StripedBiomeSource(Registry<Biome> registry) {
        super(Stream.of());
        this.biomeRegistry = registry;
        this.biomeArray = getAllOverWorldBiomes(registry);
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public BiomeSource withSeed(long l) {
        return new StripedBiomeSource(this.biomeRegistry);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return biomeArray[Math.floorMod(Math.floorDiv(x, 160), biomeArray.length)];
    }

    private static Holder<Biome>[] getAllOverWorldBiomes(Registry<Biome> registry) {
        TagKey<Biome> inOverworld = TagKey.create(Registry.BIOME_REGISTRY, new ResourceLocation("c", "in_overworld"));

        //Check that "c:in_overworld" exists/is valid
        Optional<HolderSet.Named<Biome>> tagOptional = registry.getTag(inOverworld);
        if (tagOptional.isPresent()) {
            Holder<Biome> plains = registry.getOrCreateHolder(Biomes.PLAINS);
            HolderSet.Named<Biome> tag = tagOptional.get();
            if (tag.contains(plains)) {
                return tag.stream().toArray(Holder[]::new);
            }
        }

        //Fallback to combinations of some biome tags
        final TagKey<Biome>[] tags = new TagKey[]{
            BiomeTags.HAS_STRONGHOLD, //This tag has nearly all biomes except oceans, beaches, stone shore, and rivers
            BiomeTags.IS_OCEAN,
            BiomeTags.IS_BEACH,
            BiomeTags.IS_RIVER,
            BiomeTags.HAS_MINESHAFT //This tag contains the rest of what we want
        };

        Set<Holder<Biome>> biomes = new HashSet<>();

        for (TagKey<Biome> tag : tags) {
            HolderSet.Named<Biome> tagSet = registry.getOrCreateTag(tag);
            for (Holder<Biome> biome : tagSet) {
                biomes.add(biome);
            }
        }

        return biomes.toArray(new Holder[0]);
    }
}
