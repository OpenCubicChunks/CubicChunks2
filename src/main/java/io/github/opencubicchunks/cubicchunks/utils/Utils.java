package io.github.opencubicchunks.cubicchunks.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.function.Supplier;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.launch.common.MappingConfiguration;

public class Utils {
    private static MappingResolver mappingResolver;

    @SuppressWarnings("unchecked")
    public static <I, O> O unsafeCast(I obj) {
        return (O) obj;
    }

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
        Path dir = FabricLoader.getInstance().getGameDir();

        if (dir == null) {
            Path assumed = Path.of("run").toAbsolutePath();
            System.err.println("Fabric is not running properly. Returning assumed game directory: " + assumed);
            dir = assumed;
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
                Class<?> mappingResolverClass = Class.forName("net.fabricmc.loader.FabricMappingResolver");
                Constructor<?> constructor = mappingResolverClass.getDeclaredConstructor(Supplier.class, String.class);
                constructor.setAccessible(true);
                return (MappingResolver) constructor.newInstance((Supplier) new MappingConfiguration()::getMappings, "named");
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e1) {
                throw new RuntimeException(e1);
            }
        }
    }
}