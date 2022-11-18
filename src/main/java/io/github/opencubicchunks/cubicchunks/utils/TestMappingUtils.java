package io.github.opencubicchunks.cubicchunks.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.function.Supplier;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.launch.MappingConfiguration;

public class TestMappingUtils {
    private static MappingResolver mappingResolver;

    /**
     * This method is useful for when running unit tests
     *
     * @return A mapping resolver. If fabric is not running/properly initialized it will create a mapping resolver. ("intermediary" -> "named")
     */
    public static MappingResolver getMappingResolver() {
        if (mappingResolver != null) {
            return mappingResolver;
        }

        mappingResolver = makeResolver();
        return mappingResolver;
    }

    /**
     * This method is useful for when running unit tests
     *
     * @return Whether fabric is running in a development environment. If fabric is not running/properly initialized it will return true.
     */
    public static boolean isDev() {
        try {
            return FabricLoader.getInstance().isDevelopmentEnvironment();
        } catch (NullPointerException e) {
            return true;
        }
    }

    public static Path getGameDir() {
        Path dir = null;
        Path def = Path.of("run").toAbsolutePath();
        try {
            dir = FabricLoader.getInstance().getGameDir();
        } catch (IllegalStateException e) {
            System.err.println("Fabric not initialized, using assumed game dir: " + def);
        }

        if (dir == null) {
            dir = def;
        }

        return dir;
    }

    private static MappingResolver makeResolver() {
        try {
            return FabricLoader.getInstance().getMappingResolver();
        } catch (NullPointerException e) {
            System.err.println("Fabric is not running properly. Creating a mapping resolver.");
            //FabricMappingResolver's constructor is package-private so we call it with reflection
            try {
                MappingConfiguration config = new MappingConfiguration();
                Class<?> mappingResolverClass = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl");
                Constructor<?> constructor = mappingResolverClass.getDeclaredConstructor(Supplier.class, String.class);
                constructor.setAccessible(true);
                return (MappingResolver) constructor.newInstance((Supplier) config::getMappings, "named");
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e1) {
                throw new RuntimeException(e1);
            }
        }
    }
}