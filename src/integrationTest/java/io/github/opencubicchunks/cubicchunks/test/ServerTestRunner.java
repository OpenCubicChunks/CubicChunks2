package io.github.opencubicchunks.cubicchunks.test;

import java.util.Optional;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface ServerTestRunner {
    @Nullable Pair<ServerLevel, Optional<BlockPos>> firstErrorLocation();
}
