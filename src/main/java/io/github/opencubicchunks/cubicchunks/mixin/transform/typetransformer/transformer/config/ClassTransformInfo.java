package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.util.Map;

import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;

public class ClassTransformInfo {
    private final Map<MethodID, Map<Integer, TransformType>> typeHints;

    public ClassTransformInfo(Map<MethodID, Map<Integer, TransformType>> typeHints) {
        this.typeHints = typeHints;
    }

    public Map<MethodID, Map<Integer, TransformType>> getTypeHints() {
        return typeHints;
    }
}
