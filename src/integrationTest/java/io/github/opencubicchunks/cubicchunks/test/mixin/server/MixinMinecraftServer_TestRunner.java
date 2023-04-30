package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.test.IntegrationTests;
import io.github.opencubicchunks.cubicchunks.test.LevelTestRunner;
import io.github.opencubicchunks.cubicchunks.test.ServerTestRunner;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 999)
public abstract class MixinMinecraftServer_TestRunner implements ServerTestRunner {
    @Shadow @Final protected WorldData worldData;

    @Shadow @Final protected LevelStorageSource.LevelStorageAccess storageSource;

    private boolean isFrozen = false;

    private final Collection<IntegrationTests.LightingIntegrationTest> incompleteTests = IntegrationTests.getLightingTests();
    private final Collection<IntegrationTests.LightingIntegrationTest> failedTests = new ArrayList<>();

    @Nullable private Pair<ServerLevel, Optional<BlockPos>> firstError = null;

    @Shadow @Final private Map<ResourceKey<Level>, ServerLevel> levels;

    @Shadow @Final private Executor executor;

    @Shadow public abstract RegistryAccess.Frozen registryAccess();

    @Shadow public abstract void halt(boolean waitForServer);

    @Override @Nullable public Pair<ServerLevel, Optional<BlockPos>> firstErrorLocation() {
        return this.firstError;
    }

    @Inject(method = "createLevels", at = @At(value = "HEAD"), cancellable = true)
    private void createTestLevels(ChunkProgressListener chunkProgressListener, CallbackInfo ci) {
        ci.cancel();

        // each registered test gets its own level
        IntegrationTests.getLightingTests().forEach(test -> {
            ResourceKey<Level> levelResourceKey = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(test.testName));
            Holder<DimensionType> dimensionTypeHolder = this.registryAccess().registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY).getOrCreateHolder(DimensionType.OVERWORLD_LOCATION);
            ChunkGenerator chunkGenerator2 = WorldGenSettings.makeDefaultOverworld(this.registryAccess(), test.seed);
            DerivedLevelData derivedLevelData = new DerivedLevelData(this.worldData, this.worldData.overworldData());
            ServerLevel level = new ServerLevel(
                (MinecraftServer) (Object) this, this.executor, this.storageSource,
                derivedLevelData, levelResourceKey, dimensionTypeHolder, chunkProgressListener,
                chunkGenerator2, false, test.seed,
                ImmutableList.of(), false);

            ((LevelTestRunner) level).startTestInLevel(test);

            this.levels.put(levelResourceKey, level);
        });

    }

    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void prepareLevels(ChunkProgressListener progressListener, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void unloadLevelWhenTestFinish(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        for (ResourceKey<Level> levelResourceKey : new ArrayList<>(this.levels.keySet())) {
            ServerLevel level = this.levels.get(levelResourceKey);
            LevelTestRunner levelTestRunner = (LevelTestRunner) level;
            if (levelTestRunner.testFinished()) {
                IntegrationTests.LightingIntegrationTest test = levelTestRunner.getTest();
                incompleteTests.remove(test);

                if (test.getState() == IntegrationTests.TestState.FAIL) {
                    this.failedTests.add(test);
                    if (firstError == null) {
                        firstError = new ObjectObjectImmutablePair<>(level, test.getFailurePos());
                    }
                }

                // Failing test levels are not unloaded if FREEZE_FAILING_WORLDS is set
                if (test.getState() != IntegrationTests.TestState.FAIL || !IntegrationTests.FREEZE_FAILING_WORLDS) {
                    // Unload the test's level
                    ServerLevel removed = this.levels.remove(levelResourceKey);
                    try {
                        System.out.println("Removed " + levelResourceKey);
                        removed.close();
                    } catch (IOException e) {
                        System.err.println("Exception when closing test level");
                        e.printStackTrace(System.err);
                    }
                }
            }
        }

        if (this.incompleteTests.isEmpty()) {
            if (IntegrationTests.FREEZE_FAILING_WORLDS && !failedTests.isEmpty()) {
                if (!this.isFrozen) {
                    this.isFrozen = true;
                    CubicChunks.LOGGER.info("Tests complete! Holding failing worlds open");
                }
            } else {
                CubicChunks.LOGGER.info("Tests complete! Exiting...");
                if (!failedTests.isEmpty()) {
                    CrashReport crashReport = new CrashReport("Failed tests!", new Exception("Some exception"));
                    throw new ReportedException(crashReport);
                } else {
                    CubicChunks.LOGGER.info("No failed tests!");
                }

                this.halt(false);
            }
        }
    }

    /**
     * @author NotStirred
     * @reason By default, the player is spawned in the overworld. Instead spawn the player in the first error location, or some valid world
     *         if none exists.
     */
    @Overwrite
    public final ServerLevel overworld() {
        Pair<ServerLevel, Optional<BlockPos>> errorLocation = firstErrorLocation();

        if (errorLocation != null) {
            return errorLocation.first();
        } else {
            // if there is no error location, we just place the player in some valid level
            return this.levels.values().iterator().next();
        }
    }

    @Redirect(method = "saveAllChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/ServerLevelData;"
        + "setWorldBorder(Lnet/minecraft/world/level/border/WorldBorder$Settings;)V"))
    private void preventNoOverworldNPE(ServerLevelData instance, WorldBorder.Settings settings) {
        // do nothing instead
    }
    @Redirect(method = "saveAllChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;"
        + "getWorldBorder()Lnet/minecraft/world/level/border/WorldBorder;"))
    private WorldBorder preventNoOverworldNPE(ServerLevel instance) {
        return null;
    }
    @Redirect(method = "saveAllChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/border/WorldBorder;"
        + "createSettings()Lnet/minecraft/world/level/border/WorldBorder$Settings;"))
    private WorldBorder.Settings preventNoOverworldNPE(WorldBorder instance) {
        return null;
    }
}
