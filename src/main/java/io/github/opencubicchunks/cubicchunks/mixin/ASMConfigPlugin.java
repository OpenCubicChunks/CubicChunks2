package io.github.opencubicchunks.cubicchunks.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.opencubicchunks.dasm.MappingsProvider;
import io.github.opencubicchunks.dasm.RedirectsParseException;
import io.github.opencubicchunks.dasm.RedirectsParser;
import io.github.opencubicchunks.dasm.Transformer;
import io.github.opencubicchunks.dasm.TypeRedirect;
import net.neoforged.fml.loading.FMLEnvironment;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
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
    private final Map<String, RedirectsParser.RedirectSet> redirectSetByName = new HashMap<>();
    private final Throwable constructException;

    private final Transformer transformer;

    public ASMConfigPlugin() {
        boolean developmentEnvironment = false;
        try {
            developmentEnvironment = !FMLEnvironment.production;
        } catch (Throwable ignored) {
        }
        MappingsProvider mappings = new MappingsProvider() {

            @Override public String mapFieldName(String owner, String fieldName, String descriptor) {
                return fieldName;
            }

            @Override public String mapMethodName(String owner, String methodName, String descriptor) {
                return methodName;
            }

            @Override public String mapClassName(String className) {
                return className;
            }
        };

        this.transformer = new Transformer(mappings, developmentEnvironment);

        List<RedirectsParser.RedirectSet> redirectSets;
        List<RedirectsParser.ClassTarget> targetClasses;
        try {
            //TODO: add easy use of multiple set and target json files
            redirectSets = loadSetsFile("dasm/sets/sets.json");
            targetClasses = loadTargetsFile("dasm/targets.json");

            for (RedirectsParser.RedirectSet redirectSet : redirectSets) {
                redirectSetByName.put(redirectSet.getName(), redirectSet);
            }
            for (RedirectsParser.ClassTarget target : targetClasses) {
                classTargetByName.put(mappings.mapClassName(target.getClassName()), target);
                List<RedirectsParser.RedirectSet> sets = new ArrayList<>();
                for (String set : target.getSets()) {
                    sets.add(redirectSetByName.get(set));
                }
                redirectSetsByClassTarget.put(target, sets);
                if (target.isWholeClass()) {
                    classDuplicationDummyTargets.put(
                        mappings.mapClassName(findWholeClassTypeRedirectFor(target, redirectSetByName)),
                        target.getClassName());
                }
            }
        } catch (Throwable e) {
            constructException = e; // Annoying because mixin catches Throwable for creating a config plugin >:(
            return;
        }
        constructException = null;
    }

    private String findWholeClassTypeRedirectFor(RedirectsParser.ClassTarget target, Map<String, RedirectsParser.RedirectSet> redirects) {
        List<String> sets = target.getSets();
        for (String set : sets) {
            for (TypeRedirect typeRedirect : redirects.get(set).getTypeRedirects()) {
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
                return;
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

    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        RedirectsParser.ClassTarget classTarget = new RedirectsParser.ClassTarget(targetClassName);
        List<RedirectsParser.RedirectSet> redirectSets = new ArrayList<>();

        findRedirectSets(targetClassName, targetClass, redirectSets);
        buildClassTarget(targetClass, classTarget);

        this.transformer.transformClass(targetClass, classTarget, redirectSets);
    }

    private void findRedirectSets(String targetClassName, ClassNode targetClass, List<RedirectsParser.RedirectSet> redirectSets) {
        if (targetClass.invisibleAnnotations == null) {
            return;
        }
        for (AnnotationNode ann : targetClass.invisibleAnnotations) {
            if (!ann.desc.equals("Lio/github/opencubicchunks/cubicchunks/mixin/DasmRedirect;")) {
                continue;
            }
            // The name value pairs of this annotation. Each name value pair is stored as two consecutive
            // elements in the list. The name is a String, and the value may be a
            // Byte, Boolean, Character, Short, Integer, Long, Float, Double, String or org.objectweb.asm.Type,
            // or a two elements String array (for enumeration values), an AnnotationNode,
            // or a List of values of one of the preceding types. The list may be null if there is no name value pair.
            List<Object> values = ann.values;
            if (values == null) {
                redirectSets.add(redirectSetByName.get("general"));
                continue;
            }
            List<String> useSets = null;
            for (int i = 0, valuesSize = values.size(); i < valuesSize; i += 2) {
                String name = (String) values.get(i);
                Object value = values.get(i + 1);
                if (name.equals("value")) {
                    useSets = (List<String>) value;
                }
            }
            for (String useSet : useSets) {
                RedirectsParser.RedirectSet redirectSet = redirectSetByName.get(useSet);
                if (redirectSet == null) {
                    throw new IllegalArgumentException("No redirect set " + useSet + ", targetClass=" + targetClassName);
                }
                redirectSets.add(redirectSet);
            }
        }
    }

    private static void buildClassTarget(ClassNode targetClass, RedirectsParser.ClassTarget classTarget) {
        for (Iterator<MethodNode> iterator = targetClass.methods.iterator(); iterator.hasNext(); ) {
            MethodNode method = iterator.next();
            if (method.invisibleAnnotations == null) {
                continue;
            }
            for (AnnotationNode ann : method.invisibleAnnotations) {
                if (!ann.desc.equals("Lio/github/opencubicchunks/cubicchunks/mixin/TransformFrom;")) {
                    continue;
                }
                iterator.remove();

                // The name value pairs of this annotation. Each name value pair is stored as two consecutive
                // elements in the list. The name is a String, and the value may be a
                // Byte, Boolean, Character, Short, Integer, Long, Float, Double, String or org.objectweb.asm.Type,
                // or a two elements String array (for enumeration values), an AnnotationNode,
                // or a List of values of one of the preceding types. The list may be null if there is no name value pair.
                List<Object> values = ann.values;
                String targetName = null;
                boolean makeSyntheticAccessor = false;
                String desc = null;
                for (int i = 0, valuesSize = values.size(); i < valuesSize; i += 2) {
                    String name = (String) values.get(i);
                    Object value = values.get(i + 1);
                    if (name.equals("value")) {
                        targetName = (String) value;
                    } else if (name.equals("makeSyntheticAccessor")) {
                        makeSyntheticAccessor = (Boolean) value;
                    } else if (name.equals("signature")) {
                        desc = parseMethodDescriptor((AnnotationNode) value);
                    }
                }
                if (desc == null) {
                    int split = targetName.indexOf('(');
                    desc = targetName.substring(split);
                    targetName = targetName.substring(0, split);
                }
                RedirectsParser.ClassTarget.TargetMethod targetMethod = new RedirectsParser.ClassTarget.TargetMethod(
                    new Transformer.ClassMethod(Type.getObjectType(targetClass.name), new org.objectweb.asm.commons.Method(targetName, desc)),
                    method.name,
                    true, makeSyntheticAccessor
                );
                classTarget.addTarget(targetMethod);
            }
        }
    }

    private static String parseMethodDescriptor(AnnotationNode ann) {
        if (ann == null) {
            return null;
        }
        List<Object> values = ann.values;

        Type ret = null;
        List<Type> args = null;
        boolean useFromString = false;
        for (int i = 0, valuesSize = values.size(); i < valuesSize; i += 2) {
            String name = (String) values.get(i);
            Object value = values.get(i + 1);
            if (name.equals("ret")) {
                ret = (Type) value;
            } else if (name.equals("args")) {
                args = (List<Type>) value;
            } else if (name.equals("useFromString")) {
                useFromString = (Boolean) value;
            }
        }
        if (useFromString) {
            return null;
        }
        return Type.getMethodDescriptor(ret, args.toArray(new Type[0]));
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

        JsonObject setsJson = parseFileAsJson(fileName).getAsJsonObject();
        JsonElement sets = setsJson.get("sets");
        JsonElement globalImports = setsJson.get("imports");

        if (globalImports == null) {
            return redirectsParser.parseRedirectSet(sets.getAsJsonObject());
        } else {
            return redirectsParser.parseRedirectSet(sets.getAsJsonObject(), globalImports);
        }
    }
}