package io.github.opencubicchunks.cubicchunks.test;

import java.util.Collection;
import java.util.HashSet;

import io.github.opencubicchunks.cubicchunks.test.tests.PlaceholderTests;

public class IntegrationTests {
    public static final boolean DISABLE_NETWORK = System.getProperty("cubicchunks.test.disableNetwork", "false").equals("true");
    public static final boolean FREEZE_FAILING_WORLDS = System.getProperty("cubicchunks.test.freezeFailingWorlds", "false").equals("true");

    private static final Collection<LightingIntegrationTest> LIGHTING_TESTS = new HashSet<>();
    static {
        PlaceholderTests.register(LIGHTING_TESTS);
    }

    public static Collection<LightingIntegrationTest> getLightingTests() {
        return LIGHTING_TESTS;
    }

    public enum TestState {
        PASS,
        FAIL,
        NONE,
    }
}
