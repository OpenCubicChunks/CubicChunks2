package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.TransformType;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.ASMUtil;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.AncestorHashMap;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.FieldID;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.Value;

public class TransformTrackingValue implements Value {
    private final Type subType;
    private final Set<AbstractInsnNode> source;
    private final Set<Integer> localVars; //Used uniquely for parameters
    private final Set<AbstractInsnNode> consumers = new HashSet<>(); //Any instruction which "consumes" this value
    private final Set<FieldSource> fieldSources = new HashSet<>(); //Used for field detecting which field this value comes from. For now only tracks instance fields (i.e not static)
    private final AncestorHashMap<FieldID, TransformTrackingValue> pseudoValues;

    private final Set<TransformTrackingValue> mergedFrom = new HashSet<>();
    private final Set<TransformTrackingValue> mergedTo = new HashSet<>();

    private final TransformSubtype transform;

    private final Set<TransformTrackingValue> valuesWithSameType = new HashSet<>();
    final Set<UnresolvedMethodTransform> possibleTransformChecks = new HashSet<>(); //Used to track possible transform checks

    public TransformTrackingValue(Type subType, AncestorHashMap<FieldID, TransformTrackingValue> fieldPseudoValues){
        this.subType = subType;
        this.source = new HashSet<>();
        this.localVars = new HashSet<>();
        this.pseudoValues = fieldPseudoValues;
        this.transform = TransformSubtype.createDefault();

        this.transform.getTransformTypePtr().addTrackingValue(this);
        this.transform.setSubType(TransformSubtype.getSubType(subType));
    }

    public TransformTrackingValue(Type subType, int localVar, AncestorHashMap<FieldID, TransformTrackingValue> fieldPseudoValues){
        this.subType = subType;
        this.source = new HashSet<>();
        this.localVars = new HashSet<>();
        localVars.add(localVar);
        this.pseudoValues = fieldPseudoValues;
        this.transform = TransformSubtype.createDefault();

        this.transform.getTransformTypePtr().addTrackingValue(this);
        this.transform.setSubType(TransformSubtype.getSubType(subType));
    }

    public TransformTrackingValue(Type subType, AbstractInsnNode source, AncestorHashMap<FieldID, TransformTrackingValue> fieldPseudoValues){
        this(subType, fieldPseudoValues);
        this.source.add(source);
    }

    public TransformTrackingValue(Type subType, AbstractInsnNode insn, int var, TransformSubtype transform, AncestorHashMap<FieldID, TransformTrackingValue> fieldPseudoValues) {
        this.subType = subType;
        this.source = new HashSet<>();
        this.source.add(insn);
        this.localVars = new HashSet<>();
        this.localVars.add(var);
        this.transform = transform;
        this.pseudoValues = fieldPseudoValues;

        this.transform.getTransformTypePtr().addTrackingValue(this);
        this.transform.setSubType(TransformSubtype.getSubType(subType));
    }

    public TransformTrackingValue(Type subType, Set<AbstractInsnNode> source, Set<Integer> localVars, TransformSubtype transform, AncestorHashMap<FieldID, TransformTrackingValue> fieldPseudoValues){
        this.subType = subType;
        this.source = source;
        this.localVars = localVars;
        this.transform = transform;
        this.pseudoValues = fieldPseudoValues;

        this.transform.getTransformTypePtr().addTrackingValue(this);
        this.transform.setSubType(TransformSubtype.getSubType(subType));
    }

    public TransformTrackingValue merge(TransformTrackingValue other){
        if(transform.getTransformType() != null && other.transform.getTransformType() != null && transform.getTransformType() != other.transform.getTransformType()){
            throw new RuntimeException("Merging incompatible values. (Different transform types had already been assigned)");
        }

        setSameType(this, other);

        TransformTrackingValue value = new TransformTrackingValue(
                subType,
                union(source, other.source),
                union(localVars, other.localVars),
                transform,
                pseudoValues
        );

        value.mergedFrom.add(this);
        value.mergedFrom.add(other);

        this.mergedTo.add(value);
        other.mergedTo.add(value);

        return value;
    }

    public TransformType getTransformType(){
        return transform.getTransformType();
    }

    public void setTransformType(TransformType transformType){
        if(this.transform.getTransformType() != null && transformType != this.transform.getTransformType()){
            throw new RuntimeException("Transform subType already set");
        }

        if(this.transform.getTransformType() == transformType){
            return;
        }

        Type rawType = this.transform.getRawType(transformType);
        int dimension = ASMUtil.getDimensions(this.subType) - ASMUtil.getDimensions(rawType);
        this.transform.setArrayDimensionality(dimension);

        this.transform.getTransformTypePtr().setValue(transformType);
    }

