package io.github.opencubicchunks.gradle;

import static org.objectweb.asm.Opcodes.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import io.github.opencubicchunks.dasm.MappingsProvider;
import io.github.opencubicchunks.dasm.RedirectsParser.ClassTarget;
import io.github.opencubicchunks.dasm.RedirectsParser.RedirectSet;
import io.github.opencubicchunks.dasm.RedirectsParser.RedirectSet.TypeRedirect;
import io.github.opencubicchunks.dasm.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

class CustomClassWriter {
    private final Transformer transformer = new Transformer(MappingsProvider.IDENTITY, true);

    private final RedirectSet redirectSet;

    public CustomClassWriter(RedirectSet redirectSet) throws IOException {
        this.redirectSet = redirectSet;
    }

    /**
     * @param inputClassNode input class to transform
     * @return If not present, the class should not be added to the output
     */
    public Optional<ClassNode> transformClass(ClassNode inputClassNode) {

        if (redirectSet.getTypeRedirects().stream().anyMatch(typeRedirect -> typeRedirect.srcClassName().equals(inputClassNode.name.replace("/", ".")))) {
            return Optional.empty();
        }

        ClassTarget classTarget = new ClassTarget(inputClassNode.name);
        classTarget.targetWholeClass();
        transformer.transformClass(inputClassNode, classTarget, Collections.singletonList(redirectSet));

        return Optional.of(inputClassNode);
    }
}

public class CoreTransformer {
    public static void transformCoreLibrary(File coreJar, File outputCoreJar) {
        try {
            List<ClassNode> classNodes = loadClasses(coreJar);
            List<ClassNode> outputClassNodes = new ArrayList<>();

            // Scans through all class nodes and looks for @DeclaresClass
            // adds type redirects from the class node to the specified DeclaresClass#value
            RedirectSet redirectSet = new RedirectSet("");
            for (ClassNode classNode : classNodes) {
                if (classNode.visibleAnnotations == null) {
                    continue;
                }

                for (AnnotationNode visibleAnnotation : classNode.visibleAnnotations) {
                    if (visibleAnnotation.desc.contains("DeclaresClass")) {
                        List<Object> values = visibleAnnotation.values;
                        assert (values.size() & 1) == 0;
                        for (int i = 0; i < values.size(); i+=2) {
                            String key = (String) values.get(i);
                            Object value = values.get(i+1);

                            if (key.equals("value")) {
                                if (value instanceof String stringValue) {
                                    redirectSet.addRedirect(new TypeRedirect(classNode.name.replace("/", "."), stringValue.replace(".", "/")));
                                }
                            }
                        }
                    }
                }
            }

            // transformation
            CustomClassWriter customClassWriter = new CustomClassWriter(redirectSet);
            classNodes.parallelStream().forEach(classNode -> customClassWriter.transformClass(classNode).ifPresent(outputClassNodes::add));

            saveAsJar(outputClassNodes, coreJar, outputCoreJar);
            System.out.printf("Writing jar %s\n", outputCoreJar);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads all class entries from a jar outputJar
     */
    private static List<ClassNode> loadClasses(File jarFile) throws IOException {
        try (JarFile jar = new JarFile(jarFile); Stream<JarEntry> str = jar.stream()) {
            return str.flatMap(z -> readJarClasses(jar, z).stream())
                .map(CoreTransformer::classNodeFromBytes)
                .collect(Collectors.toList());
        }
    }

    private static ClassNode classNodeFromBytes(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode(ASM9);
        reader.accept(classNode, 0);
        return classNode;
    }

    private static Optional<byte[]> readJarClasses(JarFile jar, JarEntry entry) {
        String name = entry.getName();
        try (InputStream inputStream = jar.getInputStream(entry)){
            if (name.endsWith(".class")) {
                return Optional.of(inputStream.readAllBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Optional.empty();
    }

    /**
     * Takes a list of class nodes and writes them to the output outputJar
     *
     * All non-class entries from the specified input jar are also written to the output jar
     */
    static void saveAsJar(List<ClassNode> classNodes, File inputJar, File outputJar) {
        try (JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(outputJar))) {
            Map<String, byte[]> nonClassEntries = loadNonClasses(inputJar);

            // write all non-class entries from the input jar
            for (Map.Entry<String, byte[]> e : nonClassEntries.entrySet()) {
                outputStream.putNextEntry(new ZipEntry(e.getKey()));
                outputStream.write(e.getValue());
                outputStream.closeEntry();
            }

            // write all class nodes
            for (ClassNode classNode : classNodes) {
                ClassWriter writer = new ClassWriter(0);
                classNode.accept(writer);
                byte[] bytes = writer.toByteArray();

                outputStream.putNextEntry(new ZipEntry(classNode.name));

                outputStream.putNextEntry(new ZipEntry(classNode.name + ".class"));
                outputStream.write(bytes);
                outputStream.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Loads all NON-class entries from a jar outputJar
     */
    private static void readNonJars(JarFile jar, JarEntry entry, Map<String, byte[]> nonClasses) {
        String name = entry.getName();
        try (InputStream inputStream = jar.getInputStream(entry)){
            if (!name.endsWith(".class")) {
                byte[] bytes = inputStream.readAllBytes();
                nonClasses.put(name, bytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, byte[]> loadNonClasses(File jarFile) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        JarFile jar = new JarFile(jarFile);
        Stream<JarEntry> str = jar.stream();
        str.forEach(z -> readNonJars(jar, z, classes));
        jar.close();
        return classes;
    }
}