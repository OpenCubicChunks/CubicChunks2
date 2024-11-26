package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis;

import java.util.Set;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.TransformType;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.Nullable;

public class TransformTypeRef {
    private @Nullable TransformType value;
    private final Set<TransformTrackingValue> trackingValues = new ObjectOpenCustomHashSet<>(new Hash.Strategy<>() {
        @Override public int hashCode(TransformTrackingValue transformTrackingValue) {
            return System.identityHashCode(transformTrackingValue);
        }

        @Override public boolean equals(TransformTrackingValue transformTrackingValue, TransformTrackingValue k1) {
            return transformTrackingValue == k1;
        }
    });

    public TransformTypeRef(@Nullable TransformType value) {
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
            trackingValue.updateType(newType);
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
