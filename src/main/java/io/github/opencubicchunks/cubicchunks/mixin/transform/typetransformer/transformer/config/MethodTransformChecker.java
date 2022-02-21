package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformSubtype;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformTrackingValue;
import org.jetbrains.annotations.Nullable;

public class MethodTransformChecker {
    private final MethodParameterInfo target;
    private final @Nullable Minimum[] minimums;

    public MethodTransformChecker(MethodParameterInfo target, @Nullable Minimum[] minimums) {
        this.target = target;
        this.minimums = minimums;
    }

    /**
     * Checks if the passed in values could be of a transformed method
     *
     * @param returnValue The current return value
     * @param parameters The current parameters
     *
     * @return -1 if they are incompatible, 0 if they are compatible, 1 if they should be transformed
     */
    public int checkValidity(@Nullable TransformTrackingValue returnValue, TransformTrackingValue... parameters) {
        //First check if it is still possible
        if (returnValue != null) {
            if (!isApplicable(returnValue.getTransform(), target.getReturnType())) {
                return -1;
            }
        }

        //Check if the parameters are compatible
        for (int i = 0; i < parameters.length; i++) {
            if (!isApplicable(parameters[i].getTransform(), target.getParameterTypes()[i])) {
                return -1;
            }
        }

        if (minimums != null) {
            //Check if any minimums are met
            for (Minimum minimum : minimums) {
                if (minimum.isMet(returnValue, parameters)) {
                    return 1;
                }
            }
            return 0;
        }

        //If no minimums are given, we assume that it should be transformed
        return 1;
    }

    private static boolean isApplicable(TransformSubtype current, @Nullable TransformSubtype target) {
        if (target == null) {
            return true;
        }

        if (current.getTransformType() == null) {
            return true;
        }

        //Current is not null
        if (target.getTransformType() == null) {
            return false;
        }

        //Current is not null and target is not null
        return current.equals(target);
    }

    public static record Minimum(TransformSubtype returnType, TransformSubtype... parameterTypes) {
        public boolean isMet(TransformTrackingValue returnValue, TransformTrackingValue[] parameters) {
            if (returnType.getTransformType() != null) {
                if (!returnValue.getTransform().equals(returnType)) {
                    return false;
                }
            }

            for (int i = 0; i < parameterTypes.length; i++) {
                if (parameterTypes[i].getTransformType() != null) {
                    if (!parameters[i].getTransform().equals(parameterTypes[i])) {
                        return false;
                    }
                }
            }

            return true;
        }
    }
}
