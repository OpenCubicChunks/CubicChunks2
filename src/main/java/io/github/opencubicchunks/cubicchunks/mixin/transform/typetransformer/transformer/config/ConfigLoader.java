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
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformSubtype;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.AncestorHashMap;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import io.github.opencubicchunks.cubicchunks.utils.Utils;
import net.fabricmc.loader.api.MappingResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ConfigLoader {
    public static Config loadConfig(InputStream is){
        JsonParser parser = new JsonParser();
        JsonObject root = parser.parse(new InputStreamReader(is)).getAsJsonObject();

        MappingResolver map = getMapper();

        HierarchyTree hierarchy = new HierarchyTree();
        loadHierarchy(hierarchy, root.get("hierarchy").getAsJsonObject(), map, null);

        Map<String, MethodID> methodIDMap = loadMethodDefinitions(root.get("method_definitions"), map);
        Map<String, TransformType> transformTypeMap = loadTransformTypes(root.get("types"), map, methodIDMap);
        AncestorHashMap<MethodID, List<MethodParameterInfo>> parameterInfo = loadMethodParameterInfo(root.get("methods"), map, methodIDMap, transformTypeMap, hierarchy);
        Map<Type, ClassTransformInfo> classes = loadClassInfo(root.get("classes"), map, methodIDMap, transformTypeMap, hierarchy);
        List<InterfaceInfo> interfaces = loadInterfaces(root.get("interfaces"), map, transformTypeMap);

        for(TransformType type : transformTypeMap.values()){
            type.addParameterInfoTo(parameterInfo);
        }

        for(InterfaceInfo itf: interfaces){
            itf.addTransformsTo(parameterInfo);
        }

        Config config = new Config(
                hierarchy,
                transformTypeMap,
                parameterInfo,
                classes,
                interfaces
        );

        return config;
    }

    private static List<InterfaceInfo> loadInterfaces(JsonElement accessors, MappingResolver map, Map<String, TransformType> transformTypeMap) {
        JsonArray arr = accessors.getAsJsonArray();
        List<InterfaceInfo> interfaces = new ArrayList<>();

        for(JsonElement e : arr){
            JsonObject obj = e.getAsJsonObject();
            String name = obj.get("name").getAsString();

            JsonArray methods = obj.get("methods").getAsJsonArray();
            List<Method> methodsList = new ArrayList<>();
            List<TransformSubtype[]> argTypes = new ArrayList<>();
            for(JsonElement m : methods) {
                JsonObject obj2 = m.getAsJsonObject();
                String[] methodInfo = obj2.get("name").getAsString().split(" ");
                Method method = new Method(methodInfo[0], methodInfo[1]);

                TransformSubtype[] transformTypes = new TransformSubtype[Type.getArgumentTypes(method.getDescriptor()).length];

                JsonArray types = obj2.get("types").getAsJsonArray();

                int i;
                for (i = 0; i < types.size(); i++) {
                    String type = types.get(i).getAsString();
                    transformTypes[i] = TransformSubtype.fromString(type, transformTypeMap);
                }

                for(; i < transformTypes.length; i++){
                    transformTypes[i] = TransformSubtype.of(null);
                }

                methodsList.add(method);
                argTypes.add(transformTypes);
            }

            InterfaceInfo itf = new InterfaceInfo(Type.getObjectType(name), methodsList, argTypes);
            interfaces.add(itf);
        }

        return interfaces;
    }

    private static Map<Type, ClassTransformInfo> loadClassInfo(JsonElement classes, MappingResolver map, Map<String, MethodID> methodIDMap, Map<String, TransformType> transformTypeMap,
                                                               HierarchyTree hierarchy) {
        JsonArray arr = classes.getAsJsonArray();
        Map<Type, ClassTransformInfo> classInfo = new HashMap<>();
        for(JsonElement element : arr){
            JsonObject obj = element.getAsJsonObject();
            Type type = remapType(Type.getObjectType(obj.get("class").getAsString()), map, false);

            JsonArray typeHintsArr = obj.get("type_hints").getAsJsonArray();
            Map<MethodID, Map<Integer, TransformType>> typeHints = new AncestorHashMap<>(hierarchy);
            for(JsonElement typeHint : typeHintsArr){
                MethodID method = loadMethodIDFromLookup(typeHint.getAsJsonObject().get("method"), map, methodIDMap);
                Map<Integer, TransformType> paramTypes = new HashMap<>();
                JsonArray paramTypesArr = typeHint.getAsJsonObject().get("types").getAsJsonArray();
                for (int i = 0; i < paramTypesArr.size(); i++) {
                    JsonElement paramType = paramTypesArr.get(i);
                    if(!paramType.isJsonNull()){
                        paramTypes.put(i, transformTypeMap.get(paramType.getAsString()));
                    }
                }
                typeHints.put(method, paramTypes);
            }

            JsonElement constructorReplacersArr = obj.get("constructor_replacers");
            Map<String, ConstructorReplacer> constructorReplacers = new HashMap<>();
            if(constructorReplacersArr != null){
                for(JsonElement constructorReplacer : constructorReplacersArr.getAsJsonArray()){
                    JsonObject constructorReplacerObj = constructorReplacer.getAsJsonObject();
                    String original = constructorReplacerObj.get("original").getAsString();

                    if(!original.contains("(")){
                        original = "(" + original + ")V";
                    }

                    Map<String, String> replacements = new HashMap<>();
                    for(Map.Entry<String, JsonElement> replacement : constructorReplacerObj.get("type_replacements").getAsJsonObject().entrySet()){
                        Type type1 = remapType(Type.getObjectType(replacement.getKey()), map, false);
                        Type type2 = remapType(Type.getObjectType(replacement.getValue().getAsString()), map, false);

                        replacements.put(type1.getInternalName(), type2.getInternalName());
                    }

                    constructorReplacers.put(original, new ConstructorReplacer(original, replacements));
                }
            }

            ClassTransformInfo info = new ClassTransformInfo(typeHints, constructorReplacers);
            classInfo.put(type, info);
        }

        return classInfo;
    }

    private static void loadHierarchy(HierarchyTree hierarchy, JsonObject descendants, MappingResolver map, Type parent) {
        for(Map.Entry<String, JsonElement> entry : descendants.entrySet()) {
            if (entry.getKey().equals("extra_interfaces")){
                JsonArray arr = entry.getValue().getAsJsonArray();
                for(JsonElement element : arr){
                    Type type = remapType(Type.getObjectType(element.getAsString()), map, false);
                    hierarchy.addInterface(type);
                }
            }else if(entry.getKey().equals("__interfaces")){
                JsonArray arr = entry.getValue().getAsJsonArray();
                for(JsonElement element : arr){
                    Type type = remapType(Type.getObjectType(element.getAsString()), map, false);
                    hierarchy.addInterface(type, parent);
                }
            }else {
                Type type = remapType(Type.getObjectType(entry.getKey()), map, false);
                hierarchy.addNode(type, parent);
                loadHierarchy(hierarchy, entry.getValue().getAsJsonObject(), map, type);
            }
        }
    }

    private static AncestorHashMap<MethodID, List<MethodParameterInfo>> loadMethodParameterInfo(JsonElement methods, MappingResolver map, Map<String, MethodID> methodIDMap, Map<String, TransformType> transformTypes, HierarchyTree hierarchy) {
        final AncestorHashMap<MethodID, List<MethodParameterInfo>> parameterInfo = new AncestorHashMap<>(hierarchy);

        if(methods == null) return parameterInfo;

        if(!methods.isJsonArray()){
            System.err.println("Method parameter info is not an array. Cannot read it");
            return parameterInfo;
        }

        for(JsonElement method : methods.getAsJsonArray()){
            JsonObject obj = method.getAsJsonObject();
            MethodID methodID = loadMethodIDFromLookup(obj.get("method"), map, methodIDMap);
            List<MethodParameterInfo> paramInfo = new ArrayList<>();
            JsonArray possibilites = obj.get("possibilities").getAsJsonArray();
            for(JsonElement possibilityElement : possibilites) {
                JsonObject possibility = possibilityElement.getAsJsonObject();
                JsonArray paramsJson = possibility.get("parameters").getAsJsonArray();
                TransformSubtype[] params = new TransformSubtype[paramsJson.size()];
                for (int i = 0; i < paramsJson.size(); i++) {
                    JsonElement param = paramsJson.get(i);
                    if (param.isJsonPrimitive()) {
                        params[i] = TransformSubtype.fromString(param.getAsString(), transformTypes);
                    }
                }

                TransformSubtype returnType = TransformSubtype.of(null);
                JsonElement returnTypeJson = possibility.get("return");

                if (returnTypeJson != null) {
                    if (returnTypeJson.isJsonPrimitive()) {
                        returnType = TransformSubtype.fromString(returnTypeJson.getAsString(), transformTypes);
                    }
                }

                int expansionsNeeded = 1;
                if (returnType != null) {
                    expansionsNeeded = returnType.transformedTypes(Type.INT_TYPE /*This can be anything cause we just want the length*/).size();
                }

                List<Integer>[][] indices = new List[expansionsNeeded][params.length];
                BytecodeFactory[] expansion = new BytecodeFactory[expansionsNeeded];

                JsonElement replacementJson = possibility.get("replacement");
                JsonArray replacementJsonArray = null;
                if (replacementJson != null) {
                    if (replacementJson.isJsonArray()) {
                        replacementJsonArray = replacementJson.getAsJsonArray();
                        //Generate default indices
                        for (int i = 0; i < params.length; i++) {
                            TransformSubtype param = params[i];

                            if (param == null) {
                                for (int j = 0; j < expansionsNeeded; j++) {
                                    indices[j][i] = Collections.singletonList(0);
                                }
                                continue;
                            }

                            List<Type> types = param.transformedTypes(Type.INT_TYPE /*This doesn't matter because we are just querying the size*/);
                            if (types.size() != 1 && types.size() != expansionsNeeded && expansionsNeeded != 1) {
                                throw new IllegalArgumentException("Expansion size does not match parameter size");
                            }

                            if (types.size() == 1) {
                                for (int j = 0; j < expansionsNeeded; j++) {
                                    indices[j][i] = Collections.singletonList(0);
                                }
                            } else if (expansionsNeeded != 1){
                                for (int j = 0; j < expansionsNeeded; j++) {
                                    indices[j][i] = Collections.singletonList(j);
                                }
                            } else{
                                indices[0][i] = new ArrayList<>(types.size());
                                for (int j = 0; j < types.size(); j++) {
                                    indices[0][i].add(j);
                                }
                            }
                        }
                    } else {
                        JsonObject replacementObject = replacementJson.getAsJsonObject();
                        replacementJsonArray = replacementObject.get("expansion").getAsJsonArray();
                        JsonArray indicesJson = replacementObject.get("indices").getAsJsonArray();
                        for (int i = 0; i < indicesJson.size(); i++) {
                            JsonElement indices1 = indicesJson.get(i);
                            if (indices1.isJsonArray()) {
                                for (int j = 0; j < indices1.getAsJsonArray().size(); j++) {
                                    List<Integer> l = indices[i][j] = new ArrayList<>();
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
                                for (int j = 0; j < expansionsNeeded; j++) {
                                    indices[j][i] = Collections.singletonList(indices1.getAsInt());
                                }
                            }
                        }
                    }
                }

                MethodReplacement mr;
                if (replacementJsonArray == null) {
                    mr = null;
                } else {
                    BytecodeFactory[] factories = new BytecodeFactory[expansionsNeeded];
                    for (int i = 0; i < expansionsNeeded; i++) {
                        factories[i] = new JSONBytecodeFactory(replacementJsonArray.get(i).getAsJsonArray(), map, methodIDMap);
                    }

                    JsonElement finalizerJson = possibility.get("finalizer");
                    BytecodeFactory finalizer = null;
                    List<Integer>[] finalizerIndices = null;

                    if (finalizerJson != null) {
                        JsonArray finalizerJsonArray = finalizerJson.getAsJsonArray();
                        finalizer = new JSONBytecodeFactory(finalizerJsonArray, map, methodIDMap);

                        finalizerIndices = new List[params.length];
                        JsonElement finalizerIndicesJson = possibility.get("finalizerIndices");
                        if (finalizerIndicesJson != null) {
                            JsonArray finalizerIndicesJsonArray = finalizerIndicesJson.getAsJsonArray();
                            for (int i = 0; i < finalizerIndicesJsonArray.size(); i++) {
                                JsonElement finalizerIndicesJsonElement = finalizerIndicesJsonArray.get(i);
                                if (finalizerIndicesJsonElement.isJsonArray()) {
                                    finalizerIndices[i] = new ArrayList<>();
                                    for (JsonElement finalizerIndicesJsonElement1 : finalizerIndicesJsonElement.getAsJsonArray()) {
                                        finalizerIndices[i].add(finalizerIndicesJsonElement1.getAsInt());
                                    }
                                }else {
                                    finalizerIndices[i] = Collections.singletonList(finalizerIndicesJsonElement.getAsInt());
                                }
                            }
                        }else{
                            for (int i = 0; i < params.length; i++) {
                                List<Integer> l = new ArrayList<>();
                                for (int j = 0; j < params[i].transformedTypes(Type.INT_TYPE).size(); j++) {
                                    l.add(j);
                                }
                                finalizerIndices[i] = l;
                            }
                        }
                    }

                    mr = new MethodReplacement(factories, indices, finalizer, finalizerIndices);
                }

                JsonElement minimumsJson = possibility.get("minimums");
                MethodTransformChecker.Minimum[] minimums = null;
                if (minimumsJson != null) {
                    if (!minimumsJson.isJsonArray()) {
                        System.err.println("Minimums are not an array. Cannot read them");
                        continue;
                    }
                    minimums = new MethodTransformChecker.Minimum[minimumsJson.getAsJsonArray().size()];
                    for (int i = 0; i < minimumsJson.getAsJsonArray().size(); i++) {
                        JsonObject minimum = minimumsJson.getAsJsonArray().get(i).getAsJsonObject();

                        TransformSubtype minimumReturnType;
                        if (minimum.has("return")) {
                            minimumReturnType = TransformSubtype.fromString(minimum.get("return").getAsString(), transformTypes);
                        } else {
                            minimumReturnType = TransformSubtype.of(null);
                        }

                        TransformSubtype[] argTypes = new TransformSubtype[minimum.get("parameters").getAsJsonArray().size()];
                        for (int j = 0; j < argTypes.length; j++) {
                            JsonElement argType = minimum.get("parameters").getAsJsonArray().get(j);
                            if (!argType.isJsonNull()) {
                                argTypes[j] = TransformSubtype.fromString(argType.getAsString(), transformTypes);
                            } else {
                                argTypes[j] = TransformSubtype.of(null);
                            }
                        }

                        minimums[i] = new MethodTransformChecker.Minimum(minimumReturnType, argTypes);
                    }
                }

                MethodParameterInfo info = new MethodParameterInfo(methodID, returnType, params, minimums, mr);
                paramInfo.add(info);
            }
            parameterInfo.put(methodID, paramInfo);
        }

        return parameterInfo;
    }

    private static MethodID loadMethodIDFromLookup(JsonElement method, MappingResolver map, Map<String, MethodID> methodIDMap) {
        if(method.isJsonPrimitive()){
            if(methodIDMap.containsKey(method.getAsString())){
                return methodIDMap.get(method.getAsString());
            }
        }

        return loadMethodID(method, map, null);
    }

    private static Map<String, TransformType> loadTransformTypes(JsonElement typeJson, MappingResolver map, Map<String, MethodID> methodIDMap) {
        Map<String, TransformType> types = new HashMap<>();

        JsonArray typeArray = typeJson.getAsJsonArray();
        for(JsonElement type : typeArray){
            JsonObject obj = type.getAsJsonObject();
            String id = obj.get("id").getAsString();

            if(id.contains(" ")){
                throw new IllegalArgumentException("Transform type id cannot contain spaces");
            }

            Type original = remapType(Type.getType(obj.get("original").getAsString()), map, false);
            JsonArray transformedTypesArray = obj.get("transformed").getAsJsonArray();
            Type[] transformedTypes = new Type[transformedTypesArray.size()];
            for(int i = 0; i < transformedTypesArray.size(); i++){
                transformedTypes[i] = remapType(Type.getType(transformedTypesArray.get(i).getAsString()), map, false);
            }

            JsonElement fromOriginalJson = obj.get("from_original");
            MethodID[] fromOriginal = null;
            if(fromOriginalJson != null) {
                JsonArray fromOriginalArray = fromOriginalJson.getAsJsonArray();
                fromOriginal = new MethodID[fromOriginalArray.size()];
                if (fromOriginalArray.size() != transformedTypes.length) {
                    throw new IllegalArgumentException("Number of from_original methods does not match number of transformed types");
                }
                for (int i = 0; i < fromOriginalArray.size(); i++) {
                    JsonElement fromOriginalElement = fromOriginalArray.get(i);
                    if (fromOriginalElement.isJsonPrimitive()) {
                        fromOriginal[i] = methodIDMap.get(fromOriginalElement.getAsString());
                    }

                    if (fromOriginal[i] == null) {
                        fromOriginal[i] = loadMethodID(fromOriginalArray.get(i), map, null);
                    }
                }
            }

            MethodID toOriginal = null;
            JsonElement toOriginalJson = obj.get("to_original");
            if(toOriginalJson != null){
                toOriginal = loadMethodIDFromLookup(obj.get("to_original"), map, methodIDMap);
            }

            Type originalPredicateType = null;
            JsonElement originalPredicateTypeJson = obj.get("original_predicate");
            if(originalPredicateTypeJson != null){
                originalPredicateType = remapType(Type.getObjectType(originalPredicateTypeJson.getAsString()), map, false);
            }

            Type transformedPredicateType = null;
            JsonElement transformedPredicateTypeJson = obj.get("transformed_predicate");
            if(transformedPredicateTypeJson != null){
                transformedPredicateType = remapType(Type.getObjectType(transformedPredicateTypeJson.getAsString()), map, false);
            }

            Type originalConsumerType = null;
            JsonElement originalConsumerTypeJson = obj.get("original_consumer");
            if(originalConsumerTypeJson != null){
                originalConsumerType = remapType(Type.getObjectType(originalConsumerTypeJson.getAsString()), map, false);
            }

            Type transformedConsumerType = null;
            JsonElement transformedConsumerTypeJson = obj.get("transformed_consumer");
            if(transformedConsumerTypeJson != null){
                transformedConsumerType = remapType(Type.getObjectType(transformedConsumerTypeJson.getAsString()), map, false);
            }

            String[] postfix = new String[transformedTypes.length];
            JsonElement postfixJson = obj.get("postfix");
            if(postfixJson != null){
                JsonArray postfixArray = postfixJson.getAsJsonArray();
                for(int i = 0; i < postfixArray.size(); i++){
                    postfix[i] = postfixArray.get(i).getAsString();
                }
            }else if(postfix.length != 1){
                for(int i = 0; i < postfix.length; i++){
                    postfix[i] = "_" + id + "_" + i;
                }
            }else{
                postfix[0] = "_" + id;
            }

            Map<Object, BytecodeFactory[]> constantReplacements = new HashMap<>();
            JsonElement constantReplacementsJson = obj.get("constant_replacements");
            if(constantReplacementsJson != null) {
                JsonArray constantReplacementsArray = constantReplacementsJson.getAsJsonArray();
                for (int i = 0; i < constantReplacementsArray.size(); i++) {
                    JsonObject constantReplacementsObject = constantReplacementsArray.get(i).getAsJsonObject();
                    JsonPrimitive constantReplacementsFrom = constantReplacementsObject.get("from").getAsJsonPrimitive();

                    Object from;
                    if(constantReplacementsFrom.isString()){
                        from = constantReplacementsFrom.getAsString();
                    }else {
                        from = constantReplacementsFrom.getAsNumber();
                        from = getNumber(from, original.getSize() == 2);
                    }

                    JsonArray toArray = constantReplacementsObject.get("to").getAsJsonArray();
                    BytecodeFactory[] to = new BytecodeFactory[toArray.size()];
                    for(int j = 0; j < toArray.size(); j++){
                        JsonElement toElement = toArray.get(j);
                        if(toElement.isJsonPrimitive()){
                            JsonPrimitive toPrimitive = toElement.getAsJsonPrimitive();
                            if(toPrimitive.isString()){
                                to[j] = new ConstantFactory(toPrimitive.getAsString());
                            }else{
                                Number constant = toPrimitive.getAsNumber();
                                constant = getNumber(constant, transformedTypes[j].getSize() == 2);
                                to[j] = new ConstantFactory(constant);
                            }
                        }else{
                            to[j] = new JSONBytecodeFactory(toElement.getAsJsonArray(), map, methodIDMap);
                        }
                    }

                    constantReplacements.put(from, to);
                }
            }


            TransformType transformType = new TransformType(id, original, transformedTypes, fromOriginal, toOriginal, originalPredicateType, transformedPredicateType, originalConsumerType, transformedConsumerType, postfix, constantReplacements);
            types.put(id, transformType);
        }

        return types;
    }

    private static Number getNumber(Object from, boolean doubleSize){
        String s = from.toString();
        if(doubleSize){
            if(s.contains(".")){
                return Double.parseDouble(s);
            }else{
                return Long.parseLong(s);
            }
        }else {
            if(s.contains(".")){
                return Float.parseFloat(s);
            }else{
                return Integer.parseInt(s);
            }
        }
    }

    private static Map<String, MethodID> loadMethodDefinitions(JsonElement methodMap, MappingResolver map) {
        Map<String, MethodID> methodIDMap = new HashMap<>();

        if(methodMap == null) return methodIDMap;

        if(!methodMap.isJsonArray()){
            System.err.println("Method ID map is not an array. Cannot read it");
            return methodIDMap;
        }

        for(JsonElement method : methodMap.getAsJsonArray()){
            JsonObject obj = method.getAsJsonObject();
            String id = obj.get("id").getAsString();
            MethodID methodID = loadMethodID(obj.get("method"), map, null);

            methodIDMap.put(id, methodID);
        }

        return methodIDMap;
    }

    public static MethodID loadMethodID(JsonElement method, @Nullable MappingResolver map, MethodID.@Nullable CallType defaultCallType) {
        MethodID methodID;
        if(method.isJsonPrimitive()){
            String id = method.getAsString();
            String[] parts = id.split(" ");

            MethodID.CallType callType;
            int nameIndex;
            int descIndex;
            if(parts.length == 3){
                char callChar = parts[0].charAt(0);
                callType = switch (callChar){
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
            }else{
                callType = MethodID.CallType.VIRTUAL;
                nameIndex = 0;
                descIndex = 1;
            }

            if(defaultCallType != null){
                callType = defaultCallType;
            }

            String desc = parts[descIndex];

            String[] ownerAndName = parts[nameIndex].split("#");
            String owner = ownerAndName[0];
            String name = ownerAndName[1];

            methodID = new MethodID(Type.getObjectType(owner), name, Type.getMethodType(desc), callType);
        }else{
            String owner = method.getAsJsonObject().get("owner").getAsString();
            String name = method.getAsJsonObject().get("name").getAsString();
            String desc = method.getAsJsonObject().get("desc").getAsString();
            String callTypeStr = method.getAsJsonObject().get("call_type").getAsString();

            MethodID.CallType callType = MethodID.CallType.valueOf(callTypeStr.toUpperCase());

            methodID = new MethodID(Type.getObjectType(owner), name, Type.getMethodType(desc), callType);
        }

        if(map != null){
            //Remap the method ID
            methodID = remapMethod(methodID, map);
        }

        return methodID;
    }

    public static MethodID remapMethod(MethodID methodID, @NotNull MappingResolver map) {
        //Map owner
        Type mappedOwner = remapType(methodID.getOwner(), map, false);

        //Map name
        String mappedName = map.mapMethodName("intermediary",
                methodID.getOwner().getClassName(), methodID.getName(), methodID.getDescriptor().getInternalName()
        );

        //Map desc
        Type[] args = methodID.getDescriptor().getArgumentTypes();
        Type returnType = methodID.getDescriptor().getReturnType();

        Type[] mappedArgs = new Type[args.length];
        for(int i = 0; i < args.length; i++){
            mappedArgs[i] = remapType(args[i], map, false);
        }

        Type mappedReturnType = remapType(returnType, map, false);

        Type mappedDesc = Type.getMethodType(mappedReturnType, mappedArgs);

        return new MethodID(mappedOwner, mappedName, mappedDesc, methodID.getCallType());
    }

    public static Type remapType(Type type, @NotNull MappingResolver map, boolean warnIfNotPresent) {
        if(type.getSort() == Type.ARRAY){
            Type componentType = remapType(type.getElementType(), map, warnIfNotPresent);
            return Type.getType("[" + componentType.getDescriptor());
        }else if(type.getSort() == Type.OBJECT) {
            String unmapped = type.getClassName();
            String mapped = map.mapClassName("intermediary", unmapped);
            if (mapped == null) {
                if (warnIfNotPresent) {
                    System.err.println("Could not remap type: " + unmapped);
                }
                return type;
            }
            return Type.getObjectType(mapped.replace('.', '/'));
        }else{
            return type;
        }
    }

    private static MappingResolver getMapper() {
        return Utils.getMappingResolver();
    }
}
