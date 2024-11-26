package io.github.opencubicchunks.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.extension.LoomGradleExtensionImpl;
import net.fabricmc.loom.util.service.ScopedSharedServiceManager;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.language.jvm.tasks.ProcessResources;

public class TypeTransformConfigGenPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.afterEvaluate(
            proj -> {
                ProcessResources processResources = ((ProcessResources) project.getTasks().getByName("processResources"));
                LoomGradleExtensionAPI loomApi = project.getExtensions().getByType(LoomGradleExtensionAPI.class);
                // TODO: try to use LoomGradleExtensionAPI#getMappingsFile() instead of loom internals
                MemoryMappingTree mappings = ((LoomGradleExtensionImpl) loomApi).getMappingConfiguration().getMappingsService(new ScopedSharedServiceManager()).getMappingTree();

                File destinationDir = processResources.getDestinationDir();
                processResources.filesMatching("**/type-transform.json", copySpec -> {
                    copySpec.exclude();
                    try {
                        File file = copySpec.getFile();
                        String content = Files.readString(file.toPath());
                        File output = copySpec.getRelativePath().getFile(destinationDir);

                        TypeTransformConfigGen generator = new TypeTransformConfigGen(proj, mappings, content);

                        Files.writeString(output.toPath(), generator.generate());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        );
    }
}
