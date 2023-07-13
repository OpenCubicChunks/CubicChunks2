package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import java.util.function.BooleanSupplier;

import io.github.opencubicchunks.cubicchunks.test.IntegrationTests;
import io.github.opencubicchunks.cubicchunks.test.LevelTestRunner;
import io.github.opencubicchunks.cubicchunks.test.LightingIntegrationTest;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class MixinServerLevel_TestRunner implements LevelTestRunner {
    private boolean testStarted = false;
    private LightingIntegrationTest test;
    private LightingIntegrationTest.TestContext context;

    private boolean teardownComplete = false;

    @Override public void startTestInLevel(LightingIntegrationTest integrationTest) {
        this.test = integrationTest;
        this.context = integrationTest.new TestContext((ServerLevel) (Object) this);
        this.testStarted = true;

        try {
            this.test.setup.accept(this.context);
        } catch (Throwable t) {
            this.context.fail(t);
        }
    }

    @Override public boolean testFinished() {
        return this.testStarted && test.isFinished();
    }

    @Override public IntegrationTests.TestState testState() {
        return test.getState();
    }

    @Override public LightingIntegrationTest getTest() {
        return this.test;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tickTest(BooleanSupplier b, CallbackInfo ci) {
        if (this.test != null && this.test.getState() == IntegrationTests.TestState.FAIL) {
            // The test has failed, we don't want to update the level in any way for debugging purposes
            if (IntegrationTests.FREEZE_FAILING_WORLDS) {
                ci.cancel();
                return;
            }
        }

        if (this.test != null && !this.test.isFinished()) {
            try {
                this.test.tick.accept(this.context);
            } catch (Throwable t) {
                this.context.fail(t);
            }
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void handleTestFinished(BooleanSupplier b, CallbackInfo ci) {
        if (this.test != null) {
            if (!this.teardownComplete && this.test.isFinished() && this.test.getState() != IntegrationTests.TestState.FAIL) {
                this.teardownComplete = true;
                try {
                    this.test.teardown.accept(this.context);
                } catch (Throwable t) {
                    this.context.fail(t);
                }
            }
        }
    }
}
