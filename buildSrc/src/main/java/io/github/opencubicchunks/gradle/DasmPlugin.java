package io.github.opencubicchunks.gradle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class DasmPlugin implements Plugin<Project> {
    private static final boolean TO_NAMED = false;

    @Override public void apply(Project project) {
        ProcessResources processResources = ((ProcessResources) project.getTasks().getByName("processResources"));
        LoomGradleExtension loom = (LoomGradleExtension) project.getExtensions().getByName("loom");

        File destinationDir = processResources.getDestinationDir();
        processResources.filesMatching("dasm/**/*.json", copySpec -> {
            MappingsProviderImpl mappingsProvider = loom.getMappingsProvider();

            copySpec.exclude();
            File file = copySpec.getFile();
            File output = copySpec.getRelativePath().getFile(destinationDir);
            processFile(file, output, mappingsProvider);
        });
    }

    private void processFile(File file, File output, MappingsProviderImpl mappingsProvider) {
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
            MemoryMappingTree mappings = mappingsProvider.getMappings();

            JsonElement parsed = new JsonParser().parse(bufferedReader);
            if (file.getName().equals("targets.json")) {
                parsed = processTargets(parsed, mappings);
            } else {
                parsed = processSets(parsed, mappings);
            }
            Files.createDirectories(output.toPath().getParent());
            Files.write(output.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(parsed).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private JsonElement processSets(JsonElement parsed, MemoryMappingTree mappings) {
        int intermediary = mappings.getDstNamespaces().indexOf("intermediary");
        int named = mappings.getDstNamespaces().indexOf("named");

        JsonObject output = new JsonObject();
        for (Map.Entry<String, JsonElement> set : parsed.getAsJsonObject().entrySet()) {
            output.add(set.getKey(), remapSet(mappings, intermediary, named, set.getValue().getAsJsonObject()));
        }
        return output;
    }

    private JsonObject remapSet(MemoryMappingTree mappings, int intermediary, int named, JsonObject value) {
        JsonObject output = new JsonObject();
        for (Map.Entry<String, JsonElement> setPart : value.entrySet()) {
            if (setPart.getKey().equals("typeRedirects")) {
                output.add(setPart.getKey(), remapTypeRedirects(mappings, intermediary, named, setPart.getValue().getAsJsonObject()));
            } else if (setPart.getKey().equals("fieldRedirects")) {
                output.add(setPart.getKey(), remapFieldRedirects(mappings, intermediary, named, setPart.getValue().getAsJsonObject()));
            } else if (setPart.getKey().equals("methodRedirects")) {
                output.add(setPart.getKey(), remapMethodRedirects(mappings, intermediary, named, setPart.getValue().getAsJsonObject()));
            } else {
                output.add(setPart.getKey(), setPart.getValue());
            }
        }
        return output;
    }

    private JsonObject remapTypeRedirects(MemoryMappingTree mappings, int intermediary, int named, JsonObject redirects) {
        JsonObject output = new JsonObject();
        for (Map.Entry<String, JsonElement> typeEntry : redirects.entrySet()) {
            output.add(remapClassName(mappings, intermediary, named, typeEntry.getKey()), typeEntry.getValue());
        }
        return output;
    }

    private JsonObject remapFieldRedirects(MemoryMappingTree mappings, int intermediary, int named, JsonObject redirects) {
        JsonObject output = new JsonObject();
        for (Map.Entry<String, JsonElement> fieldJsonEntry : redirects.entrySet()) {
            String key = fieldJsonEntry.getKey();
            JsonElement fieldVal = fieldJsonEntry.getValue();
            String[] parts = key.split("\s*\\|\s*");
            String rawOwner = parts[0];
            String mappingsOwner = rawOwner;
            if (fieldJsonEntry.getValue().isJsonObject() && fieldJsonEntry.getValue().getAsJsonObject().get("mappingsOwner") != null) {
                mappingsOwner = fieldJsonEntry.getValue().getAsJsonObject().get("mappingsOwner").getAsString();
                String newMappingsOwner = remapClassName(mappings, intermediary, named, mappingsOwner);
                fieldVal.getAsJsonObject().add("mappingsOwner", new JsonPrimitive(newMappingsOwner));
            }
            String fieldDecl = parts[1];
            Method fakeMethod = Method.getMethod(fieldDecl + "()");
            Type type = fakeMethod.getReturnType();
            String name = fakeMethod.getName();

            String newOwner = remapClassName(mappings, intermediary, named, rawOwner);
            Type newType = remapType(mappings, intermediary, named, type);
            String newName = remapFieldName(mappings, intermediary, named, mappingsOwner, type, name);

            String newKey = newOwner + " | " + newType.getClassName() + " " + newName;

            output.add(newKey, fieldVal);
        }
        return output;
    }

    private JsonObject remapMethodRedirects(MemoryMappingTree mappings, int intermediary, int named, JsonObject redirects) {
        JsonObject output = new JsonObject();
        for (Map.Entry<String, JsonElement> methodJsonEntry : redirects.entrySet()) {
            String key = methodJsonEntry.getKey();
            JsonElement methodVal = methodJsonEntry.getValue();
            String[] parts = key.split("\s*\\|\s*");
            String rawOwner = parts[0];
            String mappingsOwner = rawOwner;
            if (methodJsonEntry.getValue().isJsonObject() && methodJsonEntry.getValue().getAsJsonObject().get("mappingsOwner") != null) {
                mappingsOwner = methodJsonEntry.getValue().getAsJsonObject().get("mappingsOwner").getAsString();
                String newMappingsOwner = remapClassName(mappings, intermediary, named, mappingsOwner);
                methodVal.getAsJsonObject().add("mappingsOwner", new JsonPrimitive(newMappingsOwner));
            }
            String methodDecl = parts[1];
            Method asmMethod = Method.getMethod(methodDecl);
            Type returnType = asmMethod.getReturnType();
            Type[] argumentTypes = asmMethod.getArgumentTypes();
            String name = asmMethod.getName();

            for (int i = 0; i < argumentTypes.length; i++) {
                argumentTypes[i] = remapType(mappings, intermediary, named, argumentTypes[i]);
            }
            String newOwner = remapClassName(mappings, intermediary, named, rawOwner);
            Type newReturnType = remapType(mappings, intermediary, named, returnType);
            String newName = remapMethodName(mappings, intermediary, named, mappingsOwner, asmMethod);

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(newOwner).append(" | ");
            stringBuilder.append(newReturnType.getClassName()).append(" ").append(newName).append("(");
            stringBuilder.append(Arrays.stream(argumentTypes).map(arg -> arg.getClassName()).collect(Collectors.joining(", ")));
            stringBuilder.append(")");
            String newKey = stringBuilder.toString();

            output.add(newKey, methodVal);
        }
        return output;
    }
    private JsonElement processTargets(JsonElement parsed, MemoryMappingTree mappings) throws IOException {
        int intermediary = mappings.getDstNamespaces().indexOf("intermediary");
        int named = mappings.getDstNamespaces().indexOf("named");

        JsonObject out = new JsonObject();
        Set<Map.Entry<String, JsonElement>> jsonEntries = new HashSet<>(parsed.getAsJsonObject().entrySet());

        for (Map.Entry<String, JsonElement> jsonEntry : jsonEntries) {
            String className = jsonEntry.getKey();
            String dstName = remapClassName(mappings, intermediary, named, className);

            JsonObject newClassEntry = remapClassValue(className, mappings, intermediary, named, jsonEntry);
            out.add(dstName, newClassEntry);
            int i = 0;
        }
        return out;
    }

    private static JsonObject remapClassValue(String ownerName, MemoryMappingTree mappings, int intermediary, int named, Map.Entry<String, JsonElement> jsonEntry) {
        JsonObject classEntry = jsonEntry.getValue().getAsJsonObject();
        JsonObject newClassEntry = new JsonObject();
        for (Map.Entry<String, JsonElement> classEntryJsonEntry : classEntry.entrySet()) {
            if (classEntryJsonEntry.getKey().equals("targetMethods")) {
                JsonObject newTargetMethods = new JsonObject();
                for (Map.Entry<String, JsonElement> methodJsonEntry : classEntryJsonEntry.getValue().getAsJsonObject().entrySet()) {
                    JsonElement methodVal = methodJsonEntry.getValue();
                    String methodOwner;
                    if (methodVal.isJsonObject() && methodVal.getAsJsonObject().get("mappingsOwner") != null) {
                        String mappingsOwner = methodVal.getAsJsonObject().get("mappingsOwner").getAsString();
                        String newMappingsOwner = remapClassName(mappings, intermediary, named, mappingsOwner);
                        methodVal.getAsJsonObject().add("mappingsOwner", new JsonPrimitive(newMappingsOwner));
                        methodOwner = mappingsOwner;
                    } else {
                        methodOwner = ownerName;
                    }
                    String methodKey = methodJsonEntry.getKey();
                    Method asmMethod = Method.getMethod(methodKey);
                    Type returnType = asmMethod.getReturnType();
                    Type newReturnType = remapType(mappings, intermediary, named, returnType);

                    Type[] argumentTypes = asmMethod.getArgumentTypes();
                    for (int i = 0; i < argumentTypes.length; i++) {
                        argumentTypes[i] = remapType(mappings, intermediary, named, argumentTypes[i]);
                    }

                    String mappedName = remapMethodName(mappings, intermediary, named, methodOwner, asmMethod);

                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(newReturnType.getClassName()).append(" ").append(mappedName).append("(");
                    stringBuilder.append(Arrays.stream(argumentTypes).map(arg -> arg.getClassName()).collect(Collectors.joining(", ")));
                    stringBuilder.append(")");
                    String newKey = stringBuilder.toString();
                    newTargetMethods.add(newKey, methodVal);
                }
                newClassEntry.add(classEntryJsonEntry.getKey(), newTargetMethods);
            } else {
                newClassEntry.add(classEntryJsonEntry.getKey(), classEntryJsonEntry.getValue());
            }
        }
        return newClassEntry;
    }

    private static String remapMethodName(MemoryMappingTree mappings, int intermediary, int named, String methodOwner, Method asmMethod) {
        MappingTree.ClassMapping mapEntry = mappings.getClass(methodOwner.replace('.', '/'), TO_NAMED ? intermediary : named);
        if (mapEntry == null) {
            return asmMethod.getName();
        }
        MappingTree.MethodMapping method = mapEntry.getMethod(asmMethod.getName(), asmMethod.getDescriptor(), TO_NAMED ? intermediary : named);
        if (method == null) {
            return asmMethod.getName();
        }
        return method.getDstName(TO_NAMED ? named : intermediary);
    }

    private static String remapFieldName(MemoryMappingTree mappings, int intermediary, int named, String fieldOwner, Type type, String name) {
        MappingTree.ClassMapping mapEntry = mappings.getClass(fieldOwner.replace('.', '/'), TO_NAMED ? intermediary : named);
        MappingTree.FieldMapping method = mapEntry.getField(name, type.getDescriptor(), TO_NAMED ? intermediary : named);
        if (method == null) {
            return name;
        }
        return method.getDstName(TO_NAMED ? named : intermediary);
    }

    private static String remapClassName(MemoryMappingTree mappings, int intermediary, int named, String className) {
        MappingTree.ClassMapping mapEntry = mappings.getClass(className.replace('.', '/'), TO_NAMED ? intermediary : named);
        String dstName = mapEntry == null ? className : mapEntry.getDstName(TO_NAMED ? named : intermediary).replace('/', '.');
        return dstName;
    }

    private static Type remapType(MemoryMappingTree mappings, int intermediary, int named, Type type) {
        switch (type.getSort()) {
            case Type.VOID:
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
            case Type.FLOAT:
            case Type.LONG:
            case Type.DOUBLE:
                return type;
            case Type.ARRAY:
                return Type.getType('[' + remapType(mappings, intermediary, named, type.getElementType()).getDescriptor());
            case Type.OBJECT:
                MappingTree.ClassMapping mapEntry = mappings.getClass(type.getInternalName(), TO_NAMED ? intermediary : named);
                if (mapEntry == null) {
                    return type;
                }
                return Type.getObjectType(mapEntry.getDstName(TO_NAMED ? named : intermediary));
            default:
                throw new IllegalArgumentException("Invalid type sort: " + type.getSort());
        }
    }
}
