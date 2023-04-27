package io.github.opencubicchunks.cubicchunks.mixin;

import static org.objectweb.asm.Opcodes.*;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class AnnotationConfigPlugin implements IMixinConfigPlugin {
    public AnnotationConfigPlugin() {
    }

    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Making any methods annotated as @Public public
        for (MethodNode method : targetClass.methods) {
            List<AnnotationNode> visibleAnnotations = method.visibleAnnotations;
            if (visibleAnnotations != null) {
                if (visibleAnnotations.stream().anyMatch(annotationNode -> annotationNode.desc.equals("Lio/github/opencubicchunks/cc_core/annotation/Public;"))) {
                    method.access &= ~ACC_PRIVATE;
                    method.access |= ACC_PUBLIC;
                }
            }
        }
    }

    @Override public void onLoad(String mixinPackage) {
    }

    @Override public String getRefMapperConfig() {
        return null;
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Nullable
    @Override public List<String> getMixins() {
        return null;
    }

    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Removing any @StirrinStub annotated methods (our mixins can now create those methods normally)
        for (Iterator<MethodNode> it = targetClass.methods.iterator(); it.hasNext();) {
            MethodNode method = it.next();
            List<AnnotationNode> visibleAnnotations = method.visibleAnnotations;
            if (visibleAnnotations != null) {
                if (visibleAnnotations.stream().anyMatch(annotationNode -> annotationNode.desc.equals("Lio/github/opencubicchunks/stirrin/StirrinStub;"))) {
                    it.remove();
                }
            }
        }
    }
}