package io.github.opencubicchunks.cubicchunks.config;

import java.io.File;
import java.util.Map;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;

public abstract class BaseConfig {
    protected static void write(File configPath, CommentedConfig config) {
        var writer = new TomlWriter();
        writer.write(config, configPath, WritingMode.REPLACE);
    }

    protected static void updateConfig(Config target, Config newValues) {
        for (Map.Entry<String, Object> entry : target.valueMap().entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            var newValue = newValues.get(key);
            if (value instanceof Config) {
                if (newValue instanceof Config) {
                    // Recursively update sub-configs
                    updateConfig(((Config) value), ((Config) newValue));
                }
            } else {
                // Note that this doesn't handle values having incorrect types - TODO should it? incorrect types currently cause a crash
                if (newValue != null) {
                    entry.setValue(newValue);
                }
            }
        }
    }

    protected static void read(File configPath, CommentedConfig config) {
        var parser = new TomlParser();
        // We parse the config separately and then merge it, so that new keys get added and unnecessary keys are discarded
        // ParsingMode.MERGE doesn't support nested configs
        var parsedConfig = CommentedConfig.inMemory();
        parser.parse(configPath, parsedConfig, ParsingMode.REPLACE, FileNotFoundAction.THROW_ERROR);
        updateConfig(config, parsedConfig);
    }
}
