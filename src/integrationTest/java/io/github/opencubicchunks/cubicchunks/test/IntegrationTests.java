package io.github.opencubicchunks.cubicchunks.test;

import java.util.Collection;
import java.util.HashSet;
import java.util.function.Consumer;

import io.github.opencubicchunks.cubicchunks.test.tests.PlaceholderTests;

public class IntegrationTests {
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

    public static final class LightingIntegrationTest {
        public final long seed;

        public final Consumer<TestContext> setup;
        public final Consumer<TestContext> tick;
        public final Consumer<TestContext> teardown;

        private TestState state = TestState.NONE;
        private boolean finished = false;

        public LightingIntegrationTest(
            long seed,
            Consumer<TestContext> setup,
            Consumer<TestContext> tick,
            Consumer<TestContext> teardown
        ) {
            this.seed = seed;
            this.setup = setup;
            this.tick = tick;
            this.teardown = teardown;
        }

        public boolean isFinished() {
            return this.finished;
        }

        public TestState getState() {
            return this.state;
        }

        @Override
        public String toString() {
            return "LightingIntegrationTest[" +
                "setup=" + setup + ", " +
                "test=" + tick + ", " +
                "teardown=" + teardown + ']';
        }

        public final class TestContext {
            public TestContext() {
            }

            public void fail() {
                if (!finished) {
                    finished = true;
                    state = TestState.FAIL;
                }
            }
            public void pass() {
                if (!finished) {
                    finished = true;
                    state = TestState.PASS;
                }
            }
        }
    }
}
