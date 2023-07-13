package io.github.opencubicchunks.cubicchunks.test.tests;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.server.level.CubeMap;
import io.github.opencubicchunks.cubicchunks.server.level.CubicDistanceManager;
import io.github.opencubicchunks.cubicchunks.test.LightingIntegrationTest;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeStatus;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.chunk.ChunkStatus;

public class PlaceholderTests {
    public static void register(Collection<LightingIntegrationTest> lightingTests) {
        lightingTests.add(new LightingIntegrationTest("test_0", 0,
            context -> {
                CubePos pos = CubePos.of(0, 0, 0);
                ((CubicDistanceManager) context.level().getChunkSource().chunkMap.getDistanceManager()).addCubeTicket(TicketType.START, pos,
                    33 + CubeStatus.getDistance(ChunkStatus.FULL) - 5,
                    Unit.INSTANCE);
                System.out.println("Test 0 start");
            },
            context -> {
                System.out.println("Test 0 tick");
                context.pass();
            },
            context -> {
                CubePos pos = CubePos.of(0, 0, 0);
                ((CubicDistanceManager) context.level().getChunkSource().chunkMap.getDistanceManager()).removeCubeTicket(TicketType.START, pos,
                    33 + CubeStatus.getDistance(ChunkStatus.FULL) - 5,
                    Unit.INSTANCE);
                System.out.println("Test 0 end");
            }
        ));
        AtomicInteger ticksPassed = new AtomicInteger();
        lightingTests.add(new LightingIntegrationTest("test_1", 0,
            context -> {
                System.out.println("Test 1 start");
                CubePos pos = CubePos.of(0, 0, 0);
                ((CubicDistanceManager) context.level().getChunkSource().chunkMap.getDistanceManager()).addCubeTicket(TicketType.START, pos,
                    33 + CubeStatus.getDistance(ChunkStatus.FULL) - 5,
                    Unit.INSTANCE);
            },
            context -> {
                System.out.println("Test 1 tick");
                if (ticksPassed.getAndIncrement() == 30) {
                    context.pass();
                }
            },
            context -> {
                CubePos pos = CubePos.of(0, 0, 0);
                ((CubicDistanceManager) context.level().getChunkSource().chunkMap.getDistanceManager()).removeCubeTicket(TicketType.START, pos,
                    33 + CubeStatus.getDistance(ChunkStatus.FULL) - 5,
                    Unit.INSTANCE);
                System.out.println("Test 1 end");
            }
        ));
        var minPos = CubePos.of(-1, -1, -1);
        var maxPos = CubePos.of(1, 1, 1);
        var requiredLevel = ChunkStatus.LIGHT;
        var unloading = new boolean[] {false};
        CompletableFuture<?>[] future = new CompletableFuture[1];
        lightingTests.add(new LightingIntegrationTest("test_2", 0,
            context -> {
                System.out.println("Test 2 start");
                future[0] = context.getCubeLoadFuturesInVolume(minPos, maxPos, requiredLevel);
            },
            context -> {
                System.out.println("Test 2 tick");
                if (!future[0].isDone()) return;
                if (!unloading[0]) {
                    unloading[0] = true;
                    future[0] = context.getCubeUnloadFuturesInVolume(minPos, maxPos, requiredLevel);
                    return;
                }
                context.pass();
            },
            context -> {
                ((CubeMap) context.level().getChunkSource().chunkMap).getUpdatingCubeMap().values().stream().filter(holder -> {
                    var status = holder.getLastAvailableStatus();
                    return status != null && status != ChunkStatus.EMPTY;
                }).forEach(
                    holder -> System.out.println(holder.getLastAvailableStatus())
                );
                System.out.println("Test 2 end");
            }
        ));
    }
}
