package io.github.opencubicchunks.cubicchunks.mixin.transform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.Permission;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.fabricmc.loader.launch.common.FabricLauncherBase;

/**
 * This is mostly taken from Fabric-ASM. It is used to load classes which are generated at runtime
 */
public class CustomClassAdder {
    private static final String PROTOCOL = "ccadder";
    private static final URL URL;

    public static final Map<String, byte[]> knownData = new HashMap<>();
    public static final Map<String, Supplier<byte[]>> lazyData = new HashMap<>();

    static {
        try {
            URL = new URL(PROTOCOL, null, -1, "/", new CCStreamHandler());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Couldn't create URL", e);
        }
    };

    public static void addCustomClass(String className, byte[] bytes) {
        className = className.replace('.', '/');

        if(!className.endsWith(".class")){
            className += ".class";
        }

        if(!className.startsWith("/")){
            className = "/" + className;
        }

        knownData.put(className, bytes);
    }

    public static void addCustomClass(String className, Supplier<byte[]> lazyBytes) {
        className = className.replace('.', '/');

        if(!className.endsWith(".class")){
            className += ".class";
        }

        if(!className.startsWith("/")){
            className = "/" + className;
        }

        lazyData.put(className, lazyBytes);
    }

    public static void addUrlToClassLoader(){
        System.out.println("Adding custom class URL to class loader");
        FabricLauncherBase.getLauncher().propose(URL);
    }

    private static String formatClassName(String className){
        className = className.replace('.', '/');

        if(!className.endsWith(".class")){
            className += ".class";
        }

        if(!className.startsWith("/")){
            className = "/" + className;
        }

        return className;
    }

    public static @Nullable byte[] find(String className) {
        className = formatClassName(className);

        if(knownData.containsKey(className)){
            return knownData.get(className);
        }else if(lazyData.containsKey(className)){
            byte[] evaluated = lazyData.get(className).get();
            knownData.put(className, evaluated);
            lazyData.remove(className);
            return evaluated;
        }

        return null;
    }

    private static class CCStreamHandler extends URLStreamHandler{
        @Override
        protected @Nullable URLConnection openConnection(URL url) {
            byte[] data = find(url.getPath());

            if(data == null){
                return null;
            }

            return new CCConnection(url, data);
        }
    }

    private static class CCConnection extends URLConnection {
        private final byte[] data;

        public CCConnection(URL url, byte[] data) {
            super(url);
            this.data = data;
        }

        @Override public void connect() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Permission getPermission() {
            return null;
        }

        @Override public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(data);
        }
    }
}
