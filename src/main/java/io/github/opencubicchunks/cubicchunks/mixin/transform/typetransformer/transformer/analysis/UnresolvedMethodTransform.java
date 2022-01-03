package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodParameterInfo;

public record UnresolvedMethodTransform(MethodParameterInfo transform, TransformTrackingValue returnValue, TransformTrackingValue[] parameters){
    public int check(){
        return transform.getTransformCondition().checkValidity(returnValue, parameters);
    }

    public void reject(){
        if(returnValue != null) {
            returnValue.possibleTransformChecks.remove(this);
        }
        for(TransformTrackingValue value: parameters){
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

        if(returnValue != null) {
            if (transform.getReturnType() != null) {
                returnValue.getTransformTypeRef().setValue(transform.getReturnType().getTransformType());

                /*returnValue.getTransform().setArrayDimensionality(transform.getReturnType().getArrayDimensionality());
                returnValue.getTransform().setSubType(transform.getReturnType().getSubtype());*/
            }
        }

        int i = 0;
        for (TransformSubtype type : transform.getParameterTypes()) {
            if (type != null) {
                //parameters[i].setTransformType(type);
                parameters[i].getTransformTypeRef().setValue(type.getTransformType());

                /*parameters[i].getTransform().setArrayDimensionality(type.getArrayDimensionality());
                parameters[i].getTransform().setSubType(type.getSubtype());*/
            }
            i++;
        }
    }
}
