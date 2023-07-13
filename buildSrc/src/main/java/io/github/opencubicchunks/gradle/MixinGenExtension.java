package io.github.opencubicchunks.gradle;


import static org.apache.tools.ant.util.StringUtils.removePrefix;
import static org.apache.tools.ant.util.StringUtils.removeSuffix;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.stream.JsonWriter;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

// Note: this intentionally only contains the parts that I actually use
@SuppressWarnings("unused") public class MixinGenExtension {

    private final Map<String, Action<MixinConfig>> configs = new HashMap<>();

    private String filePattern = "mixin.%s.json";
    private String defaultRefmap = "mixin.refmap.json";
    private String defaultPackagePrefix = "mixin.refmap.json";

    private String defaultCompatibilityLevel;
    private String defaultMinVersion;

    MixinGenExtension() {
    }

    public void setFilePattern(String pattern) {
        this.filePattern = pattern;
    }

    public String getFilePattern() {
        return this.filePattern;
    }

    public String getDefaultRefmap() {
        return defaultRefmap;
    }

    public void setDefaultRefmap(String defaultRefmap) {
        this.defaultRefmap = defaultRefmap;
    }

    public String getDefaultPackagePrefix() {
        return defaultPackagePrefix;
    }

    public void setDefaultPackagePrefix(String defaultPackagePrefix) {
        this.defaultPackagePrefix = defaultPackagePrefix;
    }

    public String getDefaultCompatibilityLevel() {
        return defaultCompatibilityLevel;
    }

    public void setDefaultCompatibilityLevel(String level) {
        this.defaultCompatibilityLevel = level;
    }

    public String getDefaultMinVersion() {
        return defaultMinVersion;
    }

    public void setDefaultMinVersion(String defaultMinVersion) {
        this.defaultMinVersion = defaultMinVersion;
    }

    public void config(String name, Action<MixinConfig> configure) {
        configs.put(name, configure);
    }

    public static class MixinConfig {

        private Boolean required;
        private String packageName;
        private String refmap;
        private String compatibilityLevel;
        private String minVersion;
        private String configurationPlugin;
        private Integer mixinPriority;

        private Integer injectorsDefaultRequire;
        private Boolean conformVisibility;
        private String sourceSet;

        public String getSourceSet() {
            return sourceSet;
        }

        public void setSourceSet(String sourceSet) {
            this.sourceSet = sourceSet;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getRefmap() {
            return refmap;
        }

        public void setRefmap(String refmap) {
            this.refmap = refmap;
        }

        public String getCompatibilityLevel() {
            return compatibilityLevel;
        }

        public void setCompatibilityLevel(String compatibilityLevel) {
            this.compatibilityLevel = compatibilityLevel;
        }

        public String getMinVersion() {
            return minVersion;
        }

        public void setMinVersion(String minVersion) {
            this.minVersion = minVersion;
        }

        public String getConfigurationPlugin() {
            return configurationPlugin;
        }

        public void setConfigurationPlugin(String configurationPlugin) {
            this.configurationPlugin = configurationPlugin;
        }

        public Integer getInjectorsDefaultRequire() {
            return injectorsDefaultRequire;
        }

        public void setInjectorsDefaultRequire(int injectorsDefaultRequire) {
            this.injectorsDefaultRequire = injectorsDefaultRequire;
        }

        public Boolean getConformVisibility() {
            return conformVisibility;
        }

        public void setConformVisibility(boolean conformVisibility) {
            this.conformVisibility = conformVisibility;
        }

        public Integer getMixinPriority() {
            return mixinPriority;
        }

        public void setMixinPriority(Integer mixinPriority) {
            this.mixinPriority = mixinPriority;
        }
    }