    public void updateType(TransformType oldType, TransformType newType) {
        //Set appropriate array dimensions
        Set<TransformTrackingValue> copy = new HashSet<>(valuesWithSameType);
        valuesWithSameType.clear(); //To prevent infinite recursion

        for(TransformTrackingValue value : copy){
            value.setTransformType(newType);
        }

        Type rawType = this.transform.getRawType(newType);
        int dimension = ASMUtil.getDimensions(this.subType) - ASMUtil.getDimensions(rawType);
        this.transform.setArrayDimensionality(dimension);

        for(UnresolvedMethodTransform check : possibleTransformChecks){
            int validity = check.check();
            if(validity == -1){
                check.reject();
            }else if(validity == 1){
                check.accept();
            }
        }

        if(fieldSources.size() > 0){
            for(FieldSource source : fieldSources){
                //System.out.println("Field " + source.root() + " is now " + newType);
                FieldID id = new FieldID(Type.getObjectType(source.classNode()), source.fieldName(), Type.getType(source.fieldDesc()));
                if(pseudoValues.containsKey(id)){
                    TransformTrackingValue value = pseudoValues.get(id);
                    //value.transform.setArrayDimensionality(source.arrayDepth());
                    value.setTransformType(newType);
                }
            }
        }
    }

    public void addFieldSource(FieldSource fieldSource){
        fieldSources.add(fieldSource);
    }

    public void addFieldSources(Set<FieldSource> fieldSources){
        this.fieldSources.addAll(fieldSources);
    }

    public Set<FieldSource> getFieldSources() {
        return fieldSources;
    }

    public void addPossibleTransformCheck(UnresolvedMethodTransform transformCheck){
        possibleTransformChecks.add(transformCheck);
    }

    @Override
    public int getSize() {
        return subType == Type.LONG_TYPE || subType == Type.DOUBLE_TYPE ? 2 : 1;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransformTrackingValue that = (TransformTrackingValue) o;
        return Objects.equals(subType, that.subType) && Objects.equals(source, that.source) && Objects
                .equals(consumers, that.consumers);
    }

    @Override public int hashCode() {
        return Objects.hash(subType, source, localVars, consumers, transform);
    }

    public static <T> Set<T> union(Set<T> first, Set<T> second){
        Set<T> union = new HashSet<>(first);
        union.addAll(second);
        return union;
    }

    public Type getType() {
        return subType;
    }

    public Set<AbstractInsnNode> getSource() {
        return source;
    }

    public Set<Integer> getLocalVars() {
        return localVars;
    }

    public Set<AbstractInsnNode> getConsumers() {
        return consumers;
    }

    public void consumeBy(AbstractInsnNode consumer) {
        consumers.add(consumer);
    }

    public Set<TransformTrackingValue> getAllRelatedValues(){
        Set<TransformTrackingValue> relatedValues = new HashSet<>();

        Set<TransformTrackingValue> newValues = new HashSet<>(mergedFrom);
        newValues.addAll(mergedTo);

        while(!newValues.isEmpty()){
            Set<TransformTrackingValue> nextValues = new HashSet<>();
            for(TransformTrackingValue value : newValues){
                relatedValues.add(value);
                nextValues.addAll(value.mergedFrom);
                nextValues.addAll(value.mergedTo);
            }
            newValues = nextValues;
        }

        return relatedValues;
    }

    public static void setSameType(TransformTrackingValue first, TransformTrackingValue second){
        if(first.subType == null || second.subType == null){
            //System.err.println("WARNING: Attempted to set same subType on null subType");
            return;
        }

        if(first.getTransformType() == null && second.getTransformType() == null){
            first.valuesWithSameType.add(second);
            second.valuesWithSameType.add(first);
            return;
        }

        if(first.getTransformType() != null && second.getTransformType() != null && first.getTransformType() != second.getTransformType()){
            throw new RuntimeException("Merging incompatible values. (Different types had already been assigned)");
        }

        if(first.getTransformType() != null){
            second.getTransformTypeRef().setValue(first.getTransformType());
        }else if(second.getTransformType() != null){
            first.getTransformTypeRef().setValue(second.getTransformType());
        }
    }

    public TransformTypePtr getTransformTypeRef() {
        return transform.getTransformTypePtr();
    }

    @Override
    public String toString() {
        if(subType == null){
            return "null";
        }
        StringBuilder sb = new StringBuilder(subType.toString());

        if(transform.getTransformType() != null){
            sb.append(" (").append(transform).append(")");
        }

        if(fieldSources.size() > 0){
            sb.append(" (from ");
            int i = 0;
            for(FieldSource source : fieldSources){
                sb.append(source.toString());
                if(i < fieldSources.size() - 1){
                    sb.append(", ");
                }
                i++;
            }
            sb.append(")");
        }

        return sb.toString();
    }

    public TransformSubtype getTransform() {
        return transform;
    }

    public int getTransformedSize() {
        if(transform.getTransformType() == null){
            return getSize();
        }else{
            return transform.getTransformedSize();
        }
    }

    public List<Type> transformedTypes(){
        return this.transform.transformedTypes(this.subType);
    }
}
