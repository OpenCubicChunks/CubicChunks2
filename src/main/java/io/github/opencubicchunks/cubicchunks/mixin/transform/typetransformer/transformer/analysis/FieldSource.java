package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis;

public record FieldSource(String classNode, String fieldName, String fieldDesc, int arrayDepth){
    public FieldSource deeper() {
        return new FieldSource(classNode, fieldName, fieldDesc, arrayDepth + 1);
    }

    @Override
    public String toString() {
        return classNode + "." + fieldName + (arrayDepth > 0 ? "[" + arrayDepth + "]" : "");
    }

    public FieldSource root() {
        return new FieldSource(classNode, fieldName, fieldDesc, 0);
    }
}
