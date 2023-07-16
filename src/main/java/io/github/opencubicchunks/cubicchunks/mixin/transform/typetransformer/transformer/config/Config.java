package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformTrackingInterpreter;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformTrackingValue;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.AncestorHashMap;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.Analyzer;

public class Config {
    private final TypeInfo typeInfo;
    private final Map<String, TransformType> types;
    private final AncestorHashMap<MethodID, List<MethodParameterInfo>> methodParameterInfo;
    private final Map<Type, ClassTransformInfo> classes;
    private final List<Type> typesWithSuffixedTransforms;

    private final Set<Type> regularTypes = new HashSet<>();
    private final Set<Type> consumerTypes = new HashSet<>();
    private final Set<Type> predicateTypes = new HashSet<>();

    private TransformTrackingInterpreter interpreter;
    private Analyzer<TransformTrackingValue> analyzer;

    public Config(TypeInfo typeInfo, Map<String, TransformType> transformTypeMap, AncestorHashMap<MethodID, List<MethodParameterInfo>> parameterInfo,
                  Map<Type, ClassTransformInfo> classes,
                  List<Type> typesWithSuffixedTransforms) {
        this.types = transformTypeMap;
        this.methodParameterInfo = parameterInfo;
        this.typeInfo = typeInfo;
        this.classes = classes;
        this.typesWithSuffixedTransforms = typesWithSuffixedTransforms;

        for (TransformType type : this.types.values()) {
            regularTypes.add(type.getFrom());
            consumerTypes.add(type.getOriginalConsumerType());
            predicateTypes.add(type.getOriginalPredicateType());
        }
    }

    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    public Map<String, TransformType> getTypes() {
        return types;
    }

    public Map<MethodID, List<MethodParameterInfo>> getMethodParameterInfo() {
        return methodParameterInfo;
    }

    public Set<Type> getRegularTypes() {
        return regularTypes;
    }

    public Set<Type> getConsumerTypes() {
        return consumerTypes;
    }

    public Set<Type> getPredicateTypes() {
        return predicateTypes;
    }

    public List<Type> getTypesWithSuffixedTransforms() {
        return typesWithSuffixedTransforms;
    }

    public TransformTrackingInterpreter getInterpreter() {
        if (interpreter == null) {
            interpreter = new TransformTrackingInterpreter(Opcodes.ASM9, this);
        }

        return interpreter;
    }

    public Analyzer<TransformTrackingValue> getAnalyzer() {
        if (analyzer == null) {
            makeAnalyzer();
        }

        return analyzer;
    }

    private void makeAnalyzer() {
        analyzer = new Analyzer<>(getInterpreter());
    }

    public Map<Type, ClassTransformInfo> getClasses() {
        return classes;
    }
}
