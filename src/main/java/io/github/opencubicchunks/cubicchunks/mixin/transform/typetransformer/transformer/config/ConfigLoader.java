package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.BytecodeFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.ConstantFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.JSONBytecodeFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.DerivedTransformType;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.AncestorHashMap;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import io.github.opencubicchunks.cubicchunks.utils.TestMappingUtils;
import net.fabricmc.loader.api.MappingResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ConfigLoader {
    public static Config loadConfig(InputStream is) {
        JsonParser parser = new JsonParser();
        JsonObject root = parser.parse(new InputStreamReader(is)).getAsJsonObject();

        MappingResolver map = getMapper();

        TypeInfo hierarchy = loadHierarchy(root.getAsJsonArray("type_info"), map);

        Map<String, TransformType> transformTypeMap = loadTransformTypes(root.get("types"), map);
        AncestorHashMap<MethodID, List<MethodParameterInfo>> parameterInfo = loadMethodParameterInfo(root.get("methods"), map, transformTypeMap, hierarchy);
        Map<Type, ClassTransformInfo> classes = loadClassInfo(root.get("classes"), map, transformTypeMap, hierarchy);
        Map<Type, InvokerInfo> invokers = loadInvokers(root.get("invokers"), map, transformTypeMap);

        for (TransformType type : transformTypeMap.values()) {
            type.addParameterInfoTo(parameterInfo);
        }

        for (InvokerInfo invoker : invokers.values()) {
            invoker.addReplacementTo(parameterInfo);
        }

        JsonArray suffixedMethods = root.getAsJsonArray("suffixed_methods");
        List<Type> typesWithSuffixedMethods = new ArrayList<>();

        for (JsonElement element : suffixedMethods) {
            typesWithSuffixedMethods.add(remapType(Type.getObjectType(element.getAsString()), map));
        }

        Config config = new Config(
            hierarchy,
            transformTypeMap,
            parameterInfo,
            classes,
            typesWithSuffixedMethods
        );

        return config;
    }

    private static Map<Type, InvokerInfo> loadInvokers(JsonElement accessors, MappingResolver map, Map<String, TransformType> transformTypeMap) {
        JsonArray arr = accessors.getAsJsonArray();
        Map<Type, InvokerInfo> interfaces = new HashMap<>();

        for (JsonElement e : arr) {
            JsonObject obj = e.getAsJsonObject();
            String name = obj.get("name").getAsString();

            String targetName = obj.get("target").getAsString();
            Type target = remapType(Type.getObjectType(targetName), map);

            JsonArray methods = obj.get("methods").getAsJsonArray();
            List<InvokerInfo.InvokerMethodInfo> methodInfos = new ArrayList<>();
            for (JsonElement m : methods) {
                JsonObject obj2 = m.getAsJsonObject();
                String[] methodInfo = obj2.get("name").getAsString().split(" ");
                Method method = new Method(methodInfo[0], methodInfo[1]);

                String targetMethod = obj2.get("calls").getAsString();
                targetMethod = map.mapMethodName("intermediary", targetName.replace('/', '.'), targetMethod, method.getDescriptor());

                Type[] args = Type.getArgumentTypes(method.getDescriptor());
                DerivedTransformType[] transformTypes = new DerivedTransformType[args.length];

                JsonArray types = obj2.get("types").getAsJsonArray();

                int i;
                for (i = 0; i < types.size(); i++) {
                    String type = types.get(i).getAsString();
                    transformTypes[i] = DerivedTransformType.fromString(type, transformTypeMap);
                }

                for (; i < transformTypes.length; i++) {
                    transformTypes[i] = DerivedTransformType.createDefault(args[i]);
                }

                methodInfos.add(new InvokerInfo.InvokerMethodInfo(transformTypes, method.getName(), targetMethod, method.getDescriptor()));
            }

            InvokerInfo invoker = new InvokerInfo(Type.getObjectType(name), target, methodInfos);
            interfaces.put(target, invoker);
        }

        return interfaces;
    }

    private static Map<Type, ClassTransformInfo> loadClassInfo(JsonElement classes, MappingResolver map, Map<String, TransformType> transformTypeMap,
                                                               TypeInfo hierarchy) {
        JsonArray arr = classes.getAsJsonArray();
        Map<Type, ClassTransformInfo> classInfo = new HashMap<>();
        for (JsonElement element : arr) {
            JsonObject obj = element.getAsJsonObject();
            Type type = remapType(Type.getObjectType(obj.get("class").getAsString()), map);

            JsonElement typeHintsElem = obj.get("type_hints");
            Map<MethodID, List<TransformType>> typeHints = new AncestorHashMap<>(hierarchy);
            if (typeHintsElem != null) {
                JsonArray typeHintsArr = typeHintsElem.getAsJsonArray();
                for (JsonElement typeHint : typeHintsArr) {
                    MethodID method = loadMethodID(typeHint.getAsJsonObject().get("method"), map, null);
                    List<TransformType> paramTypes = new ArrayList<>();
                    JsonArray paramTypesArr = typeHint.getAsJsonObject().get("types").getAsJsonArray();
                    for (int i = 0; i < paramTypesArr.size(); i++) {
                        JsonElement paramType = paramTypesArr.get(i);
                        if (!paramType.isJsonNull()) {
                            paramTypes.add(transformTypeMap.get(paramType.getAsString()));
                        } else {
                            paramTypes.add(null);
                        }
                    }
                    typeHints.put(method, paramTypes);
                }
            }

            JsonElement constructorReplacersArr = obj.get("constructor_replacers");
            Map<String, ConstructorReplacer> constructorReplacers = new HashMap<>();
            if (constructorReplacersArr != null) {
                for (JsonElement constructorReplacer : constructorReplacersArr.getAsJsonArray()) {
                    JsonObject constructorReplacerObj = constructorReplacer.getAsJsonObject();
                    String original = constructorReplacerObj.get("original").getAsString();

                    if (!original.contains("(")) {
                        original = "(" + original + ")V";
                    }

                    Map<String, String> replacements = new HashMap<>();
                    for (Map.Entry<String, JsonElement> replacement : constructorReplacerObj.get("type_replacements").getAsJsonObject().entrySet()) {
                        Type type1 = remapType(Type.getObjectType(replacement.getKey()), map);
                        Type type2 = remapType(Type.getObjectType(replacement.getValue().getAsString()), map);

                        replacements.put(type1.getInternalName(), type2.getInternalName());
                    }

                    constructorReplacers.put(original, new ConstructorReplacer(original, replacements));
                }
            }

            boolean inPlace = false;
            if (obj.has("in_place")) {
                inPlace = obj.get("in_place").getAsBoolean();
            }

            ClassTransformInfo info = new ClassTransformInfo(typeHints, constructorReplacers, inPlace);
            classInfo.put(type, info);
        }

        return classInfo;
    }

    private static TypeInfo loadHierarchy(JsonArray data, MappingResolver map) {
        return new TypeInfo(data, t -> remapType(t, map));
    }

    private static AncestorHashMap<MethodID, List<MethodParameterInfo>> loadMethodParameterInfo(JsonElement methods, MappingResolver map,
                                                                                                Map<String, TransformType> transformTypes, TypeInfo hierarchy) {
        final AncestorHashMap<MethodID, List<MethodParameterInfo>> parameterInfo = new AncestorHashMap<>(hierarchy);

        if (methods == null) return parameterInfo;

        if (!methods.isJsonArray()) {
            System.err.println("Method parameter info is not an array. Cannot read it");
            return parameterInfo;
        }

        for (JsonElement method : methods.getAsJsonArray()) {
            JsonObject obj = method.getAsJsonObject();
            MethodID methodID = loadMethodID(obj.get("method"), map, null);
            List<MethodParameterInfo> paramInfo = new ArrayList<>();
            JsonArray possibilites = obj.get("possibilities").getAsJsonArray();
            for (JsonElement possibilityElement : possibilites) {
                JsonObject possibility = possibilityElement.getAsJsonObject();
                JsonArray paramsJson = possibility.get("parameters").getAsJsonArray();
                DerivedTransformType[] params = loadParameterTypes(methodID, transformTypes, paramsJson);

                DerivedTransformType returnType = DerivedTransformType.createDefault(methodID.getDescriptor().getReturnType());
                JsonElement returnTypeJson = possibility.get("return");

                if (returnTypeJson != null) {
                    if (returnTypeJson.isJsonPrimitive()) {
                        returnType = DerivedTransformType.fromString(returnTypeJson.getAsString(), transformTypes);
                    }
                }

                int expansionsNeeded = returnType.resultingTypes().size();

                List<Integer>[][] indices = new List[expansionsNeeded][params.length];

                JsonElement replacementJson = possibility.get("replacement");
                JsonArray replacementJsonArray = null;
                if (replacementJson != null) {
                    if (replacementJson.isJsonArray()) {
                        replacementJsonArray = replacementJson.getAsJsonArray();
                        //Generate default indices
                        generateDefaultIndices(params, expansionsNeeded, indices);
                    } else {
                        JsonObject replacementObject = replacementJson.getAsJsonObject();
                        replacementJsonArray = replacementObject.get("expansion").getAsJsonArray();
                        loadProvidedIndices(expansionsNeeded, indices, replacementObject);
                    }
                }

                MethodReplacement mr =
                    getMethodReplacement(map, possibility, params, expansionsNeeded, indices, replacementJsonArray);

                JsonElement minimumsJson = possibility.get("minimums");
                MethodTransformChecker.MinimumConditions[] minimumConditions = getMinimums(methodID, transformTypes, minimumsJson);

                MethodParameterInfo info = new MethodParameterInfo(methodID, returnType, params, minimumConditions, mr);
                paramInfo.add(info);
            }
            parameterInfo.put(methodID, paramInfo);
        }

        return parameterInfo;
    }

    @NotNull private static DerivedTransformType[] loadParameterTypes(MethodID method, Map<String, TransformType> transformTypes, JsonArray paramsJson) {
        DerivedTransformType[] params = new DerivedTransformType[paramsJson.size()];
        Type[] args = method.getDescriptor().getArgumentTypes();

        for (int i = 0; i < paramsJson.size(); i++) {
            JsonElement param = paramsJson.get(i);
            if (param.isJsonPrimitive()) {
                params[i] = DerivedTransformType.fromString(param.getAsString(), transformTypes);
            } else if (param.isJsonNull()) {
                if (method.isStatic()) {
                    params[i] = DerivedTransformType.createDefault(args[i]);
                } else if (i == 0) {
                    params[i] = DerivedTransformType.createDefault(method.getOwner());
                } else {
                    params[i] = DerivedTransformType.createDefault(args[i - 1]);
                }
            }
        }


        return params;
    }

    private static void loadProvidedIndices(int expansionsNeeded, List<Integer>[][] indices, JsonObject replacementObject) {
        JsonArray indicesJson = replacementObject.get("indices").getAsJsonArray();
        for (int i = 0; i < indicesJson.size(); i++) {
            JsonElement indices1 = indicesJson.get(i);
            if (indices1.isJsonArray()) {
                for (int j = 0; j < indices1.getAsJsonArray().size(); j++) {
                    List<Integer> l = new ArrayList<>();
                    indices[i][j] = l;
                    JsonElement indices2 = indices1.getAsJsonArray().get(j);
                    if (indices2.isJsonArray()) {
                        for (JsonElement index : indices2.getAsJsonArray()) {
                            l.add(index.getAsInt());
                        }
                    } else {
                        l.add(indices2.getAsInt());
                    }
                }
            } else {
                throw new IllegalArgumentException("Indices must be an array of arrays");
            }
        }
    }

    private static void generateDefaultIndices(DerivedTransformType[] params, int expansionsNeeded, List<Integer>[][] indices) {
        for (int i = 0; i < params.length; i++) {
            DerivedTransformType param = params[i];

            if (param == null) {
                for (int j = 0; j < expansionsNeeded; j++) {
                    indices[j][i] = Collections.singletonList(0);
                }
                continue;
            }

            List<Type> types = param.resultingTypes();
            if (types.size() != 1 && types.size() != expansionsNeeded && expansionsNeeded != 1) {
                throw new IllegalArgumentException("Expansion size does not match parameter size");
            }

            if (types.size() == 1) {
                for (int j = 0; j < expansionsNeeded; j++) {
                    indices[j][i] = Collections.singletonList(0);
                }
            } else if (expansionsNeeded != 1) {
                for (int j = 0; j < expansionsNeeded; j++) {
                    indices[j][i] = Collections.singletonList(j);
                }
            } else {
                indices[0][i] = new ArrayList<>(types.size());
                for (int j = 0; j < types.size(); j++) {
                    indices[0][i].add(j);
                }
            }
        }
    }

    @Nullable private static MethodTransformChecker.MinimumConditions[] getMinimums(MethodID method, Map<String, TransformType> transformTypes, JsonElement minimumsJson) {
        MethodTransformChecker.MinimumConditions[] minimumConditions = null;
        if (minimumsJson != null) {
            if (!minimumsJson.isJsonArray()) {
                throw new RuntimeException("Minimums are not an array. Cannot read them");
            }
            minimumConditions = new MethodTransformChecker.MinimumConditions[minimumsJson.getAsJsonArray().size()];
            for (int i = 0; i < minimumsJson.getAsJsonArray().size(); i++) {
                JsonObject minimum = minimumsJson.getAsJsonArray().get(i).getAsJsonObject();

                DerivedTransformType minimumReturnType;
                if (minimum.has("return")) {
                    minimumReturnType = DerivedTransformType.fromString(minimum.get("return").getAsString(), transformTypes);
                } else {
                    minimumReturnType = DerivedTransformType.createDefault(method.getDescriptor().getReturnType());
                }

                DerivedTransformType[] argTypes = new DerivedTransformType[minimum.get("parameters").getAsJsonArray().size()];
                Type[] args = method.getDescriptor().getArgumentTypes();
                for (int j = 0; j < argTypes.length; j++) {
                    JsonElement argType = minimum.get("parameters").getAsJsonArray().get(j);
                    if (!argType.isJsonNull()) {
                        argTypes[j] = DerivedTransformType.fromString(argType.getAsString(), transformTypes);
                    } else if (j == 0 && !method.isStatic()) {
                        argTypes[j] = DerivedTransformType.createDefault(method.getOwner());
                    } else {
                        argTypes[j] = DerivedTransformType.createDefault(args[j - method.getCallType().getOffset()]);
                    }
                }

                minimumConditions[i] = new MethodTransformChecker.MinimumConditions(minimumReturnType, argTypes);
            }
        }
        return minimumConditions;
    }

    @Nullable
    private static MethodReplacement getMethodReplacement(MappingResolver map, JsonObject possibility, DerivedTransformType[] params, int expansionsNeeded,
                                                          List<Integer>[][] indices, JsonArray replacementJsonArray) {
        MethodReplacement mr;
        if (replacementJsonArray == null) {
            mr = null;
        } else {
            BytecodeFactory[] factories = new BytecodeFactory[expansionsNeeded];
            for (int i = 0; i < expansionsNeeded; i++) {
                factories[i] = new JSONBytecodeFactory(replacementJsonArray.get(i).getAsJsonArray(), map);
            }

            JsonElement finalizerJson = possibility.get("finalizer");
            BytecodeFactory finalizer = null;
            List<Integer>[] finalizerIndices = null;

            if (finalizerJson != null) {
                JsonArray finalizerJsonArray = finalizerJson.getAsJsonArray();
                finalizer = new JSONBytecodeFactory(finalizerJsonArray, map);

                finalizerIndices = new List[params.length];
                JsonElement finalizerIndicesJson = possibility.get("finalizer_indices");
                if (finalizerIndicesJson != null) {
                    JsonArray finalizerIndicesJsonArray = finalizerIndicesJson.getAsJsonArray();
                    for (int i = 0; i < finalizerIndicesJsonArray.size(); i++) {
                        JsonElement finalizerIndicesJsonElement = finalizerIndicesJsonArray.get(i);
                        if (finalizerIndicesJsonElement.isJsonArray()) {
                            finalizerIndices[i] = new ArrayList<>();
                            for (JsonElement finalizerIndicesJsonElement1 : finalizerIndicesJsonElement.getAsJsonArray()) {
                                finalizerIndices[i].add(finalizerIndicesJsonElement1.getAsInt());
                            }
                        } else {
                            finalizerIndices[i] = Collections.singletonList(finalizerIndicesJsonElement.getAsInt());
                        }
                    }
                } else {
                    for (int i = 0; i < params.length; i++) {
                        List<Integer> l = new ArrayList<>();
                        for (int j = 0; j < params[i].resultingTypes().size(); j++) {
                            l.add(j);
                        }
                        finalizerIndices[i] = l;
                    }
                }
            }

            mr = new MethodReplacement(factories, indices, finalizer, finalizerIndices);
        }
        return mr;
    }

    private static Map<String, TransformType> loadTransformTypes(JsonElement typeJson, MappingResolver map) {
        Map<String, TransformType> types = new HashMap<>();

        JsonArray typeArray = typeJson.getAsJsonArray();
        for (JsonElement type : typeArray) {
            JsonObject obj = type.getAsJsonObject();
            String id = obj.get("id").getAsString();

            if (id.contains(" ")) {
                throw new IllegalArgumentException("Transform type id cannot contain spaces");
            }

            Type original = remapType(Type.getType(obj.get("original").getAsString()), map);
            JsonArray transformedTypesArray = obj.get("transformed").getAsJsonArray();
            Type[] transformedTypes = new Type[transformedTypesArray.size()];
            for (int i = 0; i < transformedTypesArray.size(); i++) {
                transformedTypes[i] = remapType(Type.getType(transformedTypesArray.get(i).getAsString()), map);
            }

            JsonElement fromOriginalJson = obj.get("from_original");
            MethodID[] fromOriginal = loadFromOriginalTransform(map, transformedTypes, fromOriginalJson);

            MethodID toOriginal = null;
            JsonElement toOriginalJson = obj.get("to_original");
            if (toOriginalJson != null) {
                toOriginal = loadMethodID(obj.get("to_original"), map, null);
            }

            Type originalPredicateType = loadObjectType(obj, "original_predicate", map);

            Type transformedPredicateType = loadObjectType(obj, "transformed_predicate", map);

            Type originalConsumerType = loadObjectType(obj, "original_consumer", map);

            Type transformedConsumerType = loadObjectType(obj, "transformed_consumer", map);

            String[] postfix = loadPostfix(obj, id, transformedTypes);

            Map<Object, BytecodeFactory[]> constantReplacements =
                loadConstantReplacements(map, obj, original, transformedTypes);


            TransformType transformType =
                new TransformType(id, original, transformedTypes, fromOriginal, toOriginal, originalPredicateType, transformedPredicateType, originalConsumerType, transformedConsumerType,
                    postfix, constantReplacements);
            types.put(id, transformType);
        }

        return types;
    }

    @Nullable private static MethodID[] loadFromOriginalTransform(MappingResolver map, Type[] transformedTypes, JsonElement fromOriginalJson) {
        MethodID[] fromOriginal = null;

        if (fromOriginalJson != null) {
            JsonArray fromOriginalArray = fromOriginalJson.getAsJsonArray();
            fromOriginal = new MethodID[fromOriginalArray.size()];
            if (fromOriginalArray.size() != transformedTypes.length) {
                throw new IllegalArgumentException("Number of from_original methods does not match number of transformed types");
            }
            for (int i = 0; i < fromOriginalArray.size(); i++) {
                JsonElement fromOriginalElement = fromOriginalArray.get(i);
                fromOriginal[i] = loadMethodID(fromOriginalElement, map, null);
            }
        }

        return fromOriginal;
    }

    @Nullable
    private static Type loadObjectType(JsonObject object, String key, MappingResolver map) {
        JsonElement typeElement = object.get(key);
        if (typeElement == null) {
            return null;
        }
        return remapType(Type.getObjectType(typeElement.getAsString()), map);
    }

    @NotNull private static String[] loadPostfix(JsonObject obj, String id, Type[] transformedTypes) {
        String[] postfix = new String[transformedTypes.length];
        JsonElement postfixJson = obj.get("postfix");
        if (postfixJson != null) {
            JsonArray postfixArray = postfixJson.getAsJsonArray();
            for (int i = 0; i < postfixArray.size(); i++) {
                postfix[i] = postfixArray.get(i).getAsString();
            }
        } else if (postfix.length != 1) {
            for (int i = 0; i < postfix.length; i++) {
                postfix[i] = "_" + id + "_" + i;
            }
        } else {
            postfix[0] = "_" + id;
        }
        return postfix;
    }

    @NotNull
    private static Map<Object, BytecodeFactory[]> loadConstantReplacements(MappingResolver map, JsonObject obj, Type original, Type[] transformedTypes) {
        Map<Object, BytecodeFactory[]> constantReplacements = new HashMap<>();
        JsonElement constantReplacementsJson = obj.get("constant_replacements");
        if (constantReplacementsJson != null) {
            JsonArray constantReplacementsArray = constantReplacementsJson.getAsJsonArray();
            for (int i = 0; i < constantReplacementsArray.size(); i++) {
                JsonObject constantReplacementsObject = constantReplacementsArray.get(i).getAsJsonObject();
                JsonPrimitive constantReplacementsFrom = constantReplacementsObject.get("from").getAsJsonPrimitive();

                Object from;
                if (constantReplacementsFrom.isString()) {
                    from = constantReplacementsFrom.getAsString();
                } else {
                    from = constantReplacementsFrom.getAsNumber();
                    from = getNumber(from, original.getSize() == 2);
                }

                JsonArray toArray = constantReplacementsObject.get("to").getAsJsonArray();
                BytecodeFactory[] to = new BytecodeFactory[toArray.size()];
                for (int j = 0; j < toArray.size(); j++) {
                    JsonElement toElement = toArray.get(j);
                    if (toElement.isJsonPrimitive()) {
                        JsonPrimitive toPrimitive = toElement.getAsJsonPrimitive();
                        if (toPrimitive.isString()) {
                            to[j] = new ConstantFactory(toPrimitive.getAsString());
                        } else {
                            Number constant = toPrimitive.getAsNumber();
                            constant = getNumber(constant, transformedTypes[j].getSize() == 2);
                            to[j] = new ConstantFactory(constant);
                        }
                    } else {
                        to[j] = new JSONBytecodeFactory(toElement.getAsJsonArray(), map);
                    }
                }

                constantReplacements.put(from, to);
            }
        }
        return constantReplacements;
    }

    private static Number getNumber(Object from, boolean doubleSize) {
        String s = from.toString();
        if (doubleSize) {
            if (s.contains(".")) {
                return Double.parseDouble(s);
            } else {
                return Long.parseLong(s);
            }
        } else {
            if (s.contains(".")) {
                return Float.parseFloat(s);
            } else {
                return Integer.parseInt(s);
            }
        }
    }

    /**
     * Parses a method ID from a JSON object or the string
     * @param method The JSON object or string
     * @param map The mapping resolver
     * @param defaultCallType The default call type to use if the json doesn't specify it. Used when the call type can be inferred from context.
     * @return The parsed method ID
     */
    public static MethodID loadMethodID(JsonElement method, @Nullable MappingResolver map, @Nullable MethodID.CallType defaultCallType) {
        MethodID methodID;
        if (method.isJsonPrimitive()) {
            String id = method.getAsString();
            String[] parts = id.split(" ");

            MethodID.CallType callType;
            int nameIndex;
            int descIndex;
            if (parts.length == 3) {
                char callChar = parts[0].charAt(0);
                callType = switch (callChar) {
                    case 'v' -> MethodID.CallType.VIRTUAL;
                    case 's' -> MethodID.CallType.STATIC;
                    case 'i' -> MethodID.CallType.INTERFACE;
                    case 'S' -> MethodID.CallType.SPECIAL;
                    default -> {
                        System.err.println("Invalid call type: " + callChar + ". Using default VIRTUAL type");
                        yield MethodID.CallType.VIRTUAL;
                    }
                };

                nameIndex = 1;
                descIndex = 2;
            } else {
                if (defaultCallType == null) {
                    throw new IllegalArgumentException("Invalid method ID: " + id);
                }

                callType = defaultCallType;

                nameIndex = 0;
                descIndex = 1;
            }

            String desc = parts[descIndex];

            String[] ownerAndName = parts[nameIndex].split("#");
            String owner = ownerAndName[0];
            String name = ownerAndName[1];

            methodID = new MethodID(Type.getObjectType(owner), name, Type.getMethodType(desc), callType);
        } else {
            String owner = method.getAsJsonObject().get("owner").getAsString();
            String name = method.getAsJsonObject().get("name").getAsString();
            String desc = method.getAsJsonObject().get("desc").getAsString();

            MethodID.CallType callType;
            JsonElement callTypeElement = method.getAsJsonObject().get("call_type");

            if (callTypeElement == null) {
                if (defaultCallType == null) {
                    throw new IllegalArgumentException("Invalid method ID: " + method);
                }

                callType = defaultCallType;
            } else {
                String callTypeStr = callTypeElement.getAsString();
                callType = MethodID.CallType.valueOf(callTypeStr.toUpperCase());
            }

            methodID = new MethodID(Type.getObjectType(owner), name, Type.getMethodType(desc), callType);
        }

        if (map != null) {
            //Remap the method ID
            methodID = remapMethod(methodID, map);
        }

        return methodID;
    }

    public static MethodID remapMethod(MethodID methodID, @NotNull MappingResolver map) {
        //Map owner
        Type mappedOwner = remapType(methodID.getOwner(), map);

        //Map name
        String mappedName = map.mapMethodName("intermediary",
            methodID.getOwner().getClassName(), methodID.getName(), methodID.getDescriptor().getInternalName()
        );

        //Map desc
        Type[] args = methodID.getDescriptor().getArgumentTypes();
        Type returnType = methodID.getDescriptor().getReturnType();

        Type[] mappedArgs = new Type[args.length];
        for (int i = 0; i < args.length; i++) {
            mappedArgs[i] = remapType(args[i], map);
        }

        Type mappedReturnType = remapType(returnType, map);

        Type mappedDesc = Type.getMethodType(mappedReturnType, mappedArgs);

        return new MethodID(mappedOwner, mappedName, mappedDesc, methodID.getCallType());
    }

    public static Type remapType(Type type, MappingResolver map) {
        if (type.getSort() == Type.ARRAY) {
            Type componentType = remapType(type.getElementType(), map);
            return Type.getType("[" + componentType.getDescriptor());
        } else if (type.getSort() == Type.OBJECT) {
            String unmapped = type.getClassName();
            String mapped = map.mapClassName("intermediary", unmapped);
            if (mapped == null) {
                return type;
            }
            return Type.getObjectType(mapped.replace('.', '/'));
        } else {
            return type;
        }
    }

    private static MappingResolver getMapper() {
        return TestMappingUtils.getMappingResolver();
    }
}
