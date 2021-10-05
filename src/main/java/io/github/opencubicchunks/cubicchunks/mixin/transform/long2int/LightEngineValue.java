package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Value;

public class LightEngineValue implements Value {
    private final Type type;
    private final Set<AbstractInsnNode> source;
    private final Set<Integer> localVars;
    private final Set<AbstractInsnNode> consumers = new HashSet<>();
    private BooleanReference isAPackedLong = new BooleanReference(false);

    public LightEngineValue(Type type){
        this.type = type;
        this.source = new HashSet<>();
        this.localVars = new HashSet<>();
    }

    public LightEngineValue(Type type, int localVar){
        this.type = type;
        this.source = new HashSet<>();
        this.localVars = new HashSet<>();
        localVars.add(localVar);
    }

    public LightEngineValue(Type type, AbstractInsnNode source){
        this(type);
        this.source.add(source);
    }

    public LightEngineValue(Type type, AbstractInsnNode source, int localVar){
        this.type = type;
        this.source = new HashSet<>();
        this.localVars = new HashSet<>();

        this.source.add(source);
        this.localVars.add(localVar);
    }

    public LightEngineValue(Type type, AbstractInsnNode source, Set<Integer> localVars){
        this.type = type;
        this.source = new HashSet<>();
        this.localVars = localVars;

        this.source.add(source);
    }

    public LightEngineValue(Type type, Set<AbstractInsnNode> source, Set<Integer> localVars) {
        this.type = type;
        this.source = source;
        this.localVars = localVars;
    }

    public LightEngineValue(Type type, Set<AbstractInsnNode> source, Set<Integer> localVars, BooleanReference isAPackedLong) {
        this.type = type;
        this.source = source;
        this.localVars = localVars;
        this.isAPackedLong = isAPackedLong;
    }

    public LightEngineValue(Type type, AbstractInsnNode source, int localVar, BooleanReference isAPackedLong){
        this(type);
        this.isAPackedLong = isAPackedLong;
        this.source.add(source);
        this.localVars.add(localVar);
    }

    public LightEngineValue(Type type, AbstractInsnNode insn, BooleanReference packedLongRef) {
        this(type);

        this.source.add(insn);
        this.isAPackedLong = packedLongRef;
    }

    public void setPackedLong(){
        this.isAPackedLong.setValue(true);
    }

    public void consumeBy(AbstractInsnNode consumer){
        this.consumers.add(consumer);
    }

    @Override
    public int getSize() {
        return type == Type.LONG_TYPE || type == Type.DOUBLE_TYPE ? 2 : 1;
    }

    public Type getType() {
        return type;
    }

    public Set<AbstractInsnNode> getSource() {
        return source;
    }

    public Set<Integer> getLocalVars() {
        return localVars;
    }

    public LightEngineValue merge(LightEngineValue other){
        if(other.isAPackedLong()){
            this.setPackedLong();
        }

        if(this.isAPackedLong()){
            other.setPackedLong();
        }
        
        return new LightEngineValue(this.type, union(this.source, other.source), union(this.localVars, other.localVars), isAPackedLong);
    }

    public static <T> Set<T> union(Set<T> first, Set<T> second){
        Set<T> union = new HashSet<>(first);
        union.addAll(second);
        return union;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LightEngineValue that = (LightEngineValue) o;
        return Objects.equals(type, that.type) && Objects.equals(source, that.source) && Objects
            .equals(consumers, that.consumers);
    }

    @Override public int hashCode() {
        return Objects.hash(type, source, localVars, consumers, isAPackedLong);
    }

    public Set<AbstractInsnNode> getConsumers() {
        return consumers;
    }

    public boolean isAPackedLong() {
        return isAPackedLong.getValue();
    }

    BooleanReference getPackedLongRef(){
        return isAPackedLong;
    }
}
