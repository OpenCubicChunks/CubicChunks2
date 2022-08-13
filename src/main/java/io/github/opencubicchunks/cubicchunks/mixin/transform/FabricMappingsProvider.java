package io.github.opencubicchunks.cubicchunks.mixin.transform;

import io.github.opencubicchunks.dasm.MappingsProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

public class FabricMappingsProvider implements MappingsProvider {
    private final MappingResolver mappingResolver;

    public FabricMappingsProvider() {
         this.mappingResolver = FabricLoader.getInstance().getMappingResolver();
    }

    @Override public String mapFieldName(String namespace, String owner, String fieldName, String descriptor) {
        return this.mappingResolver.mapFieldName(namespace, owner, fieldName, descriptor);
    }

    @Override public String mapMethodName(String namespace, String owner, String methodName, String descriptor) {
        return this.mappingResolver.mapMethodName(namespace, owner, methodName, descriptor);
    }

    @Override public String mapClassName(String namespace, String className) {
        return this.mappingResolver.mapClassName(namespace, className);
    }
}
