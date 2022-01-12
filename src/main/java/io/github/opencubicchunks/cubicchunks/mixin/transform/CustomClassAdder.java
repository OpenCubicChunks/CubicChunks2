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

import net.fabricmc.loader.launch.common.FabricLauncherBase;

/**
 * This is mostly taken from Fabric-ASM. It is used to load classes which are generated at runtime
 */
public class CustomClassAdder {
    private static final String PROTOCOL = "ccadder";
    private static final URL URL;

    static {
        try {
            URL = new URL(PROTOCOL, null, -1, "/", new CCStreamHandler());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Couldn't create URL", e);
        }
    }

    private static final Map<String, byte[]> data = new HashMap<>();

    public static void addCustomClass(String className, byte[] bytes) {
        className = className.replace('.', '/');

        if(!className.endsWith(".class")){
            className += ".class";
        }

        if(!className.startsWith("/")){
            className = "/" + className;
        }

        data.put(className, bytes);
    }

    public static void addUrlToClassLoader(){
        System.out.println("Adding custom class URL to class loader");
        FabricLauncherBase.getLauncher().propose(URL);
    }

    private static class CCStreamHandler extends URLStreamHandler{

        @Override protected URLConnection openConnection(URL url) throws IOException {
            if(!data.containsKey(url.getPath())) {
                return null;
            }

            System.out.println("Returning custom class URL connection for " + url.getPath());
            return new CCConnection(url, data.get(url.getPath()));
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
