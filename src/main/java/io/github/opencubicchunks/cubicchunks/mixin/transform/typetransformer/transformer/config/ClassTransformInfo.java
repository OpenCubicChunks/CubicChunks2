package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.util.List;
import java.util.Map;

import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;

public class ClassTransformInfo {
    private final Map<MethodID, List<TransformType>> typeHints;
    private final Map<String, ConstructorReplacer> constructorReplacers;
    private final boolean inPlace;

    public ClassTransformInfo(Map<MethodID, List<TransformType>> typeHints, Map<String, ConstructorReplacer> constructorReplacers, boolean inPlace) {
        this.typeHints = typeHints;
        this.constructorReplacers = constructorReplacers;
        this.inPlace = inPlace;
    }

    public Map<MethodID, List<TransformType>> getTypeHints() {
        return typeHints;
    }

    public Map<String, ConstructorReplacer> getConstructorReplacers() {
        return constructorReplacers;
    }

    public boolean isInPlace() {
        return inPlace;
    }
}
