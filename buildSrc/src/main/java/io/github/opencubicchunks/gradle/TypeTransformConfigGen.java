package io.github.opencubicchunks.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public class TypeTransformConfigGen {
    private static final Gson GSON = new Gson();

    private static final String MAP_FROM = "named";
    private static final String MAP_TO = "intermediary";

    private final JsonElement config;
    private final MappingTree mappings;

    private final int toIdx;
    private final int fromIdx;

    private final Project project;

    private final Set<ZipFile> jars = new HashSet<>();

    private final Map<String, ClassNode> classCache = new HashMap<>();

    public TypeTransformConfigGen(Project project, MappingTree mappings, String content) throws IOException {
        this.config = GSON.fromJson(content, JsonElement.class);
        this.mappings = mappings;
        this.project = project;

        this.fromIdx = this.mappings.getDstNamespaces().indexOf(MAP_FROM);
        this.toIdx = this.mappings.getDstNamespaces().indexOf(MAP_TO);

        if (fromIdx == -1 && !MAP_FROM.equals(this.mappings.getSrcNamespace())) {
            throw new IllegalStateException("Cannot find namespace " + MAP_FROM + " in mappings");
        }

        if (toIdx == -1 && !MAP_TO.equals(this.mappings.getSrcNamespace())) {
            throw new IllegalStateException("Cannot find namespace " + MAP_TO + " in mappings");
        }


        for (Configuration config: project.getConfigurations()) {
            if (config.getName().startsWith("runtime") && config.isCanBeResolved()) {
                for (File file : config.resolve()) {
                    if (file.getName().endsWith(".jar")) {
                        jars.add(new ZipFile(file));
                    }
                }
            }
        }

        for (ZipFile jar : jars) {
            System.out.println("Loading " + jar.getName());
        }
    }

    public String generate() {
        JsonObject root = config.getAsJsonObject();
        this.generateTypeInfo(root);

        if (root.has("invokers")) {
            this.processInvokerData(root.getAsJsonArray("invokers"));
        }

        JsonElement res = walkAndMapNames(root);

        //Format the output with 2 spaces
        StringWriter writer = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(writer);
        jsonWriter.setIndent("  ");

        GSON.toJson(res, jsonWriter);

        return writer.toString();
    }

    private void generateTypeInfo(JsonObject root) {
        JsonArray typeInfo = new JsonArray();
        Set<Type> processed = new HashSet<>();
        Queue<Type> queue = new ArrayDeque<>();

        JsonObject meta = root.getAsJsonObject("type_meta_info");
        JsonArray inspect = meta.getAsJsonArray("inspect");

        for (JsonElement e : inspect) {
            ClassNode classNode = findClass(e.getAsString());
            queue.addAll(getAllUsedTypes(classNode));
        }

        while (!queue.isEmpty()) {
            Type top = queue.poll();

            if (processed.contains(top)) continue;
            processed.add(top);

            JsonObject typeInfoEntry = new JsonObject();
            ClassNode classNode = findClass(top.getInternalName());

            typeInfoEntry.addProperty("name", top.getClassName().replace('.', '/'));
            typeInfoEntry.addProperty("is_interface", (classNode.access & Opcodes.ACC_INTERFACE) != 0);

            typeInfoEntry.addProperty("superclass", classNode.superName);
            if (classNode.superName != null) {
                queue.add(Type.getObjectType(classNode.superName));
            }

            JsonArray interfaces = new JsonArray();
            for (String iface : classNode.interfaces) {
                interfaces.add(iface);
                queue.add(Type.getObjectType(iface));
            }
            typeInfoEntry.add("interfaces", interfaces);

            typeInfo.add(typeInfoEntry);
        }

        root.add("type_info", typeInfo);
    }

    private Set<Type> getAllUsedTypes(ClassNode node) {
        Set<Type> types = new HashSet<>();
        types.add(Type.getObjectType(node.name));

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9) {
            @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                types.add(Type.getType(descriptor));
                return null;
            }

            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                Type[] args = Type.getArgumentTypes(descriptor);
                types.addAll(Arrays.asList(args));
                types.add(Type.getReturnType(descriptor));
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitTypeInsn(int opcode, String type) {
                        types.add(Type.getObjectType(type));
                    }

                    @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        types.add(Type.getObjectType(owner));
                        types.add(Type.getType(descriptor));
                    }

                    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        types.add(Type.getObjectType(owner));
                        Type[] args = Type.getArgumentTypes(descriptor);
                        types.addAll(Arrays.asList(args));
                        types.add(Type.getReturnType(descriptor));
                    }

                    @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                        //TODO: Do this better
                        types.add(Type.getReturnType(descriptor));
                    }

                    @Override public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                        types.add(Type.getType(descriptor));
                    }
                };
            }
        };

        node.accept(visitor);

        return types.stream()
            .map(t -> t.getSort() == Type.ARRAY ? t.getElementType() : t)
            .filter(t -> t.getSort() == Type.OBJECT)
            .collect(Collectors.toSet());
    }

    private ClassNode findClass(String name) {
        if (classCache.containsKey(name)) {
            return classCache.get(name);
        }

        String path = name.replace('.', '/') + ".class";

        InputStream in = null;
        for (ZipFile jar : jars) {
            ZipEntry entry = jar.getEntry(path);
            if (entry != null) {
                try {
                    in = jar.getInputStream(entry);
                    break;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (in == null) {
            try {
                in = ClassLoader.getSystemResourceAsStream(path);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (in == null) {
            throw new IllegalStateException("Cannot find class " + name);
        }

        try {
            ClassReader reader = new ClassReader(in);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);
            classCache.put(name, node);
            System.out.println("Found class " + name);
            return node;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processInvokerData(JsonArray element) {
        for (JsonElement invokerDataElem: element) {
            JsonObject invokerData = invokerDataElem.getAsJsonObject();

            String target = invokerData.get("target").getAsString();

            for (JsonElement method: invokerData.getAsJsonArray("methods")) {
                JsonObject methodObj = method.getAsJsonObject();

                String[] name = methodObj.get("name").getAsString().split(" ");
                String calls = methodObj.get("calls").getAsString();

                methodObj.addProperty("name", name[0] + " " + mapMethodDesc(name[1]));
                methodObj.addProperty("calls", mapMethodName(target, calls, name[1]));
            }
        }
    }

    private JsonElement walkAndMapNames(JsonElement element) {
        //Check if element is a method object
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("owner") && obj.has("name") && obj.has("desc") && obj.has("call_type")) {
                String owner = obj.get("owner").getAsString();
                String name = obj.get("name").getAsString();
                String desc = obj.get("desc").getAsString();

                obj.addProperty("owner", mapClassName(owner));
                obj.addProperty("name", mapMethodName(owner, name, desc));
                obj.addProperty("desc", mapMethodDesc(desc));
            } else {
                JsonObject newObject = new JsonObject();

                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    newObject.add(mapClassName(entry.getKey()), walkAndMapNames(entry.getValue()));
                }

                return newObject;
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();

            for (int i = 0; i < array.size(); i++) {
                array.set(i, walkAndMapNames(array.get(i)));
            }

            return array;
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();

            if (primitive.isString()) {
                String str = primitive.getAsString();

                if (!str.contains("(")) {
                    return new JsonPrimitive(mapClassName(str));
                } else {
                    String[] split = str.split(" ");

                    if (split.length == 3) {
                        String[] ownerAndName = split[1].split("#");

                        return new JsonPrimitive(
                            split[0] + " " +
                                mapClassName(ownerAndName[0]) + "#" +
                                mapMethodName(ownerAndName[0], ownerAndName[1], split[2]) + " " +
                                mapMethodDesc(split[2])
                        );
                    }

                    return primitive;
                }
            }

            return primitive;
        }

        return element;
    }

    private String mapClassName(String name) {
        MappingTree.ClassMapping classMapping = this.fromIdx == -1 ? this.mappings.getClass(name) : this.mappings.getClass(name, this.fromIdx);

        if (classMapping == null) {
            return name;
        }

        return map((MappingTreeView.ElementMappingView) classMapping);
    }

    private String mapMethodName(String owner, String name, String desc) {
        MappingTree.MethodMapping methodMapping = this.fromIdx == -1 ? this.mappings.getMethod(owner, name, desc) : this.mappings.getMethod(owner, name, desc, this.fromIdx);

        if (methodMapping == null) {
            return name;
        }

        return map((MappingTreeView.ElementMappingView) methodMapping);
    }

    private String mapMethodDesc(String desc) {
        Type returnType = Type.getReturnType(desc);
        Type[] argumentTypes = Type.getArgumentTypes(desc);

        returnType = mapType(returnType);

        for (int i = 0; i < argumentTypes.length; i++) {
            argumentTypes[i] = mapType(argumentTypes[i]);
        }

        return Type.getMethodDescriptor(returnType, argumentTypes);
    }

    private Type mapType(Type type) {
        if (type.getSort() == Type.ARRAY) {
            int dimensions = type.getDimensions();
            return Type.getType("[".repeat(dimensions) + mapType(type.getElementType()).getDescriptor());
        } else if (type.getSort() == Type.OBJECT) {
            return Type.getObjectType(mapClassName(type.getInternalName()));
        } else {
            return type;
        }
    }

    private String map(MappingTreeView.ElementMappingView element) {
        if (this.toIdx == -1) {
            return element.getSrcName();
        } else {
            return element.getDstName(this.toIdx);
        }
    }
}
