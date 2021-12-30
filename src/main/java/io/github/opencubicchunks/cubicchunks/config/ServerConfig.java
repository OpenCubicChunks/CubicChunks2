package io.github.opencubicchunks.cubicchunks.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.LevelStorageAccessAccess;
import net.minecraft.world.level.storage.LevelStorageSource;

// TODO actually store config data instead of just using this as a marker
public class ServerConfig {
    private static final String FILE_PATH = "serverconfig/cubicchunks.toml";
    private File configPath;

    private ServerConfig(File configPath) {
        this.configPath = configPath;
    }

    private static File getConfigPath(Path worldFolder) {
        return new File(worldFolder.toFile(), FILE_PATH);
    }

    private static ServerConfig create(Path worldFolder) throws IOException {
        File configPath = getConfigPath(worldFolder);
        configPath.getParentFile().mkdirs();
        configPath.createNewFile();

        Files.write(configPath.toPath(), "# This file will store config data eventually, until then it's just used as a marker\n".getBytes());

        return new ServerConfig(configPath);
    }

    @Nullable public static ServerConfig getConfig(Path worldFolder) {
        File configPath = getConfigPath(worldFolder);
        if (configPath.exists()) {
            return new ServerConfig(configPath);
        }
        return null;
    }

    public static void generateConfigIfNecessary(LevelStorageSource.LevelStorageAccess levelStorageAccess) {
        if (CubicChunks.config().common.generateNewWorldsAsCC) {
            CubicChunks.LOGGER.info("New worlds are configured to generate as CC; creating CC config file");
            var rootFolderPath = ((LevelStorageAccessAccess) levelStorageAccess).getLevelPath();
            try {
                ServerConfig.create(rootFolderPath);
            } catch (IOException e) {
                // FIXME proper error handling
                throw new Error(e);
            }
        } else {
            CubicChunks.LOGGER.info("New worlds are configured to NOT generate as CC; no Cubic Chunks data will be created");
        }
    }
}
