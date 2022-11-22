package io.github.opencubicchunks.cubicchunks.mixin.transform;

import static org.objectweb.asm.Type.ARRAY;
import static org.objectweb.asm.Type.OBJECT;
import static org.objectweb.asm.Type.getObjectType;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.TypeTransformer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.Config;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.ConfigLoader;
import io.github.opencubicchunks.cubicchunks.utils.TestMappingUtils;
import net.fabricmc.loader.api.MappingResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;

public class MainTransformer {
    public static final Config TRANSFORM_CONFIG;
    private static final Logger LOGGER = LogManager.getLogger();
    private static final boolean IS_DEV = TestMappingUtils.isDev();

    public static void transformDynamicGraphMinFixedPoint(ClassNode targetClass) {
        TypeTransformer transformer = new TypeTransformer(TRANSFORM_CONFIG, targetClass, true);

        transformer.analyzeAllMethods();

        transformer.makeConstructor("(III)V");

        transformer.transformAllMethods();
    }

    public static void transformLayerLightEngine(ClassNode targetClass) {
        TypeTransformer transformer = new TypeTransformer(TRANSFORM_CONFIG, targetClass, true);

        transformer.analyzeAllMethods();
        transformer.transformAllMethods();

        transformer.callMagicSuperConstructor();
    }

    public static void transformNoiseBasedAquifer(ClassNode target) {
        defaultTransform(target);
    }

    public static void transformSectionPos(ClassNode targetClass) {
        TypeTransformer transformer = new TypeTransformer(TRANSFORM_CONFIG, targetClass, true);

        ClassMethod blockToSection = remapMethod(
            new ClassMethod(
                getObjectType("net/minecraft/class_4076"),
                new Method("method_18691", "(J)J"),
                getObjectType("net/minecraft/class_4076")
            )
        );

        transformer.analyzeMethod(blockToSection.method.getName(), blockToSection.method.getDescriptor());
        transformer.cleanUpAnalysis();

        transformer.transformMethod(blockToSection.method.getName(), blockToSection.method.getDescriptor());
        transformer.cleanUpTransform();
    }

    public static void defaultTransform(ClassNode targetClass) {
        TypeTransformer transformer = new TypeTransformer(TRANSFORM_CONFIG, targetClass, true);

        transformer.analyzeAllMethods();
        transformer.transformAllMethods();
    }

    public static void transformLayerLightSectionStorage(ClassNode targetClass) {
        defaultTransform(targetClass);
    }

    @NotNull private static ClassMethod remapMethod(ClassMethod clMethod) {
        MappingResolver mappingResolver = TestMappingUtils.getMappingResolver();
        Type[] params = Type.getArgumentTypes(clMethod.method.getDescriptor());
        Type returnType = Type.getReturnType(clMethod.method.getDescriptor());

        Type mappedType = remapType(clMethod.owner);
        String mappedName = mappingResolver.mapMethodName("intermediary",
            clMethod.mappingOwner.getClassName(), clMethod.method.getName(), clMethod.method.getDescriptor());
        if (clMethod.method.getName().contains("method") && IS_DEV && mappedName.equals(clMethod.method.getName())) {
            throw new Error("Fail! Mapping method " + clMethod.method.getName() + " failed in dev!");
        }
        Type[] mappedParams = new Type[params.length];
        for (int i = 0; i < params.length; i++) {
            mappedParams[i] = remapDescType(params[i]);
        }
        Type mappedReturnType = remapDescType(returnType);
        return new ClassMethod(mappedType, new Method(mappedName, mappedReturnType, mappedParams));
    }

    private static Type remapDescType(Type t) {
        if (t.getSort() == ARRAY) {
            int dimCount = t.getDimensions();
            StringBuilder prefix = new StringBuilder(dimCount);
            for (int i = 0; i < dimCount; i++) {
                prefix.append('[');
            }
            return Type.getType(prefix + remapDescType(t.getElementType()).getDescriptor());
        }
        if (t.getSort() != OBJECT) {
            return t;
        }
        MappingResolver mappingResolver = TestMappingUtils.getMappingResolver();
        String unmapped = t.getClassName();
        if (unmapped.endsWith(";")) {
            unmapped = unmapped.substring(1, unmapped.length() - 1);
        }
        String mapped = mappingResolver.mapClassName("intermediary", unmapped);
        String mappedDesc = 'L' + mapped.replace('.', '/') + ';';
        if (unmapped.contains("class") && IS_DEV && mapped.equals(unmapped)) {
            throw new Error("Fail! Mapping class " + unmapped + " failed in dev!");
        }
        return Type.getType(mappedDesc);
    }

    private static Type remapType(Type t) {
        MappingResolver mappingResolver = TestMappingUtils.getMappingResolver();
        String unmapped = t.getClassName();
        String mapped = mappingResolver.mapClassName("intermediary", unmapped);
        if (unmapped.contains("class") && IS_DEV && mapped.equals(unmapped)) {
            throw new Error("Fail! Mapping class " + unmapped + " failed in dev!");
        }
        return Type.getObjectType(mapped.replace('.', '/'));
    }

    static {
        //Load config
        try {
            InputStream is = MainTransformer.class.getResourceAsStream("/type-transform.json");
            TRANSFORM_CONFIG = ConfigLoader.loadConfig(is);
            is.close();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't load transform config", e);
        }
    }

    public static final class ClassMethod {
        public final Type owner;
        public final Method method;
        public final Type mappingOwner;

        ClassMethod(Type owner, Method method) {
            this.owner = owner;
            this.method = method;
            this.mappingOwner = owner;
        }

        // mapping owner because mappings owner may not be the same as in the call site
        ClassMethod(Type owner, Method method, Type mappingOwner) {
            this.owner = owner;
            this.method = method;
            this.mappingOwner = mappingOwner;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClassMethod that = (ClassMethod) o;
            return owner.equals(that.owner) && method.equals(that.method) && mappingOwner.equals(that.mappingOwner);
        }

        @Override public int hashCode() {
            return Objects.hash(owner, method, mappingOwner);
        }

        @Override public String toString() {
            return "ClassMethod{" +
                "owner=" + owner +
                ", method=" + method +
                ", mappingOwner=" + mappingOwner +
                '}';
        }
    }
}