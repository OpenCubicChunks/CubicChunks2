package io.github.opencubicchunks.cubicchunks.mixin;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.mixin.transform.MainTransformer;
import io.github.opencubicchunks.cubicchunks.utils.Utils;
import net.fabricmc.loader.api.MappingResolver;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

public class ASMConfigPlugin implements IMixinConfigPlugin {

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
        MappingResolver map = Utils.getMappingResolver();
        boolean modified = false;
        String chunkMapDistanceManager = map.mapClassName("intermediary", "net.minecraft.class_3898$class_3216");
        String chunkMap = map.mapClassName("intermediary", "net.minecraft.class_3898");
        String chunkHolder = map.mapClassName("intermediary", "net.minecraft.class_3193");
        String naturalSpawner = map.mapClassName("intermediary", "net.minecraft.class_1948");

        if (targetClassName.equals(chunkMapDistanceManager)) {
            modified = true;
            MainTransformer.transformProxyTicketManager(targetClass);
        } else if (targetClassName.equals(chunkMap)) {
            modified = true;
            MainTransformer.transformChunkManager(targetClass);
        } else if (targetClassName.equals(chunkHolder)) {
            modified = true;
            MainTransformer.transformChunkHolder(targetClass);
        } else if (targetClassName.equals(naturalSpawner)) {
            modified = true;
            MainTransformer.transformNaturalSpawner(targetClass);
        }

        if (!modified) {
            return;
        }

        try {
            // ugly hack to add class metadata to mixin
            // based on https://github.com/Chocohead/OptiFabric/blob/54fc2ef7533e43d1982e14bc3302bcf156f590d8/src/main/java/me/modmuss50/optifabric/compat/fabricrendererapi
            // /RendererMixinPlugin.java#L25:L44
            Method addMethod = ClassInfo.class.getDeclaredMethod("addMethod", MethodNode.class, boolean.class);
            addMethod.setAccessible(true);

            ClassInfo ci = ClassInfo.forName(targetClassName);

            //Field fieldField = ClassInfo.class.getDeclaredField("fields");
            //fieldField.setAccessible(true);
            //Set<ClassInfo.Field> fields = (Set<ClassInfo.Field>) fieldField.get(ci);

            Set<String> existingMethods = ci.getMethods().stream().map(x -> x.getName() + x.getDesc()).collect(Collectors.toSet());
            for (MethodNode method : targetClass.methods) {
                if (!existingMethods.contains(method.name + method.desc)) {
                    addMethod.invoke(ci, method, false);
                }
            }

            /*//Modify descriptors of modified fields
            for(FieldNode field: targetClass.fields){
                //Should only remove one
                fields.removeIf(fieldInfo -> fieldInfo.getName().equals(field.name) && !fieldInfo.getDesc().equals(field.desc));
                fields.add(ci.new Field(field, false));
            }*/
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException /*| NoSuchFieldException*/ e) {
            throw new IllegalStateException(e);
        }


    }

    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        MappingResolver map = Utils.getMappingResolver();
        String dynamicGraphMinFixedPoint = map.mapClassName("intermediary", "net.minecraft.class_3554");
        String layerLightEngine = map.mapClassName("intermediary", "net.minecraft.class_3558");
        String layerLightSectionStorage = map.mapClassName("intermediary", "net.minecraft.class_3560");
        String blockLightSectionStorage = map.mapClassName("intermediary", "net.minecraft.class_3547");
        String skyLightSectionStorage = map.mapClassName("intermediary", "net.minecraft.class_3569");
        String sectionPos = map.mapClassName("intermediary", "net.minecraft.class_4076");
        String blockLightEngine = map.mapClassName("intermediary", "net.minecraft.class_3552");
        String skyLightEngine = map.mapClassName("intermediary", "net.minecraft.class_3572");

        Set<String> defaulted = Set.of(
            blockLightSectionStorage,
            skyLightSectionStorage,
            blockLightEngine,
            skyLightEngine
        );

        if (targetClassName.equals(dynamicGraphMinFixedPoint)) {
            MainTransformer.transformDynamicGraphMinFixedPoint(targetClass);
        } else if (targetClassName.equals(layerLightEngine)) {
            MainTransformer.transformLayerLightEngine(targetClass);
        } else if (targetClassName.equals(layerLightSectionStorage)) {
            MainTransformer.transformLayerLightSectionStorage(targetClass);
        } else if (targetClassName.equals(sectionPos)) {
            MainTransformer.transformSectionPos(targetClass);
        } else if (defaulted.contains(targetClassName)) {
            MainTransformer.defaultTransform(targetClass);
        } else {
            return;
        }

        //Save it without computing extra stuff (like maxs) which means that if the frames are wrong and mixin fails to save it, it will be saved elsewhere
        Path savePath = Utils.getGameDir().resolve("longpos-out").resolve(targetClassName.replace('.', '/') + ".class");
        try {
            Files.createDirectories(savePath.getParent());

            ClassWriter writer = new ClassWriter(0);
            targetClass.accept(writer);
            Files.write(savePath, writer.toByteArray());
            System.out.println("Saved " + targetClassName + " to " + savePath);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}