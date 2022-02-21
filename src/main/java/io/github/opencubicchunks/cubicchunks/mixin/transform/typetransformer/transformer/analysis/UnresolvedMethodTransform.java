package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodParameterInfo;

/**
 * A method transform which may need to be applied
 */
public record UnresolvedMethodTransform(MethodParameterInfo transform, TransformTrackingValue returnValue, TransformTrackingValue[] parameters) {
    public int check() {
        return transform.getTransformCondition().checkValidity(returnValue, parameters);
    }

    public void reject() {
        if (returnValue != null) {
            returnValue.possibleTransformChecks.remove(this);
        }
        for (TransformTrackingValue value : parameters) {
            value.possibleTransformChecks.remove(this);
        }
    }

    public void accept() {
        //Clear all possible transforms
        if (returnValue != null) {
            returnValue.possibleTransformChecks.clear();
        }
        for (TransformTrackingValue value : parameters) {
            value.possibleTransformChecks.clear();
        }

        if (returnValue != null) {
            if (transform.getReturnType() != null) {
                returnValue.getTransformTypeRef().setValue(transform.getReturnType().getTransformType());
            }
        }

        int i = 0;
        for (TransformSubtype type : transform.getParameterTypes()) {
            if (type != null) {
                parameters[i].getTransformTypeRef().setValue(type.getTransformType());
            }
            i++;
        }
    }
}
