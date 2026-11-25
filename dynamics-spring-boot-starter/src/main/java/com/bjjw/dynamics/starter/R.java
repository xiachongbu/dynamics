package com.bjjw.dynamics.starter;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class R<T> {

    private final int code;
    private final String msg;
    private final T data;

    public static <T> R<T> ok(T data) {
        return new R<>(200, "成功", data);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(500, msg, null);
    }
}
