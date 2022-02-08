package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis;

/**
 * Stores information about a value's relation to a field.
 * @param classNode The name of the class holding the field
 * @param fieldName The name of the field
 * @param fieldDesc The descriptor of the field
 * @param arrayDepth The depth of the value within an array.
 * For example, if the field 'foo' is of type int[][][], then, in the following code, the arrayDepth
 * of the FieldSource of the value of 'bar' would be 2.
 * <pre>
 *     int[] bar = this.foo[1][2];
 * </pre>
 */
public record FieldSource(String classNode, String fieldName, String fieldDesc, int arrayDepth) {
    /**
     * Generates a FieldSource with arrayDepth incremented by 1.
     */
    public FieldSource deeper() {
        return new FieldSource(classNode, fieldName, fieldDesc, arrayDepth + 1);
    }

    @Override
    public String toString() {
        return classNode + "." + fieldName + (arrayDepth > 0 ? "[]" : "");
    }
}
