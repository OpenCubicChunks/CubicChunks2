package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis;

/**
 * Stores information about values that should be bound to a method. But the method
 * has not been analyzed yet
 * @param offset The method parameter index of the first value that should be bound
 * @param parameters The values that should be bound to the method parameters
 */
public record FutureMethodBinding(int offset, TransformTrackingValue... parameters) {
}
