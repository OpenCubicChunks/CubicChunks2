package io.github.opencubicchunks.cubicchunks.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.opencubicchunks.cubicchunks.mixin.transform.MainTransformer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.ConfigLoader;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.FabricMappingsProvider;
import io.github.opencubicchunks.cubicchunks.utils.TestMappingUtils;
import io.github.opencubicchunks.dasm.MappingsProvider;
import io.github.opencubicchunks.dasm.RedirectsParseException;
import io.github.opencubicchunks.dasm.RedirectsParser;
import io.github.opencubicchunks.dasm.Transformer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

public class ASMConfigPlugin implements IMixinConfigPlugin {
    private final Map<String, RedirectsParser.ClassTarget> classTargetByName = new HashMap<>();
    private final Map<RedirectsParser.ClassTarget, List<RedirectsParser.RedirectSet>> redirectSetsByClassTarget = new HashMap<>();
    private final ConcurrentHashMap<String, ClassNode> classesToDuplicateSrc = new ConcurrentHashMap<>();
    private final Map<String, String> classDuplicationDummyTargets = new HashMap<>();

    private final Throwable constructException;

    private final Transformer transformer;

    private final MappingsProvider mappings;

    public ASMConfigPlugin() {
        this.transformer = new Transformer(
            MappingsProvider.IDENTITY,
            TestMappingUtils.isDev()
        );

        this.mappings = new FabricMappingsProvider(
            TestMappingUtils.getMappingResolver(),
            "intermediary"
        );

        List<RedirectsParser.RedirectSet> redirectSets;
        List<RedirectsParser.ClassTarget> targetClasses;
        try {
            //TODO: add easy use of multiple set and target json files
            redirectSets = loadSetsFile("dasm/sets/sets.json");
            targetClasses = loadTargetsFile("dasm/targets.json");
        } catch (RedirectsParseException e) {
            constructException = e; // Annoying because mixin catches Throwable for creating a config plugin >:(
            return;
        }
        constructException = null;

        Map<String, RedirectsParser.RedirectSet> redirectSetByName = new HashMap<>();

        for (RedirectsParser.RedirectSet redirectSet : redirectSets) {
            redirectSetByName.put(redirectSet.getName(), redirectSet);
        }
        for (RedirectsParser.ClassTarget target : targetClasses) {
            classTargetByName.put(target.getClassName(), target);
            List<RedirectsParser.RedirectSet> sets = new ArrayList<>();
            for (String set : target.getSets()) {
                sets.add(redirectSetByName.get(set));
            }
            redirectSetsByClassTarget.put(target, sets);
            if (target.isWholeClass()) {
                classDuplicationDummyTargets.put(findWholeClassTypeRedirectFor(target, redirectSetByName), target.getClassName());
            }
        }
    }

    private String findWholeClassTypeRedirectFor(RedirectsParser.ClassTarget target, Map<String, RedirectsParser.RedirectSet> redirects) {
        List<String> sets = target.getSets();
        for (String set : sets) {
            for (RedirectsParser.RedirectSet.TypeRedirect typeRedirect : redirects.get(set).getTypeRedirects()) {
                if (typeRedirect.srcClassName().equals(target.getClassName())) {
                    return typeRedirect.dstClassName();
                }
            }
        }
        throw new IllegalStateException("No type redirect for whole class redirect " + target.getClassName());
    }

