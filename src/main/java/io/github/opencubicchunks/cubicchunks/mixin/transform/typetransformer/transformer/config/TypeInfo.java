package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

public class TypeInfo {
    private final Map<Type, Node> lookup = new HashMap<>();

    public TypeInfo(JsonArray array, Function<Type, Type> mapper) {
        Map<Type, JsonObject> types = new HashMap<>();

        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            Type type = Type.getObjectType(object.get("name").getAsString());
            types.put(type, object);
        }

        for (Map.Entry<Type, JsonObject> entry : types.entrySet()) {
            this.load(entry.getKey(), entry.getValue(), types, mapper);
        }
    }

    private Node load(Type type, JsonObject data, Map<Type, JsonObject> loadInfo, Function<Type, Type> mapper) {
        Type mappedType = mapper.apply(type);
        if (this.lookup.containsKey(mappedType)) {
            return this.lookup.get(mappedType);
        }

        Node superClass = null;
        if (data.has("superclass")) {
            String superName = data.get("superclass").getAsString();
            Type superType = Type.getObjectType(superName);
            superClass = this.load(superType, loadInfo.get(superType), loadInfo, mapper);
        }

        Set<Node> interfaces = new HashSet<>();
        for (JsonElement element : data.getAsJsonArray("interfaces")) {
            String interfaceName = element.getAsString();
            Type interfaceType = Type.getObjectType(interfaceName);
            interfaces.add(this.load(interfaceType, loadInfo.get(interfaceType), loadInfo, mapper));
        }

        boolean isItf = data.get("is_interface").getAsBoolean();

        Node node = new Node(mappedType, interfaces, superClass, isItf);

        if (superClass != null) {
            superClass.getChildren().add(node);
        }

        for (Node itf : interfaces) {
            itf.getChildren().add(node);
        }

        this.lookup.put(mappedType, node);
        return node;
    }

    public Iterable<Type> ancestry(Type subType) {
        if (!this.lookup.containsKey(subType)) {
            return List.of(subType);
        }

        //Breadth first traversal
        List<Type> ancestry = new ArrayList<>();
        Set<Type> visited = new HashSet<>();

        Queue<Node> queue = new ArrayDeque<>();
        queue.add(this.lookup.get(subType));

        while (!queue.isEmpty()) {
            Node node = queue.remove();
            if (visited.contains(node.getValue())) {
                continue;
            }
            visited.add(node.getValue());
            ancestry.add(node.getValue());

            queue.addAll(node.getParents());
        }

        return ancestry;
    }

    public Collection<Node> nodes() {
        return lookup.values();
    }

    public @Nullable Node getNode(Type owner) {
        return lookup.get(owner);
    }

    public boolean recognisesInterface(Type potentialOwner) {
        Node node = this.lookup.get(potentialOwner);

        return node != null && node.isInterface();
    }

    public Set<Type> getKnownInterfaces() {
        return this.lookup.values().stream().filter(Node::isInterface).map(Node::getValue).collect(Collectors.toSet());
    }

    public static class Node {
        private final Type value;
        private final Set<Node> children = new HashSet<>();
        private final Set<Node> interfaces;
        private final @Nullable Node superclass;
        private final boolean isInterface;

        public Node(Type value, Set<Node> interfaces, @Nullable Node superclass, boolean itf) {
            this.value = value;
            this.interfaces = interfaces;
            this.superclass = superclass;
            this.isInterface = itf;
        }

        public Type getValue() {
            return value;
        }

        public Set<Node> getChildren() {
            return children;
        }

        public @Nullable Node getSuperclass() {
            return superclass;
        }

        public boolean isInterface() {
            return isInterface;
        }

        public Set<Type> getInterfaces() {
            return interfaces.stream().map(Node::getValue).collect(Collectors.toSet());
        }

        public Collection<Node> getParents() {
            List<Node> parents = new ArrayList<>();

            if (this.superclass != null) {
                parents.add(this.superclass);
            }

            parents.addAll(this.interfaces);

            return parents;
        }

        public boolean isDirectDescendantOf(Node potentialParent) {
            return potentialParent.getChildren().contains(this);
        }
    }
}
