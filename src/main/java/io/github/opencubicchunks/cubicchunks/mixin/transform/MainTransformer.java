package io.github.opencubicchunks.cubicchunks.mixin.transform;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.ARRAY;
import static org.objectweb.asm.Type.OBJECT;
import static org.objectweb.asm.Type.getObjectType;
import static org.objectweb.asm.Type.getType;
import static org.objectweb.asm.commons.Method.getMethod;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.CubicChunksSynthetic;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.LongPosTransformer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.TransformInfo;
import io.github.opencubicchunks.cubicchunks.utils.Int3List;
import io.github.opencubicchunks.cubicchunks.utils.XYZPredicate;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.noise.module.modifier.Abs;

import static io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.bytecodegen.functional.BytecodeGen.*;

public class MainTransformer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final boolean IS_DEV = FabricLoader.getInstance().isDevelopmentEnvironment();

    private static final String CC = "io/github/opencubicchunks/cubicchunks/";

    public static void transformChunkHolder(ClassNode targetClass) {
        Map<ClassMethod, String> vanillaToCubic = new HashMap<>();
        vanillaToCubic.put(new ClassMethod(getObjectType("net/minecraft/class_3193"), // ChunkHolder
                getMethod("void <init>("
                    + "net.minecraft.class_1923, " // ChunkPos
                    + "int, net.minecraft.class_5539, " // LevelHeightAccessor
                    + "net.minecraft.class_3568, " // LightingProvider
                    + "net.minecraft.class_3193$class_3896, " // ChunkHolder$LevelChangeListener
                    + "net.minecraft.class_3193$class_3897)")), // ChunkHolder$PlayerProvider
            "<init>");
        vanillaToCubic.put(new ClassMethod(getObjectType("net/minecraft/class_3193"), // ChunkHolder
                getMethod("void method_14007(net.minecraft.class_3898, java.util.concurrent.Executor)")), // updateFutures(ChunkMap)
            "updateCubeFutures");

        Map<ClassMethod, String> methods = new HashMap<>();
        methods.put(new ClassMethod(
            getObjectType("net/minecraft/class_3193"), // ChunkHolder
            getMethod("net.minecraft.class_2806 method_14011(int)") // ChunkStatus getStatus(int)
        ), "getCubeStatus");
        methods.put(new ClassMethod(
            getObjectType("net/minecraft/class_3898"), // ChunkMap
            getMethod("java.util.concurrent.CompletableFuture method_31417(net.minecraft.class_3193)") // prepareAccessibleChunk(ChunkHolder)
        ), "prepareAccessibleCube");
        methods.put(new ClassMethod(
            getObjectType("net/minecraft/class_3898"), // ChunkMap
            getMethod("java.util.concurrent.CompletableFuture method_17235(net.minecraft.class_3193)") // prepareTickingChunk(ChunkHolder)
        ), "prepareTickingCube");
        methods.put(new ClassMethod(
            getObjectType("net/minecraft/class_3898"), // ChunkMap
            getMethod("java.util.concurrent.CompletableFuture method_17247(net.minecraft.class_1923)") // prepareEntityTickingChunk(ChunkPos)
        ), "prepareEntityTickingCube");
        methods.put(new ClassMethod(
            getObjectType("net/minecraft/class_3898"), // ChunkMap
            getMethod("java.util.concurrent.CompletableFuture method_20576(net.minecraft.class_2818)") // packTicks(LevelChunk)
        ), "packCubeTicks");
        methods.put(new ClassMethod(
            getObjectType("net/minecraft/class_3193$class_3896"), // ChunkHolder$LevelChangeListener
            getMethod("void method_17209(" // onLevelChange
                + "net.minecraft.class_1923, " // ChunkPos
                + "java.util.function.IntSupplier, int, java.util.function.IntConsumer)")
        ), "onCubeLevelChange");

        Map<ClassField, String> fields = new HashMap<>();
        fields.put(new ClassField(
                "net/minecraft/class_3898", // net/minecraft/server/level/ChunkMap
                "field_18239", "I"), // MAX_CHUNK_DISTANCE
            "MAX_CUBE_DISTANCE");
        fields.put(new ClassField("net/minecraft/class_3193", // ChunkHolder
            "field_13864", "Lnet/minecraft/class_1923;"), "cubePos"); // pos

        Map<Type, Type> types = new HashMap<>();
        types.put(getObjectType("net/minecraft/class_1923"), // ChunkPos
            getObjectType("io/github/opencubicchunks/cubicchunks/world/level/CubePos"));
        types.put(getObjectType("net/minecraft/class_2818"), // LevelChunk
            getObjectType("io/github/opencubicchunks/cubicchunks/world/level/chunk/LevelCube"));
        types.put(getObjectType("net/minecraft/class_3193$1"), // ChunkHolder$1
            getObjectType("io/github/opencubicchunks/cubicchunks/server/level/CubeHolder$CubeLoadingError"));

        vanillaToCubic.forEach((old, newName) -> cloneAndApplyRedirects(targetClass, old, newName, methods, fields, types));
    }

    public static void transformChunkManager(ClassNode targetClass) {
        Map<ClassMethod, String> vanillaToCubic = new HashMap<>();
        Set<String> makeSyntheticAccessor = new HashSet<>();

        makeSyntheticAccessor.add("updateCubeScheduling");
        vanillaToCubic.put(new ClassMethod(getObjectType("net/minecraft/class_3898"), // ChunkMap
            getMethod("net.minecraft.class_3193 " // ChunkHolder
                + "method_17217(long, int, " // updateChunkScheduling
                + "net.minecraft.class_3193, int)" // ChunkHolder
            )), "updateCubeScheduling");
        vanillaToCubic.put(new ClassMethod(getObjectType("net/minecraft/class_3898"), //ChunkMap
            getMethod("boolean "
                + "method_27055(" // isExistingChunkFull
                + "net.minecraft.class_1923)" //ChunkPos
            )), "isExistingCubeFull");
        vanillaToCubic.put(new ClassMethod(getObjectType("net/minecraft/class_3898"), //ChunkMap
            getMethod("void "
                + "method_20605(" // processUnloads
                + "java.util.function.BooleanSupplier)" //ChunkPos
            )), "processCubeUnloads");
        vanillaToCubic.put(new ClassMethod(getObjectType("net/minecraft/class_3898"), //ChunkMap
            getMethod("void "
                + "method_20458(long, " // scheduleUnload
                + "net.minecraft.class_3193)" // ChunkHolder
            )), "scheduleCubeUnload");
        vanillaToCubic.put(new ClassMethod(getObjectType("net/minecraft/class_3898"), //ChunkMap
            getMethod("void method_17242(boolean)")), "saveAllCubes"); // saveAllChunks
        vanillaToCubic.put(new ClassMethod(getObjectType("net/minecraft/class_3898"), // ChunkMap
            getMethod("boolean method_17228(" // save
                + "net.minecraft.class_2791)" // ChunkAccess
            )), "cubeSave");

        Map<ClassMethod, String> methodRedirects = new HashMap<>();

        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_3898"), // ChunkMap
            getMethod("net.minecraft.class_2487 " // CompundTag
                + "method_17979(" // readChunk
                + "net.minecraft.class_1923)" // ChunkPos
            )), "readCubeNBT");
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_3898"), // ChunkMap
            getMethod("void "
                + "method_27054(" // markPositionReplaceable
                + "net.minecraft.class_1923)" // ChunkPos
            )), "markCubePositionReplaceable");
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_3898"), // ChunkMap
            getMethod("byte "
                + "method_27053(" // markPosition
                + "net.minecraft.class_1923, " // chunkPos
                + "net.minecraft.class_2806$class_2808)" // ChunkStatus.ChunkType
            )), "markCubePosition");
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_1923"), // ChunkPos
            getMethod("long method_8324()" // toLong
            )), "asLong");
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_3898"), // ChunkMap
            getMethod("void method_20605(java.util.function.BooleanSupplier)")), "processCubeUnloads"); // processUnloads
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_3898"), // ChunkMap
            getMethod("void method_23697()"), // flushWorker
            getObjectType("net/minecraft/class_3977")), "flushCubeWorker"); // ChunkStorage
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_3898"), // ChunkMap
            getMethod("void method_17910(" // write
                + "net.minecraft.class_1923, " // ChunkPos
                + "net.minecraft.class_2487)" // CompoundTag
            ), getObjectType("net/minecraft/class_3977")), "writeCube"); // ChunkStorage
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_3193"), // ChunkHolder
            getMethod("java.util.concurrent.CompletableFuture "
                + "method_14000()"  // getChunkToSave
            )), "getCubeToSave");
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_3193"), // ChunkHolder
            getMethod("net.minecraft.class_1923 " // ChunkPos
                + "method_13994()"  // getPos
            )), "getCubePos");
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_2818"), // LevelChunk
            getMethod("void method_12226(boolean)" // setLoaded
            )), "setLoaded");
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_2791"), // ChunkAccess
            getMethod("net.minecraft.class_1923 " // ChunkPos
                + "method_12004()" // getPos
            )), "getCubePos");
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_2791"), // ChunkAccess
            getMethod("void method_12008(boolean)" // setUnsaved
            )), "setDirty");
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_2791"), // ChunkAccess
            getMethod("net.minecraft.class_2806 " // ChunkStatus
                + "method_12009()" // getStatus
            )), "getCubeStatus");
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_2791"), // ChunkAccess
            getMethod("java.util.Map method_12016()")), "getAllCubeStructureStarts"); // getAllStarts

        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_3218"), // ServerLevel
            getMethod("void method_18764(" // unload
                + "net.minecraft.class_2818)" // LevelChunk
            )), "onCubeUnloading");
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_3227"), // ThreadedLevelLightEngine
            getMethod("void method_20386(" // updateChunkStatus
                + "net.minecraft.class_1923)" // ChunkPos
            )), "setCubeStatusEmpty");
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_3949"), // ChunkProgressListener
            getMethod("void method_17670(" // onStatusChange
                + "net.minecraft.class_1923, " // ChunkPos
                + "net.minecraft.class_2806)" // ChunkStatus
            )), "onCubeStatusChange");
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_2852"), // ChunkSerializer
            getMethod("net.minecraft.class_2487 " // CompoundTag
                + "method_12410(" // write
                + "net.minecraft.class_3218, " // ServerLevel
                + "net.minecraft.class_2791)" // ChunkAccess
            )), "write");
        methodRedirects.putAll(vanillaToCubic);

        Map<ClassField, String> fieldRedirects = new HashMap<>();
        fieldRedirects.put(new ClassField(
                "net/minecraft/class_3898", // net/minecraft/server/level/ChunkMap
                "field_17221", "Lit/unimi/dsi/fastutil/longs/LongSet;"), // toDrop
            "cubesToDrop");
        fieldRedirects.put(new ClassField(
                "net/minecraft/class_3898", // net/minecraft/server/level/ChunkMap
                "field_19343", "Ljava/util/Queue;"), // unloadQueue
            "cubeUnloadQueue");
        fieldRedirects.put(new ClassField(
                "net/minecraft/class_3898", // net/minecraft/server/level/ChunkMap
                "field_18807", "Lit/unimi/dsi/fastutil/longs/Long2ObjectLinkedOpenHashMap;"), // pendingUnloads
            "pendingCubeUnloads");
        fieldRedirects.put(new ClassField(
                "net/minecraft/class_3898", // net/minecraft/server/level/ChunkMap
                "field_17223", "Lnet/minecraft/class_3900;"), // ChunkTaskPriorityQueueSorter queueSorter
            "cubeQueueSorter");
        fieldRedirects.put(new ClassField(
                "net/minecraft/class_3898", // net/minecraft/server/level/ChunkMap
                "field_17213", "Lit/unimi/dsi/fastutil/longs/Long2ObjectLinkedOpenHashMap;"), // updatingChunkMap
            "updatingCubeMap");
        fieldRedirects.put(new ClassField(
                "net/minecraft/class_3898", // net/minecraft/server/level/ChunkMap
                "field_17220", "Lit/unimi/dsi/fastutil/longs/Long2ObjectLinkedOpenHashMap;"), // visibleChunkMap
            "visibleCubeMap");
        fieldRedirects.put(new ClassField(
                getObjectType("net/minecraft/class_3898"), // net/minecraft/server/level/ChunkMap
                "field_18239", Type.INT_TYPE), // MAX_CHUNK_DISTANCE
            "MAX_CUBE_DISTANCE");
        fieldRedirects.put(new ClassField(
                "net/minecraft/class_3898", // ChunkMap
                "field_23786", // chunkTypeCache
                "Lit/unimi/dsi/fastutil/longs/Long2ByteMap;"),
            "cubeTypeCache");
        fieldRedirects.put(new ClassField(
                "net/minecraft/class_3898", // ChunkMap
                "field_18807", // pendingUnloads
                "Lit/unimi/dsi/fastutil/longs/Long2ObjectLinkedOpenHashMap;"),
            "pendingCubeUnloads");
        fieldRedirects.put(new ClassField(
                "net/minecraft/class_3898", // ChunkMap
                "field_18307", // entitiesInLevel
                "Lit/unimi/dsi/fastutil/longs/LongSet;"),
            "cubeEntitiesInLevel");

        Map<Type, Type> typeRedirects = new HashMap<>();
        typeRedirects.put(getObjectType("net/minecraft/class_1923"), // ChunkPos
            getObjectType("io/github/opencubicchunks/cubicchunks/world/level/CubePos"));
        // TODO: generate that class at runtime? transform and duplicate?
        typeRedirects.put(getObjectType("net/minecraft/class_3900"), // ChunkTaskPriorityQueueSorter
            getObjectType("io/github/opencubicchunks/cubicchunks/server/level/CubeTaskPriorityQueueSorter"));
        typeRedirects.put(getObjectType("net/minecraft/class_2818"), // LevelChunk
            getObjectType("io/github/opencubicchunks/cubicchunks/world/level/chunk/LevelCube"));
        typeRedirects.put(getObjectType("net/minecraft/class_2791"), // ChunkAccess
            getObjectType("io/github/opencubicchunks/cubicchunks/world/level/chunk/CubeAccess"));
        typeRedirects.put(getObjectType("net/minecraft/class_2821"), // ImposterProtoChunk
            getObjectType("io/github/opencubicchunks/cubicchunks/world/level/chunk/ImposterProtoCube"));
        typeRedirects.put(getObjectType("net/minecraft/class_2852"), // ChunkSerializer
            getObjectType("io/github/opencubicchunks/cubicchunks/world/storage/CubeSerializer"));
        vanillaToCubic.forEach((old, newName) -> {
            MethodNode newMethod = cloneAndApplyRedirects(targetClass, old, newName, methodRedirects, fieldRedirects, typeRedirects);
            if (makeSyntheticAccessor.contains(newName)) {
                makeStaticSyntheticAccessor(targetClass, newMethod);
            }
        });
    }

    public static void transformProxyTicketManager(ClassNode targetClass) {
        final ClassMethod setChunkLevel = new ClassMethod(getObjectType("net/minecraft/class_3204"),
            getMethod("net.minecraft.class_3193 " // ChunkHolder
                + "method_14053(long, int, " // updateChunkScheduling
                + "net.minecraft.class_3193, int)")); // ChunkHolder
        final String updateCubeScheduling = "updateCubeScheduling";

        Map<ClassMethod, String> methodRedirects = new HashMap<>();
        methodRedirects.put(// synthetic accessor for method_14053 (updateChunkScheduling)
            new ClassMethod(
                getObjectType("net/minecraft/class_3898"), // ChunkMap
                getMethod("net.minecraft.class_3193 " // ChunkHolder
                    + "method_17217(" // access$400
                    + "long, int, "
                    + "net.minecraft.class_3193, int)") // ChunkHolder
            ), updateCubeScheduling);

        Map<ClassField, String> fieldRedirects = new HashMap<>();
        Map<Type, Type> typeRedirects = new HashMap<>();

        cloneAndApplyRedirects(targetClass, setChunkLevel, updateCubeScheduling, methodRedirects, fieldRedirects, typeRedirects);
    }

    public static void transformNaturalSpawner(ClassNode targetClass) {
        Map<ClassMethod, String> vanillaToCubic = new HashMap<>();
        Set<String> makeSyntheticAccessor = new HashSet<>();

        vanillaToCubic.put(new ClassMethod(getObjectType("net/minecraft/class_1948"), // NaturalSpawner
            getMethod("void method_27821(" //spawnForChunk
                + "net.minecraft.class_3218," //ServerLevel
                + " net.minecraft.class_2818," //LevelChunk
                + " net.minecraft.class_1948$class_5262," //NaturalSpawner.SpawnState
                + " boolean, boolean, boolean)")), "spawnForCube");

        vanillaToCubic.put(new ClassMethod(getObjectType("net/minecraft/class_1948"), // NaturalSpawner
            getMethod("void method_8663("
                + "net.minecraft.class_1311,"
                + " net.minecraft.class_3218,"
                + " net.minecraft.class_2818,"
                + " net.minecraft.class_1948$class_5261,"
                + " net.minecraft.class_1948$class_5259)")), "spawnCategoryForCube");

        vanillaToCubic.put(new ClassMethod(getObjectType("net/minecraft/class_1948"), // NaturalSpawner
            getMethod("boolean method_24933("
                + "net.minecraft.class_3218,"
                + " net.minecraft.class_2791,"
                + " net.minecraft.class_2338$class_2339,"
                + " double)")), "isRightDistanceToPlayerAndSpawnPointForCube");

        vanillaToCubic.put(new ClassMethod(getObjectType("net/minecraft/class_1948"), // NaturalSpawner
            getMethod("net.minecraft.class_1948$class_5262 method_27815(" //NaturalSpawner.SpawnState createState
                + "int," //spawningChunkCount
                + " java.lang.Iterable," // entities
                + " net.minecraft.class_1948$class_5260)" // NaturalSpawner.ChunkGetter
            )), "createCubicState");

        Map<ClassMethod, String> methodRedirects = new HashMap<>();
        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_1948"), // NaturalSpawner
            getMethod("void method_8663(" // spawnCategoryForChunk
                + "net.minecraft.class_1311," // MobCategory
                + " net.minecraft.class_3218," // ServerLevel
                + " net.minecraft.class_2818," // LevelChunk
                + " net.minecraft.class_1948$class_5261," // NaturalSpawner.SpawnPredicate
                + " net.minecraft.class_1948$class_5259)") // NaturalSpawner.AfterSpawnCallback
        ), "spawnCategoryForCube");

        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_1948"),
            getMethod("net.minecraft.class_2338" // BlockPos
                + " method_8657(" // getRandomPosWithin
                + "net.minecraft.class_1937," // Level
                + "net.minecraft.class_2818)" // LevelChunk
            )), "getRandomPosWithinCube");

        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_1923"),
                getMethod("long method_8324()")), "asLong"); // toLong

        methodRedirects.put(new ClassMethod(getObjectType("net/minecraft/class_1923"),
            getMethod("long method_8331(int, int)")), "asLong"); // asLong

        Map<ClassField, String> fieldRedirects = new HashMap<>();

        Map<Type, Type> typeRedirects = new HashMap<>();

        typeRedirects.put(getObjectType("net/minecraft/class_1923"), // ChunkPos
            getObjectType("io/github/opencubicchunks/cubicchunks/world/level/CubePos"));

        typeRedirects.put(getObjectType("net/minecraft/class_1948$class_5260"), // ChunkGetter
            getObjectType("io/github/opencubicchunks/cubicchunks/world/CubicNaturalSpawner$CubeGetter"));

        typeRedirects.put(getObjectType("net/minecraft/class_2818"), // LevelChunk
            getObjectType("net/minecraft/class_2791")); // ChunkAccess

        vanillaToCubic.forEach((old, newName) -> {
            MethodNode newMethod = cloneAndApplyRedirects(targetClass, old, newName, methodRedirects, fieldRedirects, typeRedirects);
            if (makeSyntheticAccessor.contains(newName)) {
                makeStaticSyntheticAccessor(targetClass, newMethod);
            }
        });
    }

    public static void transformDynamicGraphMinFixedPoint(ClassNode targetClass) {
        //Add the 3-int abstract methods
        addDynamicGraphAbstractMethods(targetClass);

        targetClass.methods.stream().filter(m -> !m.desc.contains("()") && (m.access & ACC_FINAL) != 0).forEach(
            //Should only target checkNeighbor and runUpdates
            m -> {
                m.access &= ~ACC_FINAL;
            }
        );

        createRemoveIf(targetClass);

        // Change computedLevels and queues to be of type Object, as we use different types for the 3-int light engine;

        changeFieldTypeToObject(targetClass, new ClassField(
                "net/minecraft/class_3554", // DynamicGraphMinFixedPoint
                "field_15784", // computedLevels
                "Lit/unimi/dsi/fastutil/longs/Long2ByteMap;"),
            (methodNode) -> {
                if(isCCSynthetic(methodNode)) {
                    return new Pair<>(getObjectType(CC + "utils/Int3UByteLinkedHashMap"), false);
                }else{
                    return new Pair<>(getObjectType("it/unimi/dsi/fastutil/longs/Long2ByteMap"), true);
                }
            }
            );

        changeFieldTypeToObject(targetClass, new ClassField(
                "net/minecraft/class_3554", // DynamicGraphMinFixedPoint
                "field_15785", // queues
                "[Lit/unimi/dsi/fastutil/longs/LongLinkedOpenHashSet;"),
            (methodNode) -> {
                if(isCCSynthetic(methodNode)) {
                    return new Pair<>(getObjectType("[L" + CC + "utils/LinkedInt3HashSet;"), false);
                }else{
                    return new Pair<>(getObjectType("[Lit/unimi/dsi/fastutil/longs/LongLinkedOpenHashSet;") ,false);
                }
            }
        );

        createFixedPoint3IntConstructor(targetClass);

        //changeFixedPointComputedLevelsTo3Int(targetClass);
        //changeFixedPointQueuesTo3Int(targetClass);
    }

    public static void transformSkyLightEngineSectionStorage(ClassNode targetClass) {
        //Lmao this whole method is probably very dumb
        ClassMethod getLightValue = remapMethod(new ClassMethod(
           getObjectType("net/minecraft/class_3569"),
           getMethod("int method_31931(long, boolean)")
        ));

        ClassNode skyLightEngineSectionStorageMixin = loadClass(getObjectType(CC + "mixin/core/common/level/lighting/MixinSkyLightSectionStorage"));
        MethodNode onGetLightValue = skyLightEngineSectionStorageMixin.methods.stream().filter(m -> m.name.equals("onGetLightValue")).findAny().orElse(null);

        if(onGetLightValue == null) {
            throw new RuntimeException("Could not find onGetLightValue in SkyLightEngineSectionStorageMixin");
        }

        TransformInfo info = new TransformInfo(
            "", List.of(1)
        );

        MethodNode toInject = LongPosTransformer.modifyMethod(onGetLightValue, skyLightEngineSectionStorageMixin, info, null).transformed();

        LabelNode endOfInject = new LabelNode();

        MethodNode injectInto = targetClass.methods.stream().filter(
            m -> m.name.equals(getLightValue.method.getName()) && m.desc.equals("(IIIZ)I")
        ).findAny().orElse(null);

        if(injectInto == null) {
            throw new RuntimeException("Could not find injectInto in SkyLightEngineSectionStorageMixin");
        }

        //Remove CallbackInfoReturnable
        Type[] args = Type.getArgumentTypes(toInject.desc);
        Type returnType = Type.getReturnType(injectInto.desc);
        Type[] newArgs = new Type[args.length - 1];
        System.arraycopy(args, 0, newArgs, 0, newArgs.length);

        int shiftFrom = (toInject.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for(Type arg : newArgs) {
            shiftFrom += arg.getSize();
        }

        AbstractInsnNode node = toInject.instructions.getFirst();
        while(node != null) {
            if(node.getOpcode() == RETURN) {
                if (node.getNext() != null) { //No point in doing this if the next node will be the end of inject
                    toInject.instructions.insertBefore(node, new JumpInsnNode(GOTO, endOfInject));
                }
                AbstractInsnNode temp = node.getNext();
                toInject.instructions.remove(node);
                node = temp;
                continue;
            }else if(node instanceof VarInsnNode var){
                //Removing returnable means there's a free var slot though we remove the loading of the callback returnable to make it easier
                if(var.var == shiftFrom){
                    //Is callback returnable
                    AbstractInsnNode temp = node.getNext();
                    toInject.instructions.remove(node);
                    node = temp;
                    continue;
                }else if(var.var > shiftFrom){
                    //Shift all the vars down
                    var.var--;
                }
            }else if(node instanceof IincInsnNode iinc){
                if(iinc.var > shiftFrom){
                    //Shift all the vars down
                    iinc.var--;
                }
            }else if(node instanceof FieldInsnNode field){
                if(field.owner.equals(skyLightEngineSectionStorageMixin.name)){
                    //Change field to new class
                    field.owner = targetClass.name;
                }
            }else if(node instanceof MethodInsnNode method){
                if(method.owner.equals(skyLightEngineSectionStorageMixin.name)){
                    //Change method to new class
                    method.owner = targetClass.name;
                }else if(
                    (
                        method.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable")
                    ) && method.name.equals("setReturnValue")
                ){
                    //Because the injection is inline, we need to actually return
                    if(returnType.getSort() != OBJECT){
                        //Type will have been boxed so we remove the boxing step
                        AbstractInsnNode prev = node.getPrevious();
                        boolean removedUnboxing = false;
                        if(prev instanceof MethodInsnNode methodCall){
                            String owner = methodCall.owner;
                            String name = methodCall.name;
                            String desc = methodCall.desc;

                            if(
                                (
                                    desc.startsWith("(I)")
                                    || desc.startsWith("(J)")
                                    || desc.startsWith("(F)")
                                    || desc.startsWith("(D)")
                                    || desc.startsWith("(S)")
                                    || desc.startsWith("(B)")
                                    || desc.startsWith("(C)")
                                    || desc.startsWith("(Z)")
                                )
                                && name.equals("valueOf")
                                && (
                                    owner.equals("java/lang/Integer")
                                    || owner.equals("java/lang/Long")
                                    || owner.equals("java/lang/Short")
                                    || owner.equals("java/lang/Byte")
                                    || owner.equals("java/lang/Character")
                                    || owner.equals("java/lang/Float")
                                    || owner.equals("java/lang/Double")
                                    )
                            ){
                                //Remove the unboxing step
                                removedUnboxing = true;
                                toInject.instructions.remove(prev);
                            }
                        }

                        if(!removedUnboxing){
                            //We need to unbox the return value
                            String owner;
                            String desc;
                            switch (returnType.getSort()) {
                                case Type.INT:
                                    owner = "java/lang/Integer";
                                    desc = "(I)Ljava/lang/Integer;";
                                    break;
                                case Type.LONG:
                                    owner = "java/lang/Long";
                                    desc = "(J)Ljava/lang/Long;";
                                    break;
                                case Type.SHORT:
                                    owner = "java/lang/Short";
                                    desc = "(S)Ljava/lang/Short;";
                                    break;
                                case Type.BYTE:
                                    owner = "java/lang/Byte";
                                    desc = "(B)Ljava/lang/Byte;";
                                    break;
                                case Type.CHAR:
                                    owner = "java/lang/Character";
                                    desc = "(C)Ljava/lang/Character;";
                                    break;
                                case Type.FLOAT:
                                    owner = "java/lang/Float";
                                    desc = "(F)Ljava/lang/Float;";
                                    break;
                                case Type.DOUBLE:
                                    owner = "java/lang/Double";
                                    desc = "(D)Ljava/lang/Double;";
                                    break;
                                case Type.BOOLEAN:
                                    owner = "java/lang/Boolean";
                                    desc = "(Z)Ljava/lang/Boolean;";
                                    break;
                                default:
                                    throw new IllegalStateException("Unexpected return type: " + returnType);
                            }

                            //Create the unboxing step
                            MethodInsnNode unboxing = new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                owner,
                                "valueOf",
                                desc
                            );

                            //Insert it
                            toInject.instructions.insert(prev, unboxing);
                        }
                    }

                    toInject.instructions.insertBefore(node, new InsnNode(returnType.getOpcode(IRETURN)));
                    AbstractInsnNode temp = node.getNext();
                    toInject.instructions.remove(node);
                    node = temp;
                    continue;
                }
            }

            node = node.getNext();
        }

        injectInto.instructions.insertBefore(injectInto.instructions.getFirst(), endOfInject);
        injectInto.instructions.insertBefore(endOfInject, toInject.instructions);
    }

    private static void addDynamicGraphAbstractMethods(ClassNode targetClass) {
        ClassMethod isSource = remapMethod(new ClassMethod(
            getObjectType("net/minecraft/class_3554"), // DynamicGraphMinFixedPoint
            getMethod("boolean method_15494(long)")
        ));

        ClassMethod getComputedLevel = remapMethod(new ClassMethod(
            getObjectType("net/minecraft/class_3554"), // DynamicGraphMinFixedPoint
            getMethod("int method_15486(long, long, int)")
        ));

        ClassMethod checkNeighborsAfterUpdate = remapMethod(new ClassMethod(
            getObjectType("net/minecraft/class_3554"), // DynamicGraphMinFixedPoint
            getMethod("void method_15487(long, int, boolean)")
        ));

        ClassMethod getLevel = remapMethod(new ClassMethod(
            getObjectType("net/minecraft/class_3554"), // DynamicGraphMinFixedPoint
            getMethod("int method_15480(long)")
        ));

        ClassMethod setLevel = remapMethod(new ClassMethod(
            getObjectType("net/minecraft/class_3554"), // DynamicGraphMinFixedPoint
            getMethod("void method_15485(long, int)")
        ));

        ClassMethod computeLevelFromNeighbor = remapMethod(new ClassMethod(
            getObjectType("net/minecraft/class_3554"), // DynamicGraphMinFixedPoint
            getMethod("int method_15488(long, long, int)")
        ));

        MethodNode isSourceNode = new MethodNode(
            ACC_PUBLIC,
            isSource.method.getName(),
            "(III)Z",
            null, null
        );

        MethodNode getComputedLevelNode = new MethodNode(
            ACC_PUBLIC,
            getComputedLevel.method.getName(),
            "(IIIIIII)I",
            null, null
        );

        MethodNode checkNeighborsAfterUpdateNode = new MethodNode(
            ACC_PUBLIC,
            checkNeighborsAfterUpdate.method.getName(),
            "(IIIIZ)V",
            null, null
        );

        MethodNode getLevelNode = new MethodNode(
            ACC_PUBLIC,
            getLevel.method.getName(),
            "(III)I",
            null, null
        );

        MethodNode setLevelNode = new MethodNode(
            ACC_PUBLIC,
            setLevel.method.getName(),
            "(IIII)V",
            null, null
        );

        MethodNode computeLevelFromNeighborNode = new MethodNode(
            ACC_PUBLIC,
            computeLevelFromNeighbor.method.getName(),
            "(IIIIIII)I",
            null, null
        );

        markCCSynthetic(isSourceNode, isSource.method.getName(), isSource.method.getDescriptor(), "REDIRECT");
        markCCSynthetic(getComputedLevelNode, getComputedLevel.method.getName(), getComputedLevel.method.getDescriptor(), "REDIRECT");
        markCCSynthetic(checkNeighborsAfterUpdateNode, checkNeighborsAfterUpdate.method.getName(), checkNeighborsAfterUpdate.method.getDescriptor(), "REDIRECT");
        markCCSynthetic(getLevelNode, getLevel.method.getName(), getLevel.method.getDescriptor(), "REDIRECT");
        markCCSynthetic(setLevelNode, setLevel.method.getName(), setLevel.method.getDescriptor(), "REDIRECT");
        markCCSynthetic(computeLevelFromNeighborNode, computeLevelFromNeighbor.method.getName(), computeLevelFromNeighbor.method.getDescriptor(), "REDIRECT");

        delegate3Int(isSourceNode, isSource, INVOKEVIRTUAL, false, 0);
        delegate3Int(getComputedLevelNode, getComputedLevel, INVOKEVIRTUAL, false, 0, 1);
        delegate3Int(checkNeighborsAfterUpdateNode, checkNeighborsAfterUpdate, INVOKEVIRTUAL, false, 0);
        delegate3Int(getLevelNode, getLevel, INVOKEVIRTUAL, false, 0);
        delegate3Int(setLevelNode, setLevel, INVOKEVIRTUAL, false, 0);
        delegate3Int(computeLevelFromNeighborNode, computeLevelFromNeighbor, INVOKEVIRTUAL, false, 0, 1);

        targetClass.methods.add(isSourceNode);
        targetClass.methods.add(getComputedLevelNode);
        targetClass.methods.add(checkNeighborsAfterUpdateNode);
        targetClass.methods.add(getLevelNode);
        targetClass.methods.add(setLevelNode);
        targetClass.methods.add(computeLevelFromNeighborNode);
    }

    private static void delegate3Int(MethodVisitor newMethod, ClassMethod currMethod, int opcode, boolean isStatic, int... expandedArgs) {
        ClassMethod blockPosAsLong = remapMethod(new ClassMethod(
            getObjectType("net/minecraft/class_2338"), // BlockPos
            getMethod("long method_10064(int, int, int)")
        ));

        Arrays.sort(expandedArgs);

        String descriptor = currMethod.method.getDescriptor();
        Type[] args = Type.getArgumentTypes(descriptor);
        Type returnType = Type.getReturnType(descriptor);

        int[] argIndices = new int[args.length];
        int index = isStatic ? 0 : 1;
        for (int i = 0; i < args.length; i++) {
            argIndices[i] = index;
            index += args[i].getSize();
        }

        int[] shiftedIndices = new int[args.length];
        boolean[] triple = new boolean[args.length];
        index = isStatic ? 0 : 1;
        int argsIndex = 0;
        for (int i = 0; i < args.length; i++) {
            shiftedIndices[i] = index;
            index += args[i].getSize();
            if(argsIndex < expandedArgs.length && expandedArgs[argsIndex] == i) {
                triple[i] = true;
                index++;
                argsIndex++;
            }
        }

        newMethod.visitCode();
        if(!isStatic){
            newMethod.visitVarInsn(ALOAD, 0);
        }
        for (int i = 0; i < args.length; i++) {
            index = shiftedIndices[i];
            if(triple[i]) {
                newMethod.visitVarInsn(ILOAD, index);
                newMethod.visitVarInsn(ILOAD, index + 1);
                newMethod.visitVarInsn(ILOAD, index + 2);
                newMethod.visitMethodInsn(INVOKESTATIC, blockPosAsLong.owner.getInternalName(), blockPosAsLong.method.getName(), blockPosAsLong.method.getDescriptor(), false);
            }else{
                newMethod.visitVarInsn(args[i].getOpcode(ILOAD), index);
            }
        }

        newMethod.visitMethodInsn(opcode, currMethod.owner.getInternalName(), currMethod.method.getName(), currMethod.method.getDescriptor(), opcode == INVOKEINTERFACE);

        if(returnType.getSort() != Type.VOID) {
            newMethod.visitInsn(returnType.getOpcode(IRETURN));
        }else{
            newMethod.visitInsn(RETURN);
        }
    }

    private static void createRemoveIf(ClassNode targetClass) {
        Type dynGraph = remapType(Type.getObjectType("net/minecraft/class_3554")); // DynamicGraphMinFixedPoint

        ClassMethod methodName = remapMethod(new ClassMethod(
            getObjectType("net/minecraft/class_3554"),
            new Method("method_24206", "(Ljava/util/function/LongPredicate;)V"))
        );

        ClassMethod removeFromQueue = remapMethod(
            new ClassMethod(
                getObjectType("net/minecraft/class_3554"),
                new Method("method_15483", "(J)V")
            )
        );

        ClassField computedLevels = remapField(new ClassField(
            "net/minecraft/class_3554",
            "field_15784",
            "Lit/unimi/dsi/fastutil/longs/Long2ByteMap;"
        ));

        MethodNode methodVisitor = new MethodNode(ACC_PUBLIC, methodName.method.getName(), "(Lio/github/opencubicchunks/cubicchunks/utils/XYZPredicate;)V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
        Label label3 = new Label();
        Label label4 = new Label();
        Label label5 = new Label();
        methodVisitor.visitTryCatchBlock(label3, label4, label5, "java/lang/Throwable");
        Label label6 = new Label();
        methodVisitor.visitLabel(label6);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(GETFIELD, dynGraph.getInternalName(), computedLevels.name, "Lio/github/opencubicchunks/cubicchunks/utils/Int3UByteLinkedHashMap;");
        methodVisitor.visitVarInsn(ASTORE, 2);
        Label label7 = new Label();
        methodVisitor.visitLabel(label7);
        methodVisitor.visitTypeInsn(NEW, "io/github/opencubicchunks/cubicchunks/utils/Int3List");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "io/github/opencubicchunks/cubicchunks/utils/Int3List", "<init>", "()V", false);
        methodVisitor.visitVarInsn(ASTORE, 3);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitInvokeDynamicInsn(
            "accept",
            "(Lio/github/opencubicchunks/cubicchunks/utils/XYZPredicate;Lio/github/opencubicchunks/cubicchunks/utils/Int3List;)Lio/github/opencubicchunks/cubicchunks/utils/Int3UByteLinkedHashMap$EntryConsumer;",
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false
            ),
            Type.getType("(IIII)V"),
            new Handle(
                Opcodes.H_INVOKESTATIC,
                CC + "mixin/transorm/MainTransformer",
                "removeIfMethod",
                "(Lio/github/opencubicchunks/cubicchunks/utils/XYZPredicate;Lio/github/opencubicchunks/cubicchunks/utils/Int3List;IIII)V",
                false
            ),
            Type.getType("(IIII)V")
        );
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "io/github/opencubicchunks/cubicchunks/utils/Int3UByteLinkedHashMap", "forEach", "(Lio/github/opencubicchunks/cubicchunks/utils/Int3UByteLinkedHashMap$EntryConsumer;)V", false);
        Label label8 = new Label();
        methodVisitor.visitLabel(label8);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInvokeDynamicInsn(
            "accept",
            "(L" + dynGraph.getInternalName() + ";)Ljava/util/function/LongConsumer;",
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false
            ),
            Type.getType("(J)V"),
            new Handle(
                Opcodes.H_INVOKEVIRTUAL,
                dynGraph.getInternalName(),
                removeFromQueue.method.getName(),
                "(J)V",
                false
            ),
            Type.getType("(J)V"));
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "io/github/opencubicchunks/cubicchunks/utils/Int3List", "forEach", "(Ljava/util/function/LongConsumer;)V", false);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "io/github/opencubicchunks/cubicchunks/utils/Int3List", "close", "()V", false);
        Label label9 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label9);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitVarInsn(ASTORE, 4);
        methodVisitor.visitLabel(label3);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "io/github/opencubicchunks/cubicchunks/utils/Int3List", "close", "()V", false);
        methodVisitor.visitLabel(label4);
        Label label10 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label10);
        methodVisitor.visitLabel(label5);
        methodVisitor.visitVarInsn(ASTORE, 5);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V", false);
        methodVisitor.visitLabel(label10);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitLabel(label9);
        methodVisitor.visitInsn(RETURN);
        Label label11 = new Label();
        methodVisitor.visitLabel(label11);
        methodVisitor.visitLocalVariable("list", "Lio/github/opencubicchunks/cubicchunks/utils/Int3List;", null, label0, label9, 3);
        methodVisitor.visitLocalVariable("this", "L" + dynGraph.getInternalName() + ";", null, label6, label11, 0);
        methodVisitor.visitLocalVariable("condition", "Lio/github/opencubicchunks/cubicchunks/utils/XYZPredicate;", null, label6, label11, 1);
        methodVisitor.visitLocalVariable("levels", "Lio/github/opencubicchunks/cubicchunks/utils/Int3UByteLinkedHashMap;", null, label7, label11, 2);
        methodVisitor.visitMaxs(3, 6);
        methodVisitor.visitEnd();

        markCCSynthetic(methodVisitor, methodName.method.getName(), methodName.method.getDescriptor(), "HARDCODED");
        targetClass.methods.add(methodVisitor);
    }

    public static void removeIfMethod(XYZPredicate condition, Int3List list, int x, int y, int z, int value){
        if(condition.test(x, y, z)){
            list.add(x, y, z);
        }
    }

    private static void createFixedPoint3IntConstructor(ClassNode targetClass){
        //Get original constructor
        MethodNode originalConstructor = targetClass.methods.stream().filter(
            m -> m.name.equals("<init>")
            && m.desc.equals("(III)V")
        ).findAny().orElse(null);

        if(originalConstructor == null){
            throw new IllegalStateException("Could not find original constructor");
        }

        //Create new constructor
        MethodNode constructor = LongPosTransformer.copy(originalConstructor);
        constructor.name = "<init>";
        constructor.desc = "(IIII)V"; //The fourth int is redundant but distinguishes the constructor from the original one

        int shiftFrom = 4;

        AbstractInsnNode[] instructions = constructor.instructions.toArray();
        for(int i = 0; i < instructions.length; i++){
            AbstractInsnNode instruction = instructions[i];
            if(instruction.getOpcode() == NEW){
                TypeInsnNode typeInsnNode = (TypeInsnNode) instruction;
                if(typeInsnNode.desc.endsWith("$2")){ //Kinda dodgy but it works
                    typeInsnNode.desc = CC + "utils/Int3UByteLinkedHashMap";
                }else if(typeInsnNode.desc.endsWith("$1")){
                    typeInsnNode.desc = CC + "utils/LinkedInt3HashSet";
                }
            }else if(instruction.getOpcode() == INVOKESPECIAL){
                MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;
                if(methodInsnNode.name.equals("<init>")){
                    if(methodInsnNode.owner.endsWith("$2")){
                        methodInsnNode.owner = CC + "utils/Int3UByteLinkedHashMap";
                    }else if(methodInsnNode.owner.endsWith("$1")){
                        methodInsnNode.owner = CC + "utils/LinkedInt3HashSet";
                    }else{
                        continue;
                    }
                    methodInsnNode.desc = "()V";

                    //Remove method call arguments
                    for (int j = 1; j <= 4; j++) {
                        constructor.instructions.remove(instructions[i - j]);
                    }
                }
            }else if(instruction.getOpcode() == ANEWARRAY){
                TypeInsnNode typeInsnNode = (TypeInsnNode) instruction;
                if(typeInsnNode.desc.equals("it/unimi/dsi/fastutil/longs/LongLinkedOpenHashSet")){
                    typeInsnNode.desc = CC + "utils/LinkedInt3HashSet";
                }
            }else if(instruction.getOpcode() == INVOKEINTERFACE){
                MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;
                if(methodInsnNode.name.equals("defaultReturnValue") && methodInsnNode.desc.equals("(B)V")){
                    for (int j = 0; j <= 3; j++) {
                        constructor.instructions.remove(instructions[i - j]);
                    }
                }
            }else if(instruction instanceof VarInsnNode var){
                if(var.var >= shiftFrom){
                    var.var++;
                }
            }else if(instruction instanceof IincInsnNode iinc){
                if(iinc.var >= shiftFrom){
                    iinc.var++;
                }
            }
        }

        //Find the super call (DynamicGraphMinFixedNode inherits from Object)
        AbstractInsnNode insn = constructor.instructions.getFirst();
        while (!(insn instanceof MethodInsnNode)) {
            insn = insn.getNext();
        }

        InsnList check = new InsnList();
        check.add(new VarInsnNode(ILOAD, 4));
        check.add(new LdcInsnNode(0xDEADBEEF));
        LabelNode ok = new LabelNode();
        check.add(new JumpInsnNode(IF_ICMPEQ, ok));
        check.add(new TypeInsnNode(NEW, "java/lang/IllegalArgumentException"));
        check.add(new InsnNode(DUP));
        check.add(new LdcInsnNode("Fourth argument to synthetic constructor must be 0xDEADBEEF"));
        check.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false));
        check.add(new InsnNode(ATHROW));
        check.add(ok);

        constructor.instructions.insert(insn, check);

        markCCSynthetic(constructor, "<init>", "(III)V", "HARDCODED");

        if(targetClass.methods.size() == 0){
            targetClass.methods.add(constructor);
        }else {
            targetClass.methods.add(1, constructor);
        }
    }

    /**
     * Change a field's type to Object, and make all field accesses in the class cast the field to its original type.
     *
     * <p>Used to allow us to assign a different type to fields when replacing methods with CC equivalents,
     * rather than adding a new field or cloning the class.</p>
     *
     * Note: should also only be used for non-static private fields,
     * as static field accesses and field accesses in other classes are not updated.
     *
     * @param targetClass The class containing the field
     * @param field The field to change the type of
     */
    private static void changeFieldTypeToObject(ClassNode targetClass, ClassField field, Function<MethodNode, Pair<Type, Boolean>> typeProvider) {
        var objectTypeDescriptor = getObjectType("java/lang/Object").getDescriptor();

        var remappedField = remapField(field);

        // Find the field in the class
        var fieldNode = targetClass.fields.stream()
                .filter(x -> remappedField.name.equals(x.name) && remappedField.desc.getDescriptor().equals(x.desc))
                .findAny().orElseThrow(() -> new IllegalStateException("Target field " + remappedField + " not found"));

        // Change its type to object
        fieldNode.desc = objectTypeDescriptor;

        Type methodOwner = field.desc;
        while (methodOwner.getSort() == ARRAY){
            methodOwner = methodOwner.getElementType();
        }

        // Find all usages of the field in the class (i.e. GETFIELD and PUTFIELD instructions)
        // and update their types to object, adding a cast to the original type after all GETFIELDs
        Type finalMethodOwner = methodOwner;
        targetClass.methods.forEach(methodNode -> {
            var use = typeProvider.apply(methodNode);
            Type type = use.getFirst();
            boolean isInterface = use.getSecond();

            Type newMethodOwner = type;
            while (newMethodOwner.getSort() == ARRAY){
                newMethodOwner = newMethodOwner.getElementType();
            }
            Type finalNewMethodOwner = newMethodOwner;
            methodNode.instructions.forEach(i -> {
                if (i.getType() == AbstractInsnNode.FIELD_INSN) {
                    var instruction = ((FieldInsnNode) i);
                    if (fieldNode.name.equals(instruction.name)) {
                        if (instruction.getOpcode() == PUTFIELD) {
                            instruction.desc = objectTypeDescriptor;
                        } else if (instruction.getOpcode() == GETFIELD) {
                            instruction.desc = objectTypeDescriptor;
                            // Cast to original type
                            methodNode.instructions.insert(instruction, new TypeInsnNode(CHECKCAST, type.getInternalName()));
                        }
                    }
                }else if(!field.desc.equals(type)) {
                    // We change all occurrences of the old type to the new type (This could be done better with ASM analysis)
                    if (i instanceof MethodInsnNode methodCall){
                        if(methodCall.owner.equals(finalMethodOwner.getInternalName())){
                            methodCall.owner = finalNewMethodOwner.getInternalName();

                            methodCall.itf = isInterface;
                            if(methodCall.getOpcode() == INVOKEINTERFACE && !isInterface){
                                methodCall.setOpcode(INVOKEVIRTUAL);
                            }else if(methodCall.getOpcode() == INVOKEVIRTUAL && isInterface){
                                methodCall.setOpcode(INVOKEINTERFACE);
                            }
                        }
                    }
                }
            });
        });
    }

    /**
     * Create a static accessor method for a given method we created on a class.
     *
     * e.g. if we had created a method {@code public boolean bar(int, int)} on a class {@code Foo},
     * this method would create a method {@code public static boolean bar(Foo, int, int)}.
     *
     * @param node class of the method
     * @param newMethod method to create a static accessor for
     */
    private static void makeStaticSyntheticAccessor(ClassNode node, MethodNode newMethod) {
        Type[] params = Type.getArgumentTypes(newMethod.desc);
        Type[] newParams = new Type[params.length + 1];
        System.arraycopy(params, 0, newParams, 1, params.length);
        newParams[0] = getObjectType(node.name);

        Type returnType = Type.getReturnType(newMethod.desc);
        MethodNode newNode = new MethodNode(newMethod.access | ACC_STATIC | ACC_SYNTHETIC, newMethod.name,
            Type.getMethodDescriptor(returnType, newParams), null, null);

        int j = 0;
        for (Type param : newParams) {
            newNode.instructions.add(new VarInsnNode(param.getOpcode(ILOAD), j));
            j += param.getSize();
        }
        newNode.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, newMethod.name, newMethod.desc, false));
        newNode.instructions.add(new InsnNode(returnType.getOpcode(IRETURN)));
        node.methods.add(newNode);
    }

    /**
     * Create a clone of a method with substituted methods, fields, and types. Generally used for creating 3d equivalents of 2d methods.
     *
     * @param node the class containing the method to be cloned
     * @param existingMethodIn the method to be cloned
     * @param newName name for the newly-cloned method
     * @param methodRedirectsIn map of method substitutions
     * @param fieldRedirectsIn map of field substitutions
     * @param typeRedirectsIn map of type substitutions
     * @return the cloned method
     */
    private static MethodNode cloneAndApplyRedirects(ClassNode node, ClassMethod existingMethodIn, String newName,
                                                     Map<ClassMethod, String> methodRedirectsIn, Map<ClassField, String> fieldRedirectsIn, Map<Type, Type> typeRedirectsIn) {
        LOGGER.info("Transforming " + node.name + ": Cloning method " + existingMethodIn.method.getName() + " " + existingMethodIn.method.getDescriptor() + " "
            + "into " + newName + " and applying remapping");
        Method existingMethod = remapMethod(existingMethodIn).method;

        MethodNode m = node.methods.stream()
            .filter(x -> existingMethod.getName().equals(x.name) && existingMethod.getDescriptor().equals(x.desc))
            .findAny().orElseThrow(() -> new IllegalStateException("Target method " + existingMethod + " not found"));

        Map<Handle, String> redirectedLambdas = cloneAndApplyLambdaRedirects(node, m, methodRedirectsIn, fieldRedirectsIn, typeRedirectsIn);

        Set<String> defaultKnownClasses = Sets.newHashSet(
            Type.getType(Object.class).getInternalName(),
            Type.getType(String.class).getInternalName(),
            node.name
        );

        Map<String, String> methodRedirects = new HashMap<>();
        for (ClassMethod classMethodUnmapped : methodRedirectsIn.keySet()) {
            ClassMethod classMethod = remapMethod(classMethodUnmapped);
            methodRedirects.put(
                classMethod.owner.getInternalName() + "." + classMethod.method.getName() + classMethod.method.getDescriptor(),
                methodRedirectsIn.get(classMethodUnmapped)
            );
        }
        for (Handle handle : redirectedLambdas.keySet()) {
            methodRedirects.put(
                handle.getOwner() + "." + handle.getName() + handle.getDesc(),
                redirectedLambdas.get(handle)
            );
        }

        Map<String, String> fieldRedirects = new HashMap<>();
        for (ClassField classFieldUnmapped : fieldRedirectsIn.keySet()) {
            ClassField classField = remapField(classFieldUnmapped);
            fieldRedirects.put(
                classField.owner.getInternalName() + "." + classField.name,
                fieldRedirectsIn.get(classFieldUnmapped)
            );
        }

        Map<String, String> typeRedirects = new HashMap<>();
        for (Type type : typeRedirectsIn.keySet()) {
            typeRedirects.put(remapType(type).getInternalName(), remapType(typeRedirectsIn.get(type)).getInternalName());
        }

        methodRedirects.forEach((old, n) -> LOGGER.debug("Method mapping: " + old + " -> " + n));
        fieldRedirects.forEach((old, n) -> LOGGER.debug("Field mapping: " + old + " -> " + n));
        typeRedirects.forEach((old, n) -> LOGGER.debug("Type mapping: " + old + " -> " + n));

        Remapper remapper = new Remapper() {
            @Override
            public String mapMethodName(final String owner, final String name, final String descriptor) {
                if (name.equals("<init>")) {
                    return name;
                }
                String key = owner + '.' + name + descriptor;
                String mappedName = methodRedirects.get(key);
                if (mappedName == null) {
                    if (IS_DEV) {
                        LOGGER.warn("NOTE: handling METHOD redirect to self: " + key);
                    }
                    methodRedirects.put(key, name);
                    return name;
                }
                return mappedName;
            }

            @Override
            public String mapInvokeDynamicMethodName(final String name, final String descriptor) {
                if (IS_DEV) {
                    LOGGER.warn("NOTE: remapping invokedynamic to self: " + name + "." + descriptor);
                }
                return name;
            }

            @Override
            public String mapFieldName(final String owner, final String name, final String descriptor) {
                String key = owner + '.' + name;
                String mapped = fieldRedirects.get(key);
                if (mapped == null) {
                    if (IS_DEV) {
                        LOGGER.warn("NOTE: handling FIELD redirect to self: " + key);
                    }
                    fieldRedirects.put(key, name);
                    return name;
                }
                return mapped;
            }

            @Override
            public String map(final String key) {
                String mapped = typeRedirects.get(key);
                if (mapped == null && defaultKnownClasses.contains(key)) {
                    mapped = key;
                }
                if (mapped == null) {
                    if (IS_DEV) {
                        LOGGER.warn("NOTE: handling CLASS redirect to self: " + key);
                    }
                    typeRedirects.put(key, key);
                    return key;
                }
                return mapped;
            }
        };
        String desc = m.desc;
        Type[] params = Type.getArgumentTypes(desc);
        Type ret = Type.getReturnType(desc);
        for (int i = 0; i < params.length; i++) {
            if (params[i].getSort() == Type.OBJECT) {
                params[i] = getObjectType(remapper.map(params[i].getInternalName()));
            }
        }
        if (ret.getSort() == Type.OBJECT) {
            ret = getObjectType(remapper.map(ret.getInternalName()));
        }
        String mappedDesc = Type.getMethodDescriptor(ret, params);

        MethodNode existingOutput = findExistingMethod(node, newName, mappedDesc);
        MethodNode output;
        if (existingOutput != null) {
            LOGGER.info("Copying code into existing method " + newName + " " + mappedDesc);
            output = existingOutput;
        } else {
            output = new MethodNode(m.access, newName, mappedDesc, null, m.exceptions.toArray(new String[0]));
        }

        MethodRemapper methodRemapper = new MethodRemapper(output, remapper);

        m.accept(methodRemapper);
        output.name = newName;
        // remove protected and private, add public
        output.access &= ~(ACC_PROTECTED | ACC_PRIVATE);
        output.access |= ACC_PUBLIC;
        node.methods.add(output);

        return output;
    }

    private static MethodNode findExistingMethod(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findAny().orElse(null);
    }

    private static ClassField remapField(ClassField clField) {
        MappingResolver mappingResolver = FabricLoader.getInstance().getMappingResolver();

        Type mappedType = remapType(clField.owner);
        String mappedName = mappingResolver.mapFieldName("intermediary",
            clField.owner.getClassName(), clField.name, clField.desc.getDescriptor());
        Type mappedDesc = remapDescType(clField.desc);
        if (clField.name.contains("field") && IS_DEV && mappedName.equals(clField.name)) {
            throw new Error("Fail! Mapping field " + clField.name + " failed in dev!");
        }
        return new ClassField(mappedType, mappedName, mappedDesc);
    }

    @NotNull private static ClassMethod remapMethod(ClassMethod clMethod) {
        MappingResolver mappingResolver = FabricLoader.getInstance().getMappingResolver();
        Type[] params = Type.getArgumentTypes(clMethod.method.getDescriptor());
        Type returnType = Type.getReturnType(clMethod.method.getDescriptor());

        Type mappedType = remapType(clMethod.owner);
        String mappedName = mappingResolver.mapMethodName("intermediary",
            clMethod.mappingOwner.getClassName(), clMethod.method.getName(), clMethod.method.getDescriptor());
        if (clMethod.method.getName().contains("method") && IS_DEV && mappedName.equals(clMethod.method.getName())) {
            throw new Error("Fail! Mapping method " + clMethod.method.getName() + " failed in dev!");
        }
        Type[] mappedParams = new Type[params.length];
        for (int i = 0; i < params.length; i++) {
            mappedParams[i] = remapDescType(params[i]);
        }
        Type mappedReturnType = remapDescType(returnType);
        return new ClassMethod(mappedType, new Method(mappedName, mappedReturnType, mappedParams));
    }

    private static Type remapDescType(Type t) {
        if (t.getSort() == ARRAY) {
            int dimCount = t.getDimensions();
            StringBuilder prefix = new StringBuilder(dimCount);
            for (int i = 0; i < dimCount; i++) {
                prefix.append('[');
            }
            return Type.getType(prefix + remapDescType(t.getElementType()).getDescriptor());
        }
        if (t.getSort() != OBJECT) {
            return t;
        }
        MappingResolver mappingResolver = FabricLoader.getInstance().getMappingResolver();
        String unmapped = t.getClassName();
        if (unmapped.endsWith(";")) {
            unmapped = unmapped.substring(1, unmapped.length() - 1);
        }
        String mapped = mappingResolver.mapClassName("intermediary", unmapped);
        String mappedDesc = 'L' + mapped.replace('.', '/') + ';';
        if (unmapped.contains("class") && IS_DEV && mapped.equals(unmapped)) {
            throw new Error("Fail! Mapping class " + unmapped + " failed in dev!");
        }
        return Type.getType(mappedDesc);
    }

    private static Type remapType(Type t) {
        MappingResolver mappingResolver = FabricLoader.getInstance().getMappingResolver();
        String unmapped = t.getClassName();
        String mapped = mappingResolver.mapClassName("intermediary", unmapped);
        if (unmapped.contains("class") && IS_DEV && mapped.equals(unmapped)) {
            throw new Error("Fail! Mapping class " + unmapped + " failed in dev!");
        }
        return Type.getObjectType(mapped.replace('.', '/'));
    }

    private static Map<Handle, String> cloneAndApplyLambdaRedirects(ClassNode node, MethodNode method, Map<ClassMethod, String> methodRedirectsIn,
                                                                    Map<ClassField, String> fieldRedirectsIn, Map<Type, Type> typeRedirectsIn) {

        Map<Handle, String> lambdaRedirects = new HashMap<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == INVOKEDYNAMIC) {
                InvokeDynamicInsnNode invoke = (InvokeDynamicInsnNode) instruction;
                String bootstrapMethodName = invoke.bsm.getName();
                String bootstrapMethodOwner = invoke.bsm.getOwner();
                if (bootstrapMethodName.equals("metafactory") && bootstrapMethodOwner.equals("java/lang/invoke/LambdaMetafactory")) {
                    for (Object bsmArg : invoke.bsmArgs) {
                        if (bsmArg instanceof Handle) {
                            Handle handle = (Handle) bsmArg;
                            String owner = handle.getOwner();
                            MethodNode existingMethod = findExistingMethod(node, handle.getName(), handle.getDesc());
                            if (owner.equals(node.name) && (existingMethod.access & ACC_SYNTHETIC) != 0) {
                                String newName = "cc$redirect$" + handle.getName();
                                lambdaRedirects.put(handle, newName);
                                cloneAndApplyRedirects(node, new ClassMethod(Type.getObjectType(handle.getOwner()),
                                        new Method(handle.getName(), handle.getDesc())),
                                    newName, methodRedirectsIn, fieldRedirectsIn, typeRedirectsIn);
                            }
                        }
                    }
                }
            }
        }
        return lambdaRedirects;
    }

    public static void markCCSynthetic(MethodNode method, String name, String desc, String type) {
        if(method.visibleAnnotations == null) {
            method.visibleAnnotations = new ArrayList<>(1);
        }

        AnnotationNode synthetic = new AnnotationNode(Type.getDescriptor(CubicChunksSynthetic.class));
        synthetic.visit("original", name + "#" + desc);
        synthetic.visit("type", type);
        method.visibleAnnotations.add(synthetic);
    }

    public static boolean isCCSynthetic(MethodNode method){
        if(method.visibleAnnotations == null) {
            return false;
        }

        for(AnnotationNode annotation : method.visibleAnnotations){
            if(annotation.desc.equals(Type.getDescriptor(CubicChunksSynthetic.class))) {
                return true;
            }
        }

        return false;
    }

    private static ClassNode loadClass(Type type){
        ClassNode node = new ClassNode();

        try {
            InputStream is = ClassLoader.getSystemResourceAsStream(type.getInternalName() + ".class");
            ClassReader reader = new ClassReader(is);
            reader.accept(node, 0);
            is.close();
        }catch (IOException e){
            throw new RuntimeException(e);
        }

        return node;
    }

    public static final class ClassMethod {
        public final Type owner;
        public final Method method;
        public final Type mappingOwner;

        ClassMethod(Type owner, Method method) {
            this.owner = owner;
            this.method = method;
            this.mappingOwner = owner;
        }

        // mapping owner because mappings owner may not be the same as in the call site
        ClassMethod(Type owner, Method method, Type mappingOwner) {
            this.owner = owner;
            this.method = method;
            this.mappingOwner = mappingOwner;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClassMethod that = (ClassMethod) o;
            return owner.equals(that.owner) && method.equals(that.method) && mappingOwner.equals(that.mappingOwner);
        }

        @Override public int hashCode() {
            return Objects.hash(owner, method, mappingOwner);
        }

        @Override public String toString() {
            return "ClassMethod{" +
                "owner=" + owner +
                ", method=" + method +
                ", mappingOwner=" + mappingOwner +
                '}';
        }
    }

    public static final class ClassField {
        public final Type owner;
        public final String name;
        public final Type desc;

        ClassField(String owner, String name, String desc) {
            this.owner = getObjectType(owner);
            this.name = name;
            this.desc = getType(desc);
        }

        ClassField(Type owner, String name, Type desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClassField that = (ClassField) o;
            return owner.equals(that.owner) && name.equals(that.name) && desc.equals(that.desc);
        }

        @Override public int hashCode() {
            return Objects.hash(owner, name, desc);
        }

        @Override public String toString() {
            return "ClassField{" +
                "owner=" + owner +
                ", name='" + name + '\'' +
                ", desc=" + desc +
                '}';
        }
    }
}