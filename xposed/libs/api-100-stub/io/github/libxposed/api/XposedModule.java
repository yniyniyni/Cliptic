package io.github.libxposed.api;

import java.lang.reflect.Method;

/**
 * Compile-only stub; see {@link XposedInterface} for provenance.
 *
 * On the device the framework instantiates the concrete module subclass via the
 * {@code (XposedInterface, ModuleLoadedParam)} constructor and provides real implementations of
 * {@link #hook} / {@link #log}. The bodies here are never executed.
 */
public abstract class XposedModule implements XposedInterface, XposedModuleInterface {

    public XposedModule(XposedInterface base, ModuleLoadedParam param) {
    }

    @Override
    public MethodUnhooker hook(Method origin, Class<? extends Hooker> hooker) {
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public MethodUnhooker hook(Method origin, int priority, Class<? extends Hooker> hooker) {
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public void log(String message) {
    }

    @Override
    public void log(String message, Throwable throwable) {
    }
}