    void generateFiles(JavaPluginConvention convention) throws IOException {
        for (String name : configs.keySet()) {
            MixinConfig config = new MixinConfig();
            Action<MixinConfig> configure = configs.get(name);
            if (defaultPackagePrefix != null) {
                config.packageName = defaultPackagePrefix + "." + name;
            }
            if (defaultRefmap != null) {
                config.refmap = defaultRefmap;
            }
            if (defaultCompatibilityLevel != null) {
                config.compatibilityLevel = defaultCompatibilityLevel;
            }
            if (defaultMinVersion != null) {
                config.minVersion = defaultMinVersion;
            }
            configure.execute(config);

            SourceSet main = convention.getSourceSets().getByName(config.sourceSet == null ? "main" : config.sourceSet);
            Set<File> resourcesSet = main.getResources().getSrcDirs();
            Path resources = resourcesSet.iterator().next().getCanonicalFile().toPath();

            String fileName = String.format(filePattern, name);

            try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(resources.resolve(fileName)))) {
                writer.setIndent("    ");
                writer.beginObject();
                if (config.required != null) {
                    writer.name("required").value(config.required);
                }
                if (config.packageName != null) {
                    writer.name("package").value(config.packageName);
                }
                if (config.refmap != null) {
                    writer.name("refmap").value(config.refmap);
                }
                if (config.configurationPlugin != null) {
                    writer.name("plugin").value(config.configurationPlugin);
                }
                if (config.compatibilityLevel != null) {
                    writer.name("compatibilityLevel").value(config.compatibilityLevel);
                }
                if (config.minVersion != null) {
                    writer.name("minVersion").value(config.minVersion);
                }
                if (config.mixinPriority != null) {
                    writer.name("mixinPriority").value(config.mixinPriority);
                }
                if (config.injectorsDefaultRequire != null) {
                    writer.name("injectors").beginObject();
                    writer.name("defaultRequire").value(config.injectorsDefaultRequire);
                    writer.endObject();
                }
                if (config.conformVisibility != null) {
                    writer.name("overwrites").beginObject();
                    writer.name("conformVisibility").value(config.conformVisibility);
                    writer.endObject();
                }
                writeMixins(convention, name, config, writer);

                writer.endObject();
            }
        }

    }

    private void writeMixins(JavaPluginConvention convention, String name, MixinConfig config, JsonWriter writer) throws IOException {
        SourceSet main = convention.getSourceSets().getByName(config.sourceSet == null ? "main": config.sourceSet);
        Set<Path> classes = getMixinClasses(config, main.getAllJava());

        Set<Path> commonSet = new HashSet<>();
        Set<Path> clientSet = new HashSet<>();
        Set<Path> serverSet = new HashSet<>();

        Path prefixPath = Paths.get(config.packageName.replace('.', '/'));

        for (Path mixinClass : classes) {
            Path relative = prefixPath.relativize(mixinClass);
            if (relative.startsWith("client")) {
                clientSet.add(relative);
            } else if (relative.startsWith("server")) {
                serverSet.add(relative);
            } else if (relative.startsWith("common")) {
                commonSet.add(relative);
            }
        }
        Function<Path, String> transform = path ->
            removeSuffix(removePrefix(path.toString().replace(File.separatorChar, '.'), name + "."), ".java");

        List<String> common = commonSet.stream().map(transform).sorted(Comparator.comparing(a -> a.toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        List<String> client = clientSet.stream().map(transform).sorted(Comparator.comparing(a -> a.toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        List<String> server = serverSet.stream().map(transform).sorted(Comparator.comparing(a -> a.toLowerCase(Locale.ROOT))).collect(Collectors.toList());

        writer.name("mixins").beginArray();
        for (String path : common) {
            writer.value(path);
        }
        writer.endArray();

        writer.name("client").beginArray();
        for (String path : client) {
            writer.value(path);
        }
        writer.endArray();

        writer.name("server").beginArray();
        for (String path : server) {
            writer.value(path);
        }
        writer.endArray();
    }

    private Set<Path> getMixinClasses(MixinConfig config, SourceDirectorySet allJava) throws IOException {
        System.out.println("GetMixin Classes for " + config.packageName + " in " + allJava.getSrcDirs());
        Set<Path> srcPaths = new HashSet<>();
        for (File file : allJava.getSrcDirs()) {
            Path toPath = file.getCanonicalFile().toPath();
            System.out.println("GetMixin " + toPath);

            srcPaths.add(toPath);
        }
        Set<Path> classes = new HashSet<>();
        for (File it : allJava) {
            Path javaClass = it.getCanonicalFile().toPath();
            System.out.println("Class:  " + javaClass);
            for (Path srcPath : srcPaths) {
                if (javaClass.startsWith(srcPath)) {
                    Path relative = srcPath.relativize(javaClass);
                    if (relative.toString().replace(File.separatorChar, '.').startsWith(config.packageName)
                            && !relative.toString().endsWith("package-info.java")) {
                        classes.add(relative);
                    }
                }
            }
        }
        return classes;
    }
}