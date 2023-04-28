package io.github.opencubicchunks.cubicchunks.test.tests;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.opencubicchunks.cubicchunks.test.IntegrationTests.LightingIntegrationTest;

public class PlaceholderTests {
    public static void register(Collection<LightingIntegrationTest> lightingTests) {
        lightingTests.add(new LightingIntegrationTest(0,
            context -> {
                System.out.println("Test 0 start");
            },
            context -> {
                System.out.println("Test 0 tick");
                context.fail();
            },
            context -> {
                System.out.println("Test 0 end");
            }
        ));
        AtomicInteger ticksPassed = new AtomicInteger();
        lightingTests.add(new LightingIntegrationTest(0,
            context -> {
                System.out.println("Test 1 start");
            },
            context -> {
                System.out.println("Test 1 tick");
                if (ticksPassed.getAndIncrement() == 300) {
                    context.pass();
                }
            },
            context -> {
                System.out.println("Test 1 end");
            }
        ));
    }
}
