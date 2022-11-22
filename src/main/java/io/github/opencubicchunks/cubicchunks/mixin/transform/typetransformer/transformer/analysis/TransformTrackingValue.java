package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.Config;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.TransformType;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.ASMUtil;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.AncestorHashMap;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.FieldID;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.Value;

/**
 * A value that infers its transform type and tracks the instructions that created it, consumed it and
 * the fields it comes from
 */
public class TransformTrackingValue implements Value {
    final Set<UnresolvedMethodTransform> possibleTransformChecks = new HashSet<>(); //Used to track possible transform checks

    private final @Nullable Type type;
    private final AncestorHashMap<FieldID, TransformTrackingValue> pseudoValues;
    private final TransformSubtype transform;
    private final Set<TransformTrackingValue> valuesWithSameType = new HashSet<>();
    private final Config config;

    public TransformTrackingValue(@Nullable Type type, AncestorHashMap<FieldID, TransformTrackingValue> fieldPseudoValues, Config config) {
        this.type = type;
        this.pseudoValues = fieldPseudoValues;
        this.transform = TransformSubtype.createDefault(type);
        this.config = config;

        this.transform.getTransformTypePtr().addTrackingValue(this);
        this.transform.setSubType(TransformSubtype.getSubType(type, config));
    }

    public TransformTrackingValue(@Nullable Type type, TransformSubtype transform, AncestorHashMap<FieldID, TransformTrackingValue> fieldPseudoValues, Config config) {
        this.type = type;
        this.transform = transform;
        this.pseudoValues = fieldPseudoValues;
        this.config = config;

        this.transform.getTransformTypePtr().addTrackingValue(this);
        this.transform.setSubType(TransformSubtype.getSubType(type, config));
    }

    public TransformTrackingValue merge(TransformTrackingValue other) {
        if (transform.getTransformType() != null && other.transform.getTransformType() != null && transform.getTransformType() != other.transform.getTransformType()) {
            throw new RuntimeException("Merging incompatible values. (Different transform types had already been assigned)");
        }

        setSameType(this, other);

        TransformTrackingValue newValue = new TransformTrackingValue(
            type,
            transform,
            pseudoValues,
            config
        );

        return newValue;
    }

    public @Nullable TransformType getTransformType() {
        return transform.getTransformType();
    }

    public void setTransformType(TransformType transformType) {
        if (this.transform.getTransformType() != null && transformType != this.transform.getTransformType()) {
            throw new RuntimeException("Transform subType already set");
        }

        if (this.transform.getTransformType() == transformType) {
            return;
        }

        Type rawType = this.transform.getRawType(transformType);
        int dimension = ASMUtil.getDimensions(this.type) - ASMUtil.getDimensions(rawType);
        this.transform.setArrayDimensionality(dimension);

        this.transform.getTransformTypePtr().setValue(transformType);
    }

    public void updateType(@Nullable TransformType oldType, TransformType newType) {
        //Set appropriate array dimensions
        Set<TransformTrackingValue> copy = new HashSet<>(valuesWithSameType);
        valuesWithSameType.clear(); //To prevent infinite recursion

        for (TransformTrackingValue value : copy) {
            value.setTransformType(newType);
        }

        Type rawType = this.transform.getRawType(newType);
        int dimension = ASMUtil.getDimensions(this.type) - ASMUtil.getDimensions(rawType);
        this.transform.setArrayDimensionality(dimension);

        for (UnresolvedMethodTransform check : possibleTransformChecks) {
            int validity = check.check();
            if (validity == -1) {
                check.reject();
            } else if (validity == 1) {
                check.accept();
            }
        }
    }

    @Override
    public int getSize() {
        return type == Type.LONG_TYPE || type == Type.DOUBLE_TYPE ? 2 : 1;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransformTrackingValue that = (TransformTrackingValue) o;
        return Objects.equals(type, that.type) && Objects.equals(transform, that.transform);
    }

    @Override public int hashCode() {
        return Objects.hash(type, transform);
    }

    public static <T> Set<T> union(Set<T> first, Set<T> second) {
        Set<T> union = new HashSet<>(first);
        union.addAll(second);
        return union;
    }

    public @Nullable Type getType() {
        return type;
    }

    public static void setSameType(TransformTrackingValue first, TransformTrackingValue second) {
        if (first.type == null || second.type == null) {
            //System.err.println("WARNING: Attempted to set same subType on null subType");
            return;
        }

        if (first.getTransformType() == null && second.getTransformType() == null) {
            first.valuesWithSameType.add(second);
            second.valuesWithSameType.add(first);
            return;
        }

        if (first.getTransformType() != null && second.getTransformType() != null && first.getTransformType() != second.getTransformType()) {
            throw new RuntimeException("Merging incompatible values. (Different types had already been assigned)");
        }

        if (first.getTransformType() != null) {
            second.getTransformTypeRef().setValue(first.getTransformType());
        } else if (second.getTransformType() != null) {
            first.getTransformTypeRef().setValue(second.getTransformType());
        }
    }

    public TransformTypePtr getTransformTypeRef() {
        return transform.getTransformTypePtr();
    }

    @Override
    public String toString() {
        if (type == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(type.toString());

        if (transform.getTransformType() != null) {
            sb.append(" (").append(transform).append(")");
        }

        return sb.toString();
    }

    public TransformSubtype getTransform() {
        return transform;
    }

    public int getTransformedSize() {
        if (transform.getTransformType() == null) {
            return getSize();
        } else {
            return transform.getTransformedSize();
        }
    }

    public List<Type> transformedTypes() {
        return this.transform.resultingTypes();
    }

    public boolean isTransformed() {
        return this.transform.getTransformType() != null;
    }
}
