package io.github.opencubicchunks.cubicchunks.test;

public interface LevelTestRunner {
    void startTestInLevel(LightingIntegrationTest test);

    boolean testFinished();
    IntegrationTests.TestState testState();

    LightingIntegrationTest getTest();
}
