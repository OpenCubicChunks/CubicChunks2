package io.github.opencubicchunks.cubicchunks.mixin.transform;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.ARRAY;
import static org.objectweb.asm.Type.OBJECT;
import static org.objectweb.asm.Type.getObjectType;
import static org.objectweb.asm.Type.getType;
import static org.objectweb.asm.commons.Method.getMethod;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.Sets;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.TypeTransformer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.Config;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.ConfigLoader;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.ASMUtil;
import io.github.opencubicchunks.cubicchunks.utils.Utils;
import net.fabricmc.loader.api.MappingResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class MainTransformer {
    public static final Config TRANSFORM_CONFIG;
    private static final Logger LOGGER = LogManager.getLogger();
    private static final boolean IS_DEV = Utils.isDev();

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
        TypeTransformer transformer = new TypeTransformer(TRANSFORM_CONFIG, targetClass, true);

        transformer.analyzeAllMethods();

        //transformer.makeConstructor("(III)V", makeDynGraphConstructor());
        transformer.makeConstructor("(III)V");

        transformer.transformAllMethods();
    }

    private static InsnList makeDynGraphConstructor() {
        LabelNode l1 = new LabelNode();
        LabelNode l2 = new LabelNode();
        LabelNode l3 = new LabelNode();

        InsnList l = new InsnList();
        l.add(new VarInsnNode(Opcodes.ALOAD, 0));
        l.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        l.add(new VarInsnNode(Opcodes.ILOAD, 1));
        l.add(new IntInsnNode(Opcodes.SIPUSH, 254));
        l.add(new JumpInsnNode(Opcodes.IF_ICMPLT, l1));
        l.add(new TypeInsnNode(Opcodes.NEW, "java/lang/IllegalArgumentException"));
        l.add(new InsnNode(Opcodes.DUP));
        l.add(new LdcInsnNode("Level count must be < 254."));
        l.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false));
        l.add(new InsnNode(Opcodes.ATHROW));
        l.add(l1);
        l.add(new VarInsnNode(Opcodes.ALOAD, 0));
        l.add(new VarInsnNode(Opcodes.ILOAD, 1));
        l.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/world/level/lighting/DynamicGraphMinFixedPoint", "levelCount", "I"));
        l.add(new VarInsnNode(Opcodes.ALOAD, 0));
        l.add(new VarInsnNode(Opcodes.ILOAD, 1));
        l.add(new TypeInsnNode(Opcodes.ANEWARRAY, "io/github/opencubicchunks/cubicchunks/utils/LinkedInt3HashSet"));
        //l.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/world/level/lighting/DynamicGraphMinFixedPoint", "queues",
        // "[Lio/github/opencubicchunks/cubicchunks/utils/LinkedInt3HashSet;
        // "));
        l.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/world/level/lighting/DynamicGraphMinFixedPoint", "queues", "Ljava/lang/Object;"));
        l.add(new InsnNode(Opcodes.ICONST_0));
        l.add(new VarInsnNode(Opcodes.ISTORE, 4));
        l.add(l3);
        l.add(new VarInsnNode(Opcodes.ILOAD, 4));
        l.add(new VarInsnNode(Opcodes.ILOAD, 1));
        l.add(new JumpInsnNode(Opcodes.IF_ICMPGE, l2));
        l.add(new VarInsnNode(Opcodes.ALOAD, 0));
        //l.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/world/level/lighting/DynamicGraphMinFixedPoint", "queues",
        // "[Lio/github/opencubicchunks/cubicchunks/utils/LinkedInt3HashSet;
        // "));
        l.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/world/level/lighting/DynamicGraphMinFixedPoint", "queues", "Ljava/lang/Object;"));
        l.add(new TypeInsnNode(Opcodes.CHECKCAST, "[Lio/github/opencubicchunks/cubicchunks/utils/LinkedInt3HashSet;"));
        l.add(new VarInsnNode(Opcodes.ILOAD, 4));
        l.add(new TypeInsnNode(Opcodes.NEW, "io/github/opencubicchunks/cubicchunks/utils/LinkedInt3HashSet"));
        l.add(new InsnNode(Opcodes.DUP));
        l.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "io/github/opencubicchunks/cubicchunks/utils/LinkedInt3HashSet", "<init>", "()V", false));
        l.add(new InsnNode(Opcodes.AASTORE));
        l.add(new IincInsnNode(4, 1));
        l.add(new JumpInsnNode(Opcodes.GOTO, l3));
        l.add(l2);
        l.add(new VarInsnNode(Opcodes.ALOAD, 0));
        l.add(new TypeInsnNode(Opcodes.NEW, "io/github/opencubicchunks/cubicchunks/utils/Int3UByteLinkedHashMap"));
        l.add(new InsnNode(Opcodes.DUP));
        l.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "io/github/opencubicchunks/cubicchunks/utils/Int3UByteLinkedHashMap", "<init>", "()V", false));
        //l.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/world/level/lighting/DynamicGraphMinFixedPoint", "computedLevels",
        // "Lio/github/opencubicchunks/cubicchunks/utils/Int3UByteLinkedHashMap;
        // "));
        l.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/world/level/lighting/DynamicGraphMinFixedPoint", "computedLevels", "Ljava/lang/Object;"));
        l.add(new VarInsnNode(Opcodes.ALOAD, 0));
        l.add(new VarInsnNode(Opcodes.ILOAD, 1));
        l.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/world/level/lighting/DynamicGraphMinFixedPoint", "firstQueuedLevel", "I"));
        l.add(new InsnNode(Opcodes.RETURN));
        return l;
    }

    public static void transformLayerLightEngine(ClassNode targetClass) {
        TypeTransformer transformer = new TypeTransformer(TRANSFORM_CONFIG, targetClass, true);

        transformer.analyzeAllMethods();
        transformer.transformAllMethods();

        transformer.callMagicSuperConstructor();
    }

    public static void transformSectionPos(ClassNode targetClass) {
        TypeTransformer transformer = new TypeTransformer(TRANSFORM_CONFIG, targetClass, true);

        ClassMethod blockToSection = remapMethod(
            new ClassMethod(
                getObjectType("net/minecraft/class_4076"),
                new Method("method_18691", "(J)J"),
                getObjectType("net/minecraft/class_4076")
            )
        );

        transformer.analyzeMethod(blockToSection.method.getName(), blockToSection.method.getDescriptor());
        transformer.cleanUpAnalysis();

        transformer.transformMethod(blockToSection.method.getName(), blockToSection.method.getDescriptor());
        transformer.cleanUpTransform();
    }

    public static void defaultTransform(ClassNode targetClass) {
        TypeTransformer transformer = new TypeTransformer(TRANSFORM_CONFIG, targetClass, true);

        transformer.analyzeAllMethods();
        transformer.transformAllMethods();
    }

    public static void transformLayerLightSectionStorage(ClassNode targetClass) {
        //400% performance improvement and all the vanilla lighting bugs go away with this one simple trick

        //Find setLevel

        ClassMethod setLevel = remapMethod(
            new ClassMethod(
                getObjectType("net/minecraft/class_3560"),
                new Method("method_15485", "(JI)V"),
                getObjectType("net/minecraft/class_3554")
            )
        );

        MethodNode setLevelNode =
            targetClass.methods.stream().filter(m -> m.name.equals(setLevel.method.getName()) && m.desc.equals(setLevel.method.getDescriptor())).findFirst().orElseThrow();

        //Find field access (sectionsAffectedByLightUpdates)
        ClassField sectionsAffectedByLightUpdates = remapField(
            new ClassField(
                "net/minecraft/class_3560",
                "field_16448",
                "Lit/unimi/dsi/fastutil/longs/LongSet;"
            )
        );

        AbstractInsnNode sectionsAffectedByLightUpdatesNode = ASMUtil.getFirstMatch(setLevelNode.instructions, (insn) -> {
            if (insn.getOpcode() == GETFIELD) {
                FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;
                return fieldInsnNode.owner.equals(sectionsAffectedByLightUpdates.owner.getInternalName()) && fieldInsnNode.name.equals(sectionsAffectedByLightUpdates.name);
            }
            return false;
        });


        //Find the LLOAD_1 instruction that comes after sectionsAffectedByLightUpdatesNode
        AbstractInsnNode load1Node = ASMUtil.getFirstMatch(sectionsAffectedByLightUpdatesNode, (insn) -> insn.getOpcode() == LLOAD && ((VarInsnNode) insn).var == 1);

        //Convert to BlockPos (SectionPos.sectionToBlock(J)J)

        ClassMethod blockToSection = remapMethod(
            new ClassMethod(
                getObjectType("net/minecraft/class_4076"),
                new Method("method_18691", "(J)J"),
                getObjectType("net/minecraft/class_4076")
            )
        );

        ClassMethod blockPosOffset = remapMethod(
            new ClassMethod(
                getObjectType("net/minecraft/class_2338"),
                new Method("method_10096", "(JIII)J"),
                getObjectType("net/minecraft/class_2338")
            )
        );

        ClassMethod sectionPosOffset = remapMethod(
            new ClassMethod(
                getObjectType("net/minecraft/class_4076"),
                new Method("method_18678", "(JIII)J"),
                getObjectType("net/minecraft/class_4076")
            )
        );

        AbstractInsnNode blockPosOffsetCall = ASMUtil.getFirstMatch(load1Node, (insn) -> {
            if (insn.getOpcode() == INVOKESTATIC) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) insn;
                return methodInsnNode.owner.equals(blockPosOffset.owner.getInternalName()) && methodInsnNode.name.equals(blockPosOffset.method.getName()) && methodInsnNode.desc.equals(
                    blockPosOffset.method.getDescriptor());
            }
            return false;
        });

        AbstractInsnNode blockToSectionCall = ASMUtil.getFirstMatch(blockPosOffsetCall, (insn) -> {
            if (insn.getOpcode() == INVOKESTATIC) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) insn;
                return methodInsnNode.owner.equals(blockToSection.owner.getInternalName()) && methodInsnNode.name.equals(blockToSection.method.getName()) && methodInsnNode.desc.equals(
                    blockToSection.method.getDescriptor());
            }
            return false;
        });

        MethodInsnNode sectionOffset =
            new MethodInsnNode(INVOKESTATIC, sectionPosOffset.owner.getInternalName(), sectionPosOffset.method.getName(), sectionPosOffset.method.getDescriptor(), false);

        setLevelNode.instructions.insert(blockToSectionCall, sectionOffset);
        setLevelNode.instructions.remove(blockToSectionCall);
        setLevelNode.instructions.remove(blockPosOffsetCall);

        defaultTransform(targetClass);
    }

    /**
     * Create a static accessor method for a given method we created on a class.
     * <p>
     * e.g. if we had created a method {@code public boolean bar(int, int)} on a class {@code Foo}, this method would create a method {@code public static boolean bar(Foo, int, int)}.
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
     *
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
        MappingResolver mappingResolver = Utils.getMappingResolver();

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
        MappingResolver mappingResolver = Utils.getMappingResolver();
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
        MappingResolver mappingResolver = Utils.getMappingResolver();
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
        MappingResolver mappingResolver = Utils.getMappingResolver();
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

    static {
        //Load config
        try {
            InputStream is = MainTransformer.class.getResourceAsStream("/type-transform.json");
            TRANSFORM_CONFIG = ConfigLoader.loadConfig(is);
            is.close();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't load transform config", e);
        }
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