package io.github.opencubicchunks.cubicchunks.mixin.transform;

import io.github.opencubicchunks.dasm.MappingsProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

public class FabricMappingsProvider implements MappingsProvider {
    private final MappingResolver mappingResolver;
    private final String namespace;

    public FabricMappingsProvider(String namespace) {
         this.mappingResolver = FabricLoader.getInstance().getMappingResolver();
         this.namespace = namespace;
    }

    /**
     * @param owner Expects format {@code the/package/name/OwnerClass}
     * @param fieldName Expects a valid field name
     * @param descriptor Expects format {@code Lthe/package/name/FieldType;}
     */
    @Override public String mapFieldName(String owner, String fieldName, String descriptor) {
        return this.mappingResolver.mapFieldName(this.namespace, owner, fieldName, descriptor);
    }

    /**
     * @param owner Expects format {@code the/package/name/OwnerClass}
     * @param methodName Expects a valid method name
     * @param descriptor Expects format {@code (Lthe/package/name/ParamClassA;Lthe/package/name/ParamClassB;)Lthe/package/name/ReturnType;}
     */
    @Override public String mapMethodName(String owner, String methodName, String descriptor) {
        return this.mappingResolver.mapMethodName(this.namespace, owner, methodName, descriptor);
    }

    /**
     * @param className Expects a name in format {@code the/package/name/Class}
     */
    @Override public String mapClassName(String className) {
        return this.mappingResolver.mapClassName(this.namespace, className);
    }
}
