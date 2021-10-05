package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.bytecodegen.BytecodeFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.bytecodegen.JSONBytecodeFactory;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.core.BlockPos;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;

public class MethodInfo {
    private final boolean returnsExpandedLong;
    private final Set<Integer> expandedArgumentIndices;
    private final int numArgs;

    private final String newOwner;
    private final String newName;
    private final String newDesc;

    private final String originalOwner;
    private final String originalName;
    private final String originalDescriptor;

    private final List<Integer[]> separatedArguments = new ArrayList<>();

    private final boolean isStatic;

    private final BytecodeFactory[] expansions = new BytecodeFactory[3];

    public MethodInfo(JsonObject root, String methodOwner, String methodName, String methodDescriptor, MappingResolver mappings){
        originalDescriptor = mapDescriptor(methodDescriptor, mappings);

        String dotOwnerName = methodOwner.replace('/', '.');
        originalOwner = mappings.mapClassName("intermediary", dotOwnerName).replace('.', '/');
        originalName = mappings.mapMethodName("intermediary", dotOwnerName, methodName, methodDescriptor);

        returnsExpandedLong = root.get("returns_pos").getAsBoolean();

        boolean isStatic = false;
        JsonElement staticElement = root.get("static");
        if(staticElement != null) isStatic = staticElement.getAsBoolean();
        int offset = isStatic ? 0 : 1;

        this.isStatic = isStatic;

        expandedArgumentIndices = new HashSet<>();
        root.get("blockpos_args").getAsJsonArray().forEach((e) -> {
            expandedArgumentIndices.add(e.getAsInt() + offset);
        });

        numArgs = getNumArgs(methodDescriptor) + offset;

        newDesc = LongPosTransformer.modifyDescriptor(originalDescriptor, expandedArgumentIndices, isStatic, false);

        String newOwner = originalOwner;
        String newName = originalName;

        JsonElement newNameElement = root.get("rename");
        if(newNameElement != null){
            String newNameData = newNameElement.getAsString();
            String[] ownerAndName = newNameData.split("#");
            if(ownerAndName.length == 1){
                newName = ownerAndName[0];
            }else{
                newName = ownerAndName[1];
                newOwner = ownerAndName[0];
            }
        }

        this.newName = newName;
        this.newOwner = newOwner;

        if(returnsExpandedLong){
            JsonArray expansions = root.get("expansion").getAsJsonArray();
            for(int i = 0; i < 3; i++){
                this.expansions[i] = new JSONBytecodeFactory(expansions.get(i).getAsJsonArray(), mappings);
            }
        }

        JsonElement sepArgsElement = root.get("sep_args");
        if(sepArgsElement != null){
            sepArgsElement.getAsJsonArray().forEach((e) -> {
                JsonArray arr = e.getAsJsonArray();
                Integer[] args = new Integer[3];

                for(int i = 0; i < 3; i++) args[i] = arr.get(i).getAsInt() + offset;
                separatedArguments.add(args);
            });
        }
    }

    //ASM doesn't specify a method that does exactly this. However this code is mostly taken from Type.getArgumentAndReturnSizes
    public static int getNumArgs(String methodDescriptor) {
        int numArgs = 0;

        int currentIndex = 1;
        char currentChar = methodDescriptor.charAt(currentIndex);

        while (currentChar != ')'){
            while (methodDescriptor.charAt(currentIndex) == '['){
                currentIndex++;
            }
            if(methodDescriptor.charAt(currentIndex) == 'L'){
                int semicolonOffset = methodDescriptor.indexOf(';', currentIndex);
                currentIndex = Math.max(semicolonOffset, currentIndex);
            }
            currentIndex++;
            numArgs++;
            currentChar = methodDescriptor.charAt(currentIndex);
        }

        return numArgs;
    }

    public static String mapDescriptor(String descriptor, MappingResolver map){
        Type returnType = Type.getReturnType(descriptor);
        Type[] argTypes = Type.getArgumentTypes(descriptor);


        returnType = mapType(returnType, map);
        for(int i = 0; i < argTypes.length; i++){
            argTypes[i] = mapType(argTypes[i], map);
        }

        return Type.getMethodDescriptor(returnType, argTypes);
    }

    public static Type mapType(Type type, MappingResolver map){
        if(!(type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)) return type;
        String name = type.getInternalName().replace('/', '.');

        int start = 0;
        while(name.charAt(start) == '['){
            start++;
        }

        String notArrayTypeName = name.substring(start);
        if(notArrayTypeName.length() == 1){
            return Type.getType(name.substring(0, start) + notArrayTypeName);
        }

        return Type.getType((name.substring(0, start) + "L" + map.mapClassName("intermediary", notArrayTypeName) + ";").replace('.', '/'));
    }

    public boolean returnsPackedBlockPos(){
        return returnsExpandedLong;
    }

    public Set<Integer> getExpandedIndices(){
        return expandedArgumentIndices;
    }

    public boolean hasPackedArguments(){
        return expandedArgumentIndices.size() != 0;
    }

    public int getNumArgs() {
        return numArgs;
    }

    public String getNewOwner() {
        return newOwner;
    }

    public String getNewName() {
        return newName;
    }

    public String getNewDesc() {
        return newDesc;
    }

    public String getOriginalOwner() {
        return originalOwner;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getOriginalDescriptor() {
        return originalDescriptor;
    }

    public String getMethodID(){
        return originalOwner + "#" + originalName + " " + originalDescriptor;
    }

    public InsnList getExpansion(int index){
        return expansions[index].generate();
    }

    public boolean isStatic() {
        return isStatic;
    }

    public List<Integer[]> getSeparatedArguments() {
        return separatedArguments;
    }
}
