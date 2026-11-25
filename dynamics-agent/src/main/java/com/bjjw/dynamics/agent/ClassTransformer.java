package com.bjjw.dynamics.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * 只对目标类返回新字节码，其余类原样放行。
 */
class ClassTransformer implements ClassFileTransformer {

    private final String targetClassName;
    private final byte[] bytecode;

    ClassTransformer(String targetClassName, byte[] bytecode) {
        this.targetClassName = targetClassName;
        this.bytecode = bytecode;
    }

    @Override
    public byte[] transform(ClassLoader loader, String internalName, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (internalName == null || !targetClassName.equals(internalName.replace('/', '.'))) {
            return classfileBuffer;
        }
        return bytecode;
    }
}