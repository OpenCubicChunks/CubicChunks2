package io.github.opencubicchunks.cubicchunks.test.tests;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.server.level.CubicDistanceManager;
import io.github.opencubicchunks.cubicchunks.test.IntegrationTests.LightingIntegrationTest;
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
    }
}
