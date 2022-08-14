package io.github.opencubicchunks.gradle;

import static org.objectweb.asm.Opcodes.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import io.github.opencubicchunks.dasm.MappingsProvider;
import io.github.opencubicchunks.dasm.RedirectsParser.ClassTarget;
import io.github.opencubicchunks.dasm.RedirectsParser.RedirectSet;
import io.github.opencubicchunks.dasm.RedirectsParser.RedirectSet.TypeRedirect;
import io.github.opencubicchunks.dasm.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

class CustomClassWriter {
    private final Transformer transformer = new Transformer(new MappingsProvider() {
        @Override
        public String mapFieldName(String namespace, String owner, String fieldName, String descriptor) {
            return fieldName;
        }

        @Override
        public String mapMethodName(String namespace, String owner, String methodName, String descriptor) {
            return methodName;
        }

        @Override
        public String mapClassName(String namespace, String className) {
            return className;
        }
    }, true);

    ClassReader reader;
    ClassWriter writer;

    RedirectSet redirectSet;

    public CustomClassWriter(byte[] b) throws IOException {
        reader = new ClassReader(b);
        writer = new ClassWriter(0);

        redirectSet = new RedirectSet("1");
        redirectSet.addRedirect(new TypeRedirect("io.github.opencubicchunks.cc_core.minecraft.MCBitStorage", "net/minecraft/util/BitStorage"));
        redirectSet.addRedirect(new TypeRedirect("io.github.opencubicchunks.cc_core.minecraft.MCBlockGetter", "net/minecraft/world/level/BlockGetter"));
        redirectSet.addRedirect(new TypeRedirect("io.github.opencubicchunks.cc_core.minecraft.MCBlockPos", "net/minecraft/core/BlockPos"));
        redirectSet.addRedirect(new TypeRedirect("io.github.opencubicchunks.cc_core.minecraft.MCBlockState", "net/minecraft/world/level/block/state/BlockState"));
        redirectSet.addRedirect(new TypeRedirect("io.github.opencubicchunks.cc_core.minecraft.MCChunkPos", "net/minecraft/world/level/ChunkPos"));
        redirectSet.addRedirect(new TypeRedirect("io.github.opencubicchunks.cc_core.minecraft.MCEntity", "net/minecraft/world/entity/Entity"));
        redirectSet.addRedirect(new TypeRedirect("io.github.opencubicchunks.cc_core.minecraft.MCLevelHeightAccessor", "net/minecraft/world/level/LevelHeightAccessor"));
        redirectSet.addRedirect(new TypeRedirect("io.github.opencubicchunks.cc_core.minecraft.MCSectionPos", "net/minecraft/core/SectionPos"));
        redirectSet.addRedirect(new TypeRedirect("io.github.opencubicchunks.cc_core.minecraft.MCVec3i", "net/minecraft/core/Vec3i"));
    }

    public Optional<byte[]> changeSignatures() {

        ClassNode classNode = new ClassNode(ASM9);
        reader.accept(classNode, 0);

        if (redirectSet.getTypeRedirects().stream().anyMatch(typeRedirect -> typeRedirect.srcClassName().equals(classNode.name.replace("/", ".")))) {
            return Optional.empty();
        }

        ClassTarget classTarget = new ClassTarget(classNode.name);

        classTarget.targetWholeClass();

        transformer.transformClass(classNode, classTarget, Collections.singletonList(redirectSet));

        classNode.accept(writer);
        return Optional.of(writer.toByteArray());
    }
}

public class CoreTransformer {
    public static void transformCoreLibrary(File coreJar, File outputCoreJar) {
        try {
            Map<String, byte[]> originalClassBytes = loadClasses(coreJar);
            Map<String, byte[]> transformedClassBytes = new HashMap<>();
            for (Map.Entry<String, byte[]> entry : originalClassBytes.entrySet()) {
                String name = entry.getKey();

                try {
                    Optional<byte[]> bytes = new CustomClassWriter(entry.getValue()).changeSignatures();
                    bytes.ifPresent(value -> transformedClassBytes.put(name, value));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw t;
                }
            }
            saveAsJar(transformedClassBytes, outputCoreJar);
            System.out.printf("Writing jar %s\n", outputCoreJar);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Map<String, byte[]> loadClasses(File jarFile) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        JarFile jar = new JarFile(jarFile);
        Stream<JarEntry> str = jar.stream();
        str.forEach(z -> readJar(jar, z, classes));
        jar.close();
        return classes;
    }

    private static void readJar(JarFile jar, JarEntry entry, Map<String, byte[]> classes) {
        String name = entry.getName();
        try (InputStream inputStream = jar.getInputStream(entry)){
            if (name.endsWith(".class")) {
                byte[] bytes = inputStream.readAllBytes();
                classes.put(name, bytes);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void saveAsJar(Map<String, byte[]> outBytes, File file) {
        try {
            JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(file));
            for (String entry : outBytes.keySet()) {
                String ext = entry.contains(".") ? "" : ".class";
                outputStream.putNextEntry(new ZipEntry(entry + ext));
                outputStream.write(outBytes.get(entry));
                outputStream.closeEntry();
            }
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}