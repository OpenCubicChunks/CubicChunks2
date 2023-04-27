package io.github.opencubicchunks.cubicchunks.world;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

import io.github.opencubicchunks.cc_core.annotation.UsedFromASM;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.NaturalSpawnerAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LevelCube;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;

@SuppressWarnings("JavaReflectionMemberAccess")
public class CubicNaturalSpawner {

    public static final int SPAWN_RADIUS = (int) (16 * (Math.sqrt((NaturalSpawnerAccess.getMagicNumber())) - 1) / 2);

    private static final Method SPAWN_FOR_CUBE;
    private static final Method CREATE_CUBIC_STATE;
    private static final Method IS_RIGHT_DISTANCE_TO_PLAYER_AND_SPAWN_POINT_FOR_CUBE;

    public static void spawnForCube(ServerLevel level, CubeAccess cube, NaturalSpawner.SpawnState spawnState, boolean spawnAnimals, boolean spawnMonsters, boolean shouldSpawnAnimals) {
        try {
            SPAWN_FOR_CUBE.invoke(null, level, cube, spawnState, spawnAnimals, spawnMonsters, shouldSpawnAnimals);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isRightDistanceToPlayerAndSpawnPoint(ServerLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, double squaredDistance) {
        try {
            return (boolean) IS_RIGHT_DISTANCE_TO_PLAYER_AND_SPAWN_POINT_FOR_CUBE.invoke(null, level, chunk, pos, squaredDistance);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static NaturalSpawner.SpawnState createState(int spawningChunkCount, Iterable<Entity> entities, CubeGetter cubeGetter, LocalMobCapCalculator localMobCapCalculator) {
        return createCubicState(spawningChunkCount, entities, cubeGetter, localMobCapCalculator);
    }

    public static NaturalSpawner.SpawnState createCubicState(int spawningChunkCount, Iterable<Entity> entities, CubeGetter cubeGetter, LocalMobCapCalculator localMobCapCalculator) {
        try {
            return (NaturalSpawner.SpawnState) CREATE_CUBIC_STATE.invoke(null, spawningChunkCount, entities, cubeGetter, localMobCapCalculator);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        try {
            SPAWN_FOR_CUBE = NaturalSpawner.class.getMethod("spawnForCube", ServerLevel.class, LevelCube.class, NaturalSpawner.SpawnState.class, boolean.class, boolean.class, boolean.class);

            CREATE_CUBIC_STATE = NaturalSpawner.class.getMethod("createCubicState", int.class, Iterable.class, CubicNaturalSpawner.CubeGetter.class, LocalMobCapCalculator.class);
            IS_RIGHT_DISTANCE_TO_PLAYER_AND_SPAWN_POINT_FOR_CUBE = NaturalSpawner.class.getMethod("isRightDistanceToPlayerAndSpawnPointForCube", ServerLevel.class, ChunkAccess.class,
                BlockPos.MutableBlockPos.class, double.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("ASM did not apply", e);
        }
    }

    @FunctionalInterface
    @UsedFromASM
    public interface CubeGetter {
        void query(long pos, Consumer<CubeAccess> chunkConsumer);
    }

    public interface CubicSpawnState {
        @UsedFromASM
        boolean canSpawnForCategory(MobCategory mobCategory, CubePos cubePos);
    }
}
