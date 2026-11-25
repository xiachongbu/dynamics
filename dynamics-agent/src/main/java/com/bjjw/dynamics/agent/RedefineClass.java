package com.bjjw.dynamics.agent;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 由 attach 机制加载的 agent 入口，把已加载类的字节码替换为指定 class 文件的内容。
 */
public final class RedefineClass {

    private static final Logger log = Logger.getLogger(RedefineClass.class.getName());

    public static void agentmain(String agentArgs, Instrumentation inst) {
        // limit=2：类名不含 '-'，而临时目录路径可能含 '-'，按首个 '-' 切分才不会被路径截断
        final String[] args = agentArgs.split("-", 2);
        final String className = args[0];
        final byte[] bytecode;
        try {
            bytecode = Files.readAllBytes(Paths.get(args[1]));
        } catch (IOException e) {
            log.log(Level.SEVERE, "dynamics => 读取 class 文件失败, 未执行替换: " + args[1], e);
            return;
        }

        inst.addTransformer(new ClassTransformer(className, bytecode), true);

        int patched = 0;
        for (Class<?> loaded : inst.getAllLoadedClasses()) {
            if (!loaded.getName().equals(className)) {
                continue;
            }
            try {
                inst.retransformClasses(loaded);
                patched++;
            } catch (Exception e) {
                log.log(Level.SEVERE, "dynamics => 类热替换失败: " + className, e);
            }
        }
        if (patched == 0) {
            log.warning("dynamics => 目标类未被加载或不可修改, 未执行替换: " + className);
            return;
        }
        log.info("dynamics => 类热替换完成: " + className + ", 已替换 " + patched + " 个已加载副本");
    }
}