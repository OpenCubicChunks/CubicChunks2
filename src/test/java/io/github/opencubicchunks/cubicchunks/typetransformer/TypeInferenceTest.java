package io.github.opencubicchunks.cubicchunks.typetransformer;

import java.util.Map;

import io.github.opencubicchunks.cubicchunks.mixin.transform.MainTransformer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.TypeTransformer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.AnalysisResults;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformSubtype;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.Config;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.ASMUtil;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.BlockLightSectionStorage;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import net.minecraft.world.level.lighting.LayerLightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public class TypeInferenceTest {
    public static final Config CONFIG = MainTransformer.TRANSFORM_CONFIG;
    public static final ClassCheck[] CHECKS_TO_PERFORM = {
        new ClassCheck(
            BlockLightEngine.class,

            MethodCheck.of("getLightEmission", "blockpos"),
            MethodCheck.of("computeLevelFromNeighbor", "blockpos", "blockpos"),
            MethodCheck.of("checkNeighborsAfterUpdate", "blockpos"),
            MethodCheck.of("getComputedLevel", "blockpos", "blockpos"),
            MethodCheck.of("onBlockEmissionIncrease")
        ),

        new ClassCheck(
            SkyLightEngine.class,

            MethodCheck.of("computeLevelFromNeighbor", "blockpos", "blockpos"),
            MethodCheck.of("checkNeighborsAfterUpdate", "blockpos"),
            MethodCheck.of("getComputedLevel", "blockpos", "blockpos"),
            MethodCheck.of("checkNode", "blockpos")
        ),

        new ClassCheck(
            LayerLightEngine.class,

            MethodCheck.of("checkNode", "blockpos"),
            MethodCheck.of("getChunk"),
            MethodCheck.of("getStateAndOpacity", "blockpos"),
            MethodCheck.of("getShape", null, "blockpos"),
            MethodCheck.of("getLightBlockInto"),
            MethodCheck.of("isSource", "blockpos"),
            MethodCheck.of("getComputedLevel", "blockpos", "blockpos"),
            MethodCheck.ofWithDesc("getLevel", "(J)I", "blockpos"),
            MethodCheck.ofWithDesc("getLevel", "(Lnet/minecraft/world/level/chunk/DataLayer;J)I", null, "blockpos"),
            MethodCheck.of("setLevel", "blockpos"),
            MethodCheck.of("computeLevelFromNeighbor", "blockpos", "blockpos"),
            MethodCheck.of("runUpdates"),
            MethodCheck.of("queueSectionData"),
            MethodCheck.of("getDataLayerData"),
            MethodCheck.of("getLightValue"),
            MethodCheck.of("checkBlock"),
            MethodCheck.of("onBlockEmissionIncrease"),
            MethodCheck.of("updateSectionStatus"),
            MethodCheck.of("enableLightSources"),
            MethodCheck.of("retainData")
        ),

        new ClassCheck(
            SectionPos.class,

            MethodCheck.of("blockToSection", "blockpos")
        ),

        new ClassCheck(
            LayerLightSectionStorage.class,

            MethodCheck.of("storingLightForSection"),
            MethodCheck.ofWithDesc("getDataLayer", "(JZ)Lnet/minecraft/world/level/chunk/DataLayer;"),
            MethodCheck.ofWithDesc("getDataLayer", "(Lnet/minecraft/world/level/lighting/DataLayerStorageMap;J)Lnet/minecraft/world/level/chunk/DataLayer;"),
            MethodCheck.of("getDataLayerData"),
            MethodCheck.of("getLightValue", "blockpos"),
            MethodCheck.of("getStoredLevel", "blockpos"),
            MethodCheck.of("setStoredLevel", "blockpos"),
            MethodCheck.of("getLevel"),
            MethodCheck.of("getLevelFromSource"),
            MethodCheck.of("setLevel"),
            MethodCheck.of("createDataLayer"),
            MethodCheck.of("clearQueuedSectionBlocks"),
            MethodCheck.of("markNewInconsistencies"),
            MethodCheck.of("checkEdgesForSection"),
            MethodCheck.of("onNodeAdded"),
            MethodCheck.of("onNodeRemoved"),
            MethodCheck.of("enableLightSources"),
            MethodCheck.of("retainData"),
            MethodCheck.of("queueSectionData"),
            MethodCheck.of("updateSectionStatus")
        ),

        new ClassCheck(
            SkyLightSectionStorage.class,

            MethodCheck.ofWithDesc("getLightValue", "(J)I", "blockpos"),
            MethodCheck.ofWithDesc("getLightValue", "(JZ)I", "blockpos"),
            MethodCheck.of("onNodeAdded"),
            MethodCheck.of("queueRemoveSource"),
            MethodCheck.of("queueAddSource"),
            MethodCheck.of("onNodeRemoved"),
            MethodCheck.of("enableLightSources"),
            MethodCheck.of("createDataLayer"),
            MethodCheck.of("repeatFirstLayer"),
            MethodCheck.of("markNewInconsistencies"),
            MethodCheck.of("hasSectionsBelow"),
            MethodCheck.of("isAboveData"),
            MethodCheck.of("lightOnInSection")
        ),

        new ClassCheck(
            BlockLightSectionStorage.class,

            MethodCheck.of("getLightValue", "blockpos")
        ),

        new ClassCheck(
            DynamicGraphMinFixedPoint.class,

            MethodCheck.of("getKey"),
            MethodCheck.of("checkFirstQueuedLevel"),
            MethodCheck.of("removeFromQueue", "blockpos"),
            MethodCheck.of("removeIf", "blockpos predicate"),
            MethodCheck.of("dequeue", "blockpos"),
            MethodCheck.of("enqueue", "blockpos"),
            MethodCheck.of("checkNode", "blockpos"),
            MethodCheck.ofWithDesc("checkEdge", "(JJIZ)V", "blockpos", "blockpos"),
            MethodCheck.ofWithDesc("checkEdge", "(JJIIIZ)V", "blockpos", "blockpos"),
            MethodCheck.of("checkNeighbor", "blockpos", "blockpos"),
            MethodCheck.of("runUpdates"),

            MethodCheck.of("isSource", "blockpos"),
            MethodCheck.of("getComputedLevel", "blockpos", "blockpos"),
            MethodCheck.of("checkNeighborsAfterUpdate", "blockpos"),
            MethodCheck.of("getLevel", "blockpos"),
            MethodCheck.of("setLevel", "blockpos"),
            MethodCheck.of("computeLevelFromNeighbor", "blockpos", "blockpos")
        )
    };

    @Test
    public void runTests() {
        for (ClassCheck check : CHECKS_TO_PERFORM) {
            Map<MethodID, AnalysisResults> analysisResults = getAnalysisResults(check.clazz);

            for (MethodCheck methodCheck : check.methods) {
                AnalysisResults results = methodCheck.findWanted(analysisResults);
                if (results == null) {
                    throw new RuntimeException("No results for " + methodCheck.finder);
                }

                if (!methodCheck.check(results)) {
                    StringBuilder error = new StringBuilder();

                    error.append("Unexpected type inference results for ").append(results.methodNode().name).append(" ").append(results.methodNode().desc);
                    error.append("\n");
                    error.append("Expected: \n\t[ ");

                    int numArgs = Type.getArgumentTypes(results.methodNode().desc).length;
                    boolean isStatic = ASMUtil.isStatic(results.methodNode());

                    int i;
                    for (i = 0; i < methodCheck.expected.length; i++) {
                        if (i > 0) {
                            error.append(", ");
                        }

                        error.append(methodCheck.expected[i]);
                    }

                    for (; i < numArgs; i++) {
                        if (i > 0) {
                            error.append(", ");
                        }

                        error.append(TransformSubtype.createDefault(null));
                    }

                    error.append(" ]\n\nActual: \n\t[ ");

                    boolean start = true;
                    for (i = isStatic ? 0 : 1; i < results.getArgTypes().length; i++) {
                        if (!start) {
                            error.append(", ");
                        }
                        start = false;

                        error.append(results.getArgTypes()[i]);
                    }

                    error.append(" ]");

                    throw new AssertionError(error.toString());
                }
            }
        }
    }

    private Map<MethodID, AnalysisResults> getAnalysisResults(Class<?> clazz) {
        ClassNode classNode = ASMUtil.loadClassNode(clazz);

        TypeTransformer typeTransformer = new TypeTransformer(CONFIG, classNode, false);
        typeTransformer.analyzeAllMethods();

        return typeTransformer.getAnalysisResults();
    }

    private static record ClassCheck(Class<?> clazz, MethodCheck... methods) {

    }

    private static record MethodCheck(ASMUtil.MethodCondition finder, TransformSubtype... expected) {
        public AnalysisResults findWanted(Map<MethodID, AnalysisResults> results) {
            for (Map.Entry<MethodID, AnalysisResults> entry : results.entrySet()) {
                if (finder.testMethodID(entry.getKey())) {
                    return entry.getValue();
                }
            }

            return null;
        }

        public boolean check(AnalysisResults results) {
            TransformSubtype[] args = results.getArgTypes();

            int argsIndex = ASMUtil.isStatic(results.methodNode()) ? 0 : 1;

            for (int i = 0; i < expected.length; i++, argsIndex++) {
                if (!expected[i].equals(args[argsIndex])) {
                    return false;
                }
            }

            //Check the rest of args are default types
            for (int i = argsIndex; i < args.length; i++) {
                if (args[i].getTransformType() != null) {
                    return false;
                }
            }

            return true;
        }

        public static MethodCheck of(String methodName, String... types) {
            ASMUtil.MethodCondition finder = new ASMUtil.MethodCondition(methodName, null);

            TransformSubtype[] expected = new TransformSubtype[types.length];

            for (int i = 0; i < types.length; i++) {
                if (types[i] == null) {
                    expected[i] = TransformSubtype.createDefault(Type.VOID_TYPE);
                    continue;
                }

                expected[i] = TransformSubtype.fromString(types[i], CONFIG.getTypes());
            }

            return new MethodCheck(finder, expected);
        }

        public static MethodCheck ofWithDesc(String methodName, String desc, String... types) {
            ASMUtil.MethodCondition finder = new ASMUtil.MethodCondition(methodName, desc);

            TransformSubtype[] expected = new TransformSubtype[types.length];

            for (int i = 0; i < types.length; i++) {
                if (types[i] == null) {
                    expected[i] = TransformSubtype.createDefault(Type.VOID_TYPE);
                    continue;
                }

                expected[i] = TransformSubtype.fromString(types[i], CONFIG.getTypes());
            }

            return new MethodCheck(finder, expected);
        }
    }
}
