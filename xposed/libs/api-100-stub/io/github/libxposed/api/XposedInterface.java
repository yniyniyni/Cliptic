package io.github.libxposed.api;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * Compile-only stub of the libxposed modern API, apiVersion 100, as implemented by the
 * Vector (JingMatrix LSPosed fork) framework on the target device.
 *
 * This is NOT the published Maven artifact `io.github.libxposed:api` (that jumped to apiVersion
 * 101 with a different, Chain-based Hooker and a no-arg XposedModule constructor, which is
 * incompatible with the device). The signatures here were recovered verbatim from the on-device
 * framework dex (`/data/adb/modules/zygisk_vector/framework/lspd.dex`) and cover exactly the
 * members the Cliptic xposed module compiles against. At runtime the framework provides the real
 * classes, so only the signatures need to match.
 */
public interface XposedInterface {
    int API = 100;

    MethodUnhooker hook(Method origin, Class<? extends Hooker> hooker);

    MethodUnhooker hook(Method origin, int priority, Class<? extends Hooker> hooker);

    void log(String message);

    void log(String message, Throwable throwable);

    /** Marker interface; the framework discovers static {@code before}/{@code after} methods. */
    interface Hooker {
    }

    interface MethodUnhooker {
        Object getOrigin();

        void unhook();
    }

    interface BeforeHookCallback {
        Member getMember();

        Object getThisObject();

        Object[] getArgs();

        void returnAndSkip(Object returnValue);

        void throwAndSkip(Throwable throwable);
    }

    interface AfterHookCallback {
        Member getMember();

        Object getThisObject();

        Object[] getArgs();

        Object getResult();

        Throwable getThrowable();

        boolean isSkipped();

        void setResult(Object result);

        void setThrowable(Throwable throwable);
    }
}
