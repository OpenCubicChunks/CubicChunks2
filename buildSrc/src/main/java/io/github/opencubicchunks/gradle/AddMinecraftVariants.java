package io.github.opencubicchunks.gradle;

import java.util.Set;

import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

public abstract class AddMinecraftVariants implements TransformAction<AddMinecraftVariants.Parameters> {
    interface Parameters extends TransformParameters {
        @Input Set<String> getTargetJarNames();
        void setTargetJarNames(Set<String> targetJarNames);

        @Input long getDebug();
        void setDebug(long value);
    }

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        var fileName = getInputArtifact().get().getAsFile().getName();

        Set<String> targetJarNames = getParameters().getTargetJarNames();

        if (targetJarNames.contains(fileName)) {
            System.out.printf("found %s\n", fileName);

            String fileNameNoExt = fileName.substring(0, fileName.lastIndexOf("."));
            var outputFileName = fileNameNoExt + "-minecraftvariants.jar";
            CoreTransformer.transformCoreLibrary(getInputArtifact().get().getAsFile(), outputs.file(outputFileName));

            System.out.printf("transformed %s\n", outputFileName);
            return;
        }
        outputs.file(getInputArtifact());
    }
}