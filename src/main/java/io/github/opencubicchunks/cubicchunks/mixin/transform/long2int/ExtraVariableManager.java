package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int;

import java.util.ArrayList;
import java.util.List;

public class ExtraVariableManager {
    private final int minimumIndex;
    private final List<List<InsnRange>> variableLifespans = new ArrayList<>();

    public ExtraVariableManager(int minimumIndex) {
        this.minimumIndex = minimumIndex;
    }

    public int getExtraVariable(int startIndex, int endIndex){
        int var = 0;
        while(!canBePlacedIn(var, startIndex, endIndex)){
            var++;
        }

        variableLifespans.get(var).add(new InsnRange(startIndex, endIndex));

        return minimumIndex + var;
    }

    public int getExtraVariableForComputationalTypeTwo(int startIndex, int endIndex){
        int var = 0;
        boolean canBePlacedInCurrent = canBePlacedIn(var, startIndex, endIndex);
        boolean canBePlacedInNext = canBePlacedIn(var + 1, startIndex, endIndex);

        while (!(canBePlacedInCurrent && canBePlacedInNext)){
            canBePlacedInCurrent = canBePlacedInNext;
            canBePlacedInNext = canBePlacedIn(var++ + 1, startIndex, endIndex);
        }

        InsnRange range = new InsnRange(startIndex, endIndex);

        variableLifespans.get(var).add(range);
        variableLifespans.get(var + 1).add(range);

        return minimumIndex + var;
    }

    private boolean canBePlacedIn(int variableIndex, int startIndex, int endIndex){
        List<InsnRange> ranges;
        if(variableIndex >= variableLifespans.size()){
            ranges = new ArrayList<>();
            variableLifespans.add(ranges);
        }else{
            ranges = variableLifespans.get(variableIndex);
        }

        for(InsnRange range: ranges){
            if(range.intersects(startIndex, endIndex)){
                return false;
            }
        }

        return true;
    }

    private static record InsnRange(int startIndex, int endIndex){
        public boolean intersects(int otherStart, int otherEnd){
            return !(otherEnd < startIndex || endIndex < otherStart);
        }
    }

    public int getNumLocals(){
        return minimumIndex + variableLifespans.size();
    }
}
