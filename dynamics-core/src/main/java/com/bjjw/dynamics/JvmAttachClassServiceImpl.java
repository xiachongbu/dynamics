package com.bjjw.dynamics;

import net.bytebuddy.agent.VirtualMachine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * attach 当前 JVM 并加载临时 agent jar，由 agent 侧完成 retransform。
 */
public class JvmAttachClassServiceImpl implements ClassService {

    private static final String AGENT_CLASS = "com.bjjw.dynamics.agent.RedefineClass";

    private static final String[] AGENT_RESOURCES = {
            "com/bjjw/dynamics/agent/RedefineClass.class",
            "com/bjjw/dynamics/agent/ClassTransformer.class"
    };

    private File agentJar;

    @Override
    public void updateClass(String className, String classPath) {
        try {
            final VirtualMachine virtualMachine = VirtualMachine.ForHotSpot.attach(
                    ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
            try {
                virtualMachine.loadAgent(agentJar().getAbsolutePath(), className + "-" + classPath);
            } finally {
                virtualMachine.detach();
            }
        } catch (IOException e) {
            throw new IllegalStateException("attach 当前 JVM 失败, 类未替换: " + className, e);
        }
    }

    private File agentJar() throws IOException {
        if (agentJar == null || !agentJar.exists()) {
            agentJar = createAgentJar();
        }
        return agentJar;
    }

    private File createAgentJar() throws IOException {
        final File jarFile = File.createTempFile("dynamics-agent-", ".jar");
        jarFile.deleteOnExit();

        final Manifest manifest = new Manifest();
        final Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Agent-Class", AGENT_CLASS);
        attributes.putValue("Can-Redefine-Classes", "true");
        attributes.putValue("Can-Retransform-Classes", "true");

        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
            for (String resource : AGENT_RESOURCES) {
                try (InputStream in = JvmAttachClassServiceImpl.class.getClassLoader().getResourceAsStream(resource)) {
                    if (in == null) {
                        throw new IllegalStateException("classpath 缺少 agent 字节码 " + resource
                                + "，请确认 dynamics-agent 依赖未被排除且打包时保留了 jar 内资源");
                    }
                    out.putNextEntry(new JarEntry(resource));
                    final byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.closeEntry();
                }
            }
        }
        return jarFile;
    }
}