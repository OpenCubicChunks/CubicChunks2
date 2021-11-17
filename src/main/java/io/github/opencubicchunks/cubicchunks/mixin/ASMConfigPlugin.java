package io.github.opencubicchunks.cubicchunks.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.mixin.transform.MainTransformer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.LongPosTransformer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
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
        MappingResolver map = FabricLoader.getInstance().getMappingResolver();
        boolean modified = false;
        String chunkMapDistanceManager = map.mapClassName("intermediary", "net.minecraft.class_3898$class_3216");
        String chunkMap = map.mapClassName("intermediary", "net.minecraft.class_3898");
        String chunkHolder = map.mapClassName("intermediary", "net.minecraft.class_3193");
        String naturalSpawner = map.mapClassName("intermediary", "net.minecraft.class_1948");
        String dynamicGraphMinFixedPoint = map.mapClassName("intermediary", "net.minecraft.class_3554");

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

        if(LongPosTransformer.shouldModifyClass(targetClass, map)){
            LongPosTransformer.modifyClass(targetClass);
            modified = true;
        }

        if (targetClassName.equals(dynamicGraphMinFixedPoint)) {
            //Dynamic graph min fixed point has modifications that need to happen AFTER the long pos tranforms
            modified = true;
            MainTransformer.transformDynamicGraphMinFixedPoint(targetClass);
        }

        if(!modified){
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

    }
}