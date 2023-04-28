package io.github.opencubicchunks.cubicchunks.test.mixin;

import java.util.function.BooleanSupplier;

import io.github.opencubicchunks.cubicchunks.test.IntegrationTests;
import io.github.opencubicchunks.cubicchunks.test.LevelTestRunner;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class MixinServerLevelTestRunner implements LevelTestRunner {
    private boolean testStarted = false;
    private IntegrationTests.LightingIntegrationTest test;
    private IntegrationTests.LightingIntegrationTest.TestContext context;

    private boolean teardownComplete = false;

    @Override public void startTestInLevel(IntegrationTests.LightingIntegrationTest integrationTest) {
        this.test = integrationTest;
        this.context = integrationTest.new TestContext();
        this.testStarted = true;

        this.test.setup.accept(this.context);
    }

    @Override public boolean testFinished() {
        return this.testStarted && test.isFinished();
    }

    @Override public IntegrationTests.TestState testState() {
        return test.getState();
    }

    @Override public IntegrationTests.LightingIntegrationTest getTest() {
        return this.test;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickTest(BooleanSupplier b, CallbackInfo ci) {
        if (this.test != null && !this.test.isFinished()) {
            this.test.tick.accept(this.context);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void handleTestFinished(BooleanSupplier b, CallbackInfo ci) {
        if (this.test != null) {
            if (this.test.isFinished() && !this.teardownComplete) {
                this.teardownComplete = true;
                this.test.teardown.accept(this.context);
            }
        }
    }
}
