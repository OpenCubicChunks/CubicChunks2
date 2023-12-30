package io.github.opencubicchunks.cubicchunks.world.server;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.config.ServerConfig;

public interface CubicMinecraftServer {
    @Nullable ServerConfig getServerConfig();
}
