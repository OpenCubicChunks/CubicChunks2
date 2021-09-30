package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalVariableMapper {
    private final List<Integer> transformedParameters = new ArrayList<>();
    //private final Map<Integer, Integer> parameterMapper = new HashMap<>();

    public void addTransformedVariable(int index){
        transformedParameters.add(index);
    }

    public int mapLocalVariable(int index){
        int mappedIndex = index;
        for(int transformed : transformedParameters){
            if(index > transformed) mappedIndex++;
        }

        return mappedIndex;
    }

    public boolean isATransformedLong(int index){
        return transformedParameters.contains(index);
    }

    public boolean isARemappedTransformedLong(int index){
        for(int unmappedIndex : transformedParameters){
            if(mapLocalVariable(unmappedIndex) == index) return true;
        }
        return false;
    }
    public void generate(){
        Collections.sort(transformedParameters);
    }


    public int getLocalVariableOffset() {
        return transformedParameters.size();
    }
}
