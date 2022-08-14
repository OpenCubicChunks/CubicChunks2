package io.github.opencubicchunks.cubicchunks.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.opencubicchunks.cubicchunks.mixin.transform.FabricMappingsProvider;
import io.github.opencubicchunks.dasm.RedirectsParseException;
import io.github.opencubicchunks.dasm.RedirectsParser;
import io.github.opencubicchunks.dasm.Transformer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

public class ASMConfigPlugin implements IMixinConfigPlugin {
    private final Map<String, RedirectsParser.ClassTarget> classTargetByName = new HashMap<>();
    private final Map<RedirectsParser.ClassTarget, List<RedirectsParser.RedirectSet>> redirectSetsByClassTarget = new HashMap<>();

    private final Throwable constructException;

    private final Transformer transformer;

    public ASMConfigPlugin() {
        this.transformer = new Transformer(new FabricMappingsProvider(), FabricLoader.getInstance().isDevelopmentEnvironment());

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
        }
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
        MappingResolver map = FabricLoader.getInstance().getMappingResolver();

        //TODO: untangle the mess of some methods accepting the/class/name, and others accepting the.class.name
        //Ideally the input json would all have the same, and we'd just figure it out here
        RedirectsParser.ClassTarget target = classTargetByName.get(map.unmapClassName("intermediary", targetClassName).replace(".", "/"));
        if (target != null) {
            this.transformer.transformClass(targetClass, target, redirectSetsByClassTarget.get(target));
        } else {
            throw new RuntimeException(new ClassNotFoundException(String.format("Couldn't find target class %s to remap", targetClassName)));
        }

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
    }

    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

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