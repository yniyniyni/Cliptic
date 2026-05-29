package io.github.libxposed.api;

import android.content.pm.ApplicationInfo;

/** Compile-only stub; see {@link XposedInterface} for provenance. */
public interface XposedModuleInterface {

    default void onPackageLoaded(PackageLoadedParam param) {
    }

    default void onSystemServerLoaded(SystemServerLoadedParam param) {
    }

    interface ModuleLoadedParam {
        boolean isSystemServer();

        String getProcessName();
    }

    interface PackageLoadedParam {
        String getPackageName();

        ApplicationInfo getApplicationInfo();

        boolean isFirstPackage();

        ClassLoader getClassLoader();

        ClassLoader getDefaultClassLoader();
    }

    interface SystemServerLoadedParam {
        ClassLoader getClassLoader();
    }
}
