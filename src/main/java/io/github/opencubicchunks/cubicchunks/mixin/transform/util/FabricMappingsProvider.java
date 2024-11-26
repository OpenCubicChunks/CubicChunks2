package io.github.opencubicchunks.cubicchunks.mixin.transform.util;

import io.github.opencubicchunks.dasm.MappingsProvider;
import net.fabricmc.loader.api.MappingResolver;

public class FabricMappingsProvider implements MappingsProvider {
    private final MappingsProvider fallback;
    private final MappingResolver fabricResolver;
    private final String namespace;

    public FabricMappingsProvider(MappingResolver fabricResolver, String namespace) {
        this(MappingsProvider.IDENTITY, fabricResolver, namespace);
    }

    public FabricMappingsProvider(MappingsProvider fallback, MappingResolver fabricResolver, String namespace) {
        this.fallback = fallback;
        this.fabricResolver = fabricResolver;
        this.namespace = namespace;
    }

    @Override
    public String mapFieldName(String owner, String fieldName, String descriptor) {
        boolean slash = owner.contains("/");
        if (slash) {
            owner = owner.replace('/', '.');
        }

        String res = fabricResolver.mapFieldName(namespace, owner, fieldName, descriptor);
        String mapped = res == null ? fallback.mapFieldName(owner, fieldName, descriptor) : res;
        return slash ? mapped.replace('.', '/') : mapped;
    }

    @Override
    public String mapMethodName(String owner, String methodName, String descriptor) {
        boolean slash = owner.contains("/");
        if (slash) {
            owner = owner.replace('/', '.');
        }

        String res = fabricResolver.mapMethodName(namespace, owner, methodName, descriptor);
        String mapped = res == null ? fallback.mapMethodName(owner, methodName, descriptor) : res;
        return slash ? mapped.replace('.', '/') : mapped;
    }

    @Override
    public String mapClassName(String className) {
        boolean slash = className.contains("/");
        if (slash) {
            className = className.replace('/', '.');
        }

        String res = fabricResolver.mapClassName(namespace, className);
        String mapped = res == null ? fallback.mapClassName(className) : res;
        return slash ? mapped.replace('.', '/') : mapped;
    }
}
