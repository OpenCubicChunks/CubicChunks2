package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis;

import java.util.HashSet;
import java.util.Set;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.TransformType;
import org.jetbrains.annotations.Nullable;

public class TransformTypePtr {
    private @Nullable TransformType value;
    private final Set<TransformTrackingValue> trackingValues = new HashSet<>();

    public TransformTypePtr(@Nullable TransformType value) {
        this.value = value;
    }

    public void addTrackingValue(TransformTrackingValue trackingValue) {
        trackingValues.add(trackingValue);
    }

    private void updateType(@Nullable TransformType oldType, TransformType newType) {
        if (oldType == newType) {
            return;
        }

        for (TransformTrackingValue trackingValue : trackingValues) {
            trackingValue.updateType(oldType, newType);
        }
    }

    public void setValue(TransformType value) {
        TransformType oldType = this.value;
        this.value = value;

        if (oldType != value) {
            updateType(oldType, value);
        }
    }

    public @Nullable TransformType getValue() {
        return value;
    }
}
