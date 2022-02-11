package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

public class HierarchyTree {
    private Node root;
    private Map<Type, Node> lookup = new HashMap<>();
    private final Set<Type> knownInterfaces = new HashSet<>();

    public void addNode(Type value, Type parent) {
        Node node;
        if (parent == null) {
            node = new Node(value, 0);
            if (root != null) {
                throw new IllegalStateException("Root has already been assigned");
            }
            root = node;
        } else {
            Node parentNode = lookup.get(parent);
            node = new Node(value, parentNode.depth + 1);
            if (parentNode == null) {
                throw new IllegalStateException("Parent node not found");
            }
            parentNode.children.add(node);
            node.parent = parentNode;
        }
        lookup.put(value, node);
    }

    public Iterable<Type> ancestry(Type subType) {
        return new AncestorIterable(lookup.get(subType));
    }

    public void print(PrintStream out) {
        this.print(out, root, 0);
    }

    private void print(PrintStream out, Node node, int depth) {
        for (int i = 0; i < depth; i++) {
            out.print("  ");
        }
        out.println(node.value);
        for (Node child : node.children) {
            print(out, child, depth + 1);
        }
    }

    public Collection<Node> nodes() {
        return lookup.values();
    }

    public @Nullable Node getNode(Type owner) {
        return lookup.get(owner);
    }

    public void addInterface(Type itf, Type subType) {
        Node node = lookup.get(subType);
        if (node == null) {
            throw new IllegalStateException("Node not found");
        }
        node.interfaces.add(itf);

        this.knownInterfaces.add(itf);
    }

    public void addInterface(Type type) {
        knownInterfaces.add(type);
    }

    public void add(Class<?> clazz) {
        while (true) {
            Type subType = Type.getType(clazz);
            if (lookup.containsKey(subType)) {
                break;
            }

            Class<?> parent = clazz.getSuperclass();
            assert parent != null;

            addNode(subType, Type.getType(parent));
            clazz = parent;
        }
    }

    public boolean recognisesInterface(Type potentionalOwner) {
        return knownInterfaces.contains(potentionalOwner);
    }

    public Set<Type> getKnownInterfaces() {
        return knownInterfaces;
    }

    public static class Node {
        private final Type value;
        private final Set<Node> children = new HashSet<>();
        private final List<Type> interfaces = new ArrayList<>(4);
        private @Nullable Node parent = null;
        private final int depth;

        public Node(Type value, int depth) {
            this.value = value;
            this.depth = depth;
        }

        public Type getValue() {
            return value;
        }

        public Set<Node> getChildren() {
            return children;
        }

        public @Nullable Node getParent() {
            return parent;
        }

        public int getDepth() {
            return depth;
        }

        public void addInterface(Type subType) {
            interfaces.add(subType);
        }

        public List<Type> getInterfaces() {
            return interfaces;
        }

        public boolean isDirectDescendantOf(Node potentialParent) {
            return potentialParent.getChildren().contains(this);
        }
    }

    private static class AncestorIterable implements Iterable<Type> {
        private final Node node;

        AncestorIterable(Node root) {
            node = root;
        }

        @Override
        public @NotNull Iterator<Type> iterator() {
            return new AncestorIterator(node);
        }

        private static class AncestorIterator implements Iterator<Type> {
            private Node current;
            private int interfaceIndex = -1;

            AncestorIterator(Node node) {
                this.current = node;
            }

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public Type next() {
                Type ret;
                if (interfaceIndex == -1) {
                    ret = current.value;
                } else {
                    ret = current.interfaces.get(interfaceIndex);
                }

                interfaceIndex++;
                if (interfaceIndex >= current.interfaces.size()) {
                    current = current.parent;
                    interfaceIndex = -1;
                }

                return ret;
            }
        }
    }
}
