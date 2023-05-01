package io.github.opencubicchunks.cubicchunks.test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.test.tests.PlaceholderTests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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

    public static final class LightingIntegrationTest {
        public final String testName;
        public final long seed;

        public final Consumer<TestContext> setup;
        public final Consumer<TestContext> tick;
        public final Consumer<TestContext> teardown;

        private TestState state = TestState.NONE;
        private boolean finished = false;
        private BlockPos failurePos = null;
        private Throwable throwable = null;

        public LightingIntegrationTest(
            String testName, long seed,
            Consumer<TestContext> setup,
            Consumer<TestContext> tick,
            Consumer<TestContext> teardown
        ) {
            this.testName = testName;
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

        public Optional<BlockPos> getFailurePos() {
            return Optional.ofNullable(this.failurePos);
        }

        public Optional<Throwable> getThrown() {
            return Optional.ofNullable(this.throwable);
        }

        @Override
        public String toString() {
            return "LightingIntegrationTest[" +
                "setup=" + setup + ", " +
                "test=" + tick + ", " +
                "teardown=" + teardown + ']';
        }

        public final class TestContext {
            private final ServerLevel level;

            public TestContext(ServerLevel level) {
                this.level = level;
            }

            public void fail(Throwable t) {
                finished = true;
                state = TestState.FAIL;
                throwable = t;
            }

            public void pass() {
                if (!finished) {
                    finished = true;
                    state = TestState.PASS;
                }
            }

            public ServerLevel level() {
                return level;
            }

            public void setFailurePos(@Nullable BlockPos pos) {
                failurePos = pos;
            }
        }
    }
}
