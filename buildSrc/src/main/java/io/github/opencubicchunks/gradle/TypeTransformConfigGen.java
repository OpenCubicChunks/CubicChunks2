package io.github.opencubicchunks.gradle;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.gradle.api.Project;
import org.objectweb.asm.Type;

public class TypeTransformConfigGen {
    private static final Gson GSON = new Gson();

    private static final String MAP_FROM = "named";
    private static final String MAP_TO = "intermediary";

    private final JsonElement config;
    private final MappingTree mappings;

    private final int toIdx;
    private final int fromIdx;

    private TypeTransformConfigGen(MappingsProviderImpl mappingsProvider, String content) throws IOException {
        this.config = GSON.fromJson(content, JsonElement.class);
        this.mappings = mappingsProvider.getMappings();

        this.fromIdx = this.mappings.getDstNamespaces().indexOf(MAP_FROM);
        this.toIdx = this.mappings.getDstNamespaces().indexOf(MAP_TO);

        if (fromIdx == -1 && !MAP_FROM.equals(this.mappings.getSrcNamespace())) {
            throw new IllegalStateException("Cannot find namespace " + MAP_FROM + " in mappings");
        }

        if (toIdx == -1 && !MAP_TO.equals(this.mappings.getSrcNamespace())) {
            throw new IllegalStateException("Cannot find namespace " + MAP_TO + " in mappings");
        }
    }

    public static String apply(Project project, String content) throws IOException {
        System.out.println("Amending type transform config");
        LoomGradleExtension loom = (LoomGradleExtension) project.getExtensions().getByName("loom");

        MappingsProviderImpl mappingsProvider = loom.getMappingsProvider();
        System.out.println("mappingsProvider.getMappingsName() = " + mappingsProvider.mappingsIdentifier);
        MemoryMappingTree mappings = mappingsProvider.getMappings();

        System.out.println(mappings.getDstNamespaces());
        System.out.println(mappings.getSrcNamespace());

        TypeTransformConfigGen gen = new TypeTransformConfigGen(mappingsProvider, content);

        return gen.generate();
    }

    private String generate() {
        JsonObject root = config.getAsJsonObject();
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
            return Type.getObjectType(mapClassName(type.getClassName()));
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
