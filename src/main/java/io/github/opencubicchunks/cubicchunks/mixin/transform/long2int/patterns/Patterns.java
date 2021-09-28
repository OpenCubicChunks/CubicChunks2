package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.LongPosTransformer;
import io.github.opencubicchunks.cubicchunks.utils.Int3HashSet;

public class Patterns {
    private static final Map<String, Function<Map<String, LongPosTransformer.MethodRemappingInfo>, BytecodePattern>> patterns = new HashMap<>();

    public static BytecodePattern getPattern(String name, Map<String, LongPosTransformer.MethodRemappingInfo> transformedMethods){
        var creator = patterns.get(name);
        if(creator == null) return null;
        return creator.apply(transformedMethods);
    }

    static {
        patterns.put("block_pos_offset", BlockPosOffsetPattern::new);
        patterns.put("expanded_method_remapping", ExpandedMethodRemappingPattern::new);
        patterns.put("max_pos_expansion", MaxPosExpansionPattern::new);
        patterns.put("as_long_expansion", AsLongExpansionPattern::new);

        patterns.put("check_invalid_pos", (t) -> new CheckInvalidPosPattern());
        patterns.put("block_pos_unpack", (t) -> new BlockPosUnpackingPattern());
        patterns.put("packed_inequality", (t) -> new PackedInequalityPattern());
    }
}
