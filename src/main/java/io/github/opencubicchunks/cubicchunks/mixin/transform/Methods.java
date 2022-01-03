package io.github.opencubicchunks.cubicchunks.mixin.transform;

import java.util.function.LongPredicate;

import io.github.opencubicchunks.cubicchunks.utils.Int3List;
import io.github.opencubicchunks.cubicchunks.utils.XYZPredicate;
import net.minecraft.core.BlockPos;

//These are static methods that are used in some transformed classes (right now only DynamicGraphMinFixedPoint)
public class Methods {
    public static void removeIfMethod(XYZPredicate condition, Int3List list, int x, int y, int z, int value){
        if(condition.test(x, y, z)){
            list.add(x, y, z);
        }
    }
    public static XYZPredicate toXYZ(LongPredicate predicate){
        return (x, y, z) -> predicate.test(BlockPos.asLong(x, y, z));
    }
}
