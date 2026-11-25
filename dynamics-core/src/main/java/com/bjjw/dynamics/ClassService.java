package com.bjjw.dynamics;

/**
 * 类热替换入口。
 */
public interface ClassService {

    /**
     * 把已加载的类替换为指定 class 文件的内容。
     *
     * @param className 完整类名，如 com.example.UserService
     * @param classPath 新字节码的 class 文件绝对路径
     */
    void updateClass(String className, String classPath);

    static ClassService getInstance() {
        return ClassServiceFactory.getInstance();
    }
}