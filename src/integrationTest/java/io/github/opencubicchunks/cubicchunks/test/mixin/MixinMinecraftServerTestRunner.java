package io.github.opencubicchunks.cubicchunks.test.mixin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import com.google.common.collect.ImmutableList;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.test.IntegrationTests;
import io.github.opencubicchunks.cubicchunks.test.LevelTestRunner;
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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 999)
public abstract class MixinMinecraftServerTestRunner {
    @Shadow @Final protected WorldData worldData;

    @Shadow @Final protected LevelStorageSource.LevelStorageAccess storageSource;

    private final Collection<IntegrationTests.LightingIntegrationTest> incompleteTests = IntegrationTests.getLightingTests();

    private int testIdx = 0;

    @Shadow @Final private Map<ResourceKey<Level>, ServerLevel> levels;

    @Shadow @Final private Executor executor;

    @Shadow public abstract RegistryAccess.Frozen registryAccess();

    @Shadow public abstract void halt(boolean waitForServer);

    @Inject(method = "createLevels", at = @At(value = "HEAD"), cancellable = true)
    private void createTestLevels(ChunkProgressListener chunkProgressListener, CallbackInfo ci) {
        ci.cancel();

        // each registered test gets its own level
        IntegrationTests.getLightingTests().forEach(test -> {
            ResourceKey<Level> levelResourceKey = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation("test" + testIdx++));
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
            LevelTestRunner levelTestRunner = (LevelTestRunner) this.levels.get(levelResourceKey);
            if (levelTestRunner.testFinished()) {
                ServerLevel removed = this.levels.remove(levelResourceKey);
                try {
                    incompleteTests.remove(levelTestRunner.getTest());
                    removed.close();
                } catch (IOException e) {
                    System.err.println("Exception when closing test level");
                    e.printStackTrace(System.err);
                }
            }
        }

        if (this.incompleteTests.isEmpty()) {
            CubicChunks.LOGGER.info("Tests complete! Exiting...");
            this.halt(false);
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