    @Override public void onLoad(String mixinPackage) {
        if (this.constructException != null) {
            throw new Error(this.constructException); // throw error because Mixin catches Exception for onLoad
        }
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
        try {
            if (classDuplicationDummyTargets.containsKey(targetClassName)) {
                if (!classesToDuplicateSrc.containsKey(targetClassName)) {
                    throw new IllegalStateException("Class " + targetClassName + " has been loaded before " + classDuplicationDummyTargets.get(targetClassName));
                }
                replaceClassContent(targetClass, classesToDuplicateSrc.get(targetClassName));
                return;
            }

            //TODO: untangle the mess of some methods accepting the/class/name, and others accepting the.class.name
            //Ideally the input json would all have the same, and we'd just figure it out here
            RedirectsParser.ClassTarget target = classTargetByName.get(targetClassName);
            if (target == null) {
                return; //Class is transformed by MainTransformer
            }
            if (target.isWholeClass()) {
                ClassNode duplicate = new ClassNode();
                targetClass.accept(duplicate);
                this.transformer.transformClass(duplicate, target, redirectSetsByClassTarget.get(target));
                classesToDuplicateSrc.put(duplicate.name.replace('/', '.'), duplicate);
                return;
            }

            this.transformer.transformClass(targetClass, target, redirectSetsByClassTarget.get(target));
            try {
                // ugly hack to add class metadata to mixin
                // based on https://github.com/Chocohead/OptiFabric/blob/54fc2ef7533e43d1982e14bc3302bcf156f590d8/src/main/java/me/modmuss50/optifabric/compat/fabricrendererapi
                // /RendererMixinPlugin.java#L25:L44
                Method addMethod = ClassInfo.class.getDeclaredMethod("addMethod", MethodNode.class, boolean.class);
                addMethod.setAccessible(true);

                ClassInfo ci = ClassInfo.forName(targetClassName);
                Set<String> existingMethods = ci.getMethods().stream().map(x -> x.getName() + x.getDesc()).collect(Collectors.toSet());
                for (MethodNode method : targetClass.methods) {
                    if (!existingMethods.contains(method.name + method.desc)) {
                        addMethod.invoke(ci, method, false);
                    }
                }
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    private void replaceClassContent(ClassNode node, ClassNode replaceWith) {
        node.access = 0;
        node.name = null;
        node.signature = null;
        node.superName = null;
        node.interfaces.clear();
        node.sourceFile = null;
        node.sourceDebug = null;
        node.module = null;
        node.outerClass = null;
        node.outerMethod = null;
        node.outerMethodDesc = null;
        node.visibleAnnotations = null;
        node.invisibleAnnotations = null;
        node.visibleTypeAnnotations = null;
        node.invisibleTypeAnnotations = null;
        node.attrs = null;
        node.innerClasses.clear();
        node.nestHostClass = null;
        node.nestMembers = null;
        node.permittedSubclasses = null;
        node.recordComponents = null;
        node.fields.clear();
        node.methods.clear();

        replaceWith.accept(node);
    }

    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        MappingResolver map = TestMappingUtils.getMappingResolver();
        String dynamicGraphMinFixedPoint = map.mapClassName("intermediary", "net.minecraft.class_3554");
        String layerLightEngine = map.mapClassName("intermediary", "net.minecraft.class_3558");
        String layerLightSectionStorage = map.mapClassName("intermediary", "net.minecraft.class_3560");
        String blockLightSectionStorage = map.mapClassName("intermediary", "net.minecraft.class_3547");
        String skyLightSectionStorage = map.mapClassName("intermediary", "net.minecraft.class_3569");
        String sectionPos = map.mapClassName("intermediary", "net.minecraft.class_4076");
        String blockLightEngine = map.mapClassName("intermediary", "net.minecraft.class_3552");
        String skyLightEngine = map.mapClassName("intermediary", "net.minecraft.class_3572");
        String noiseBasedAquifer = map.mapClassName("intermediary", "net.minecraft.class_6350$class_5832");

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
        } else if (targetClassName.equals(noiseBasedAquifer)){
            MainTransformer.transformNoiseBasedAquifer(targetClass);
        } else if (defaulted.contains(targetClassName)) {
            MainTransformer.defaultTransform(targetClass);
        } else {
            return;
        }

        //Save it without computing extra stuff (like maxs) which means that if the frames are wrong and mixin fails to save it, it will be saved elsewhere
        Path savePath = TestMappingUtils.getGameDir().resolve("longpos-out").resolve(targetClassName.replace('.', '/') + ".class");
        try {
            Files.createDirectories(savePath.getParent());

            ClassWriter writer = new ClassWriter(0);
            targetClass.accept(writer);
            byte[] bytes = writer.toByteArray();
            Files.write(savePath, bytes);
            System.out.println("Saved " + targetClassName + " to " + savePath);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonElement parseFileAsJson(String fileName) {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try (InputStream is = classloader.getResourceAsStream(fileName)) {
            return new JsonParser().parse(new InputStreamReader(is, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<RedirectsParser.ClassTarget> loadTargetsFile(String fileName) throws RedirectsParseException {
        RedirectsParser redirectsParser = new RedirectsParser();

        JsonElement targetsJson = parseFileAsJson(fileName);
        return redirectsParser.parseClassTargets(targetsJson.getAsJsonObject());
    }

    private List<RedirectsParser.RedirectSet> loadSetsFile(String fileName) throws RedirectsParseException {
        RedirectsParser redirectsParser = new RedirectsParser();

        JsonElement setsJson = parseFileAsJson(fileName);
        return redirectsParser.parseRedirectSet(setsJson.getAsJsonObject());
    }
}