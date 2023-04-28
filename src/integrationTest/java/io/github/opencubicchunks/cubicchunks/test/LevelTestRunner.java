package io.github.opencubicchunks.cubicchunks.test;

public interface LevelTestRunner {
    void startTestInLevel(IntegrationTests.LightingIntegrationTest test);

    boolean testFinished();
    IntegrationTests.TestState testState();

    IntegrationTests.LightingIntegrationTest getTest();
}
