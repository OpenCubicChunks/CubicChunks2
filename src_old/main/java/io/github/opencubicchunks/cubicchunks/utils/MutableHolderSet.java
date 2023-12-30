package io.github.opencubicchunks.cubicchunks.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;

public class MutableHolderSet<T> implements HolderSet<T> {
    private final List<Holder<T>> contents = new ArrayList<>();

    public MutableHolderSet() {

    }

    public MutableHolderSet(HolderSet<T> set) {
        addAll(set);
    }

    public void add(Holder<T> holder) {
        contents.add(holder);
    }

    public void addAll(HolderSet<T> set) {
        set.stream().forEach(this::add);
    }

    @Override
    public Stream<Holder<T>> stream() {
        return contents.stream();
    }

    @Override
    public int size() {
        return contents.size();
    }

    @Override
    public Either<TagKey<T>, List<Holder<T>>> unwrap() {
        return Either.right(contents);
    }

    @Override
    public Optional<Holder<T>> getRandomElement(Random random) {
        if (contents.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(contents.get(random.nextInt(contents.size())));
        }
    }

    @Override
    public Holder<T> get(int i) {
        return contents.get(i);
    }

    @Override
    public boolean contains(Holder<T> holder) {
        return contents.contains(holder);
    }

    @Override
    public boolean isValidInRegistry(Registry<T> registry) {
        return true;
    }

    @NotNull @Override
    public Iterator<Holder<T>> iterator() {
        return contents.iterator();
    }
}
