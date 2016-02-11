/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.fd.runtime;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

// This is based on the reflection parts of
//     com.google.devtools.build.android.incrementaldeployment.StubApplication,
// plus changes to compile on JDK 6.
//
// (The code to handle resource loading etc is different; see FileManager.)
//
// The original is
// https://cs.corp.google.com/codesearch/f/piper///depot/google3/third_party/bazel/src/tools/android/java/com/google/devtools/build/android/incrementaldeployment/StubApplication.java?cl=93287264
// Public (May 11 revision, ca96e11)
// https://github.com/google/bazel/blob/master/src/tools/android/java/com/google/devtools/build/android/incrementaldeployment/StubApplication.java

/**
 * A stub application that patches the class loader, then replaces itself with the real application
 * by applying a liberal amount of reflection on Android internals.
 * <p/>
 * <p>This is, of course, terribly error-prone. Most of this code was tested with API versions
 * 8, 10, 14, 15, 16, 17, 18, 19 and 21 on the Android emulator, a Nexus 5 running Lollipop LRX22C
 * and a Samsung GT-I5800 running Froyo XWJPE. The exception is {@code monkeyPatchAssetManagers},
 * which only works on Kitkat and Lollipop.
 * <p/>
 * <p>Note that due to a bug in Dalvik, this only works on Kitkat if ART is the Java runtime.
 * <p/>
 * <p>Unfortunately, if this does not work, we don't have a fallback mechanism: as soon as we
 * build the APK with this class as the Application, we are committed to going through with it.
 * <p/>
 * <p>This class should use as few other classes as possible before the class loader is patched
 * because any class loaded before it cannot be incrementally deployed.
 */
public class BootstrapApplication extends Application {
    public static final String LOG_TAG = "InstantRun";

    static {
        com.android.tools.fd.common.Log.logging =
                new com.android.tools.fd.common.Log.Logging() {
            @Override
            public void log(@NonNull Level level, @NonNull String string) {
                log(level, string, null /* throwable */);
            }

            @Override
            public boolean isLoggable(@NonNull Level level) {
                if (level == Level.SEVERE) {
                    return Log.isLoggable(BootstrapApplication.LOG_TAG, Log.ERROR);
                } else if (level == Level.FINE) {
                    return Log.isLoggable(BootstrapApplication.LOG_TAG, Log.VERBOSE);
                } else return Log.isLoggable(BootstrapApplication.LOG_TAG, Log.INFO);
            }

            @Override
            public void log(@NonNull Level level, @NonNull String string,
                    @Nullable Throwable throwable) {
                if (level == Level.SEVERE) {
                    if (throwable == null) {
                        Log.e(BootstrapApplication.LOG_TAG, string);
                    } else {
                        Log.e(BootstrapApplication.LOG_TAG, string, throwable);
                    }
                } else if (level == Level.FINE) {
                    if (Log.isLoggable(BootstrapApplication.LOG_TAG, Log.VERBOSE)) {
                        if (throwable == null) {
                            Log.v(BootstrapApplication.LOG_TAG, string);
                        } else {
                            Log.v(BootstrapApplication.LOG_TAG, string, throwable);
                        }
                    }
                } else if (Log.isLoggable(BootstrapApplication.LOG_TAG, Log.INFO)) {
                    if (throwable == null) {
                        Log.i(BootstrapApplication.LOG_TAG, string);
                    } else {
                        Log.i(BootstrapApplication.LOG_TAG, string, throwable);
                    }
                }
            }
        };
    }

    private String externalResourcePath;
    private Application realApplication;

    public BootstrapApplication() {
        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, String.format(
                    "BootstrapApplication created. Android package is %s, real application class is %s.",
                    AppInfo.applicationId, AppInfo.applicationClass));
        }
    }

    private void createResources(long apkModified) {
        // Look for changes stashed in the inbox folder while the server was not running
        FileManager.checkInbox();

        File file = FileManager.getExternalResourceFile();
        externalResourcePath = file != null ? file.getPath() : null;

        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, "Resource override is " + externalResourcePath);
        }

        if (file != null) {
            try {
                long resourceModified = file.lastModified();
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Resource patch last modified: " + resourceModified);
                    Log.i(LOG_TAG, "APK last modified: " + apkModified + " " +
                            (apkModified > resourceModified ? ">" : "<") + " resource patch");
                }

                if (apkModified == 0L || resourceModified <= apkModified) {
                    if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                        Log.i(LOG_TAG, "Ignoring resource file, older than APK");
                    }
                    externalResourcePath = null;
                }
            } catch (Throwable t) {
                Log.e(LOG_TAG, "Failed to check patch timestamps", t);
            }
        }
    }

    private static void setupClassLoaders(String codeCacheDir, long apkModified) {
        List<String> dexList = FileManager.getDexList(apkModified);

        // Make sure class loader finds these
        @SuppressWarnings("unused") Class<Server> server = Server.class;
        @SuppressWarnings("unused") Class<MonkeyPatcher> patcher = MonkeyPatcher.class;

        if (!dexList.isEmpty()) {
            try {
                long codeModified = 0L;
                String lastClass = dexList.get(0);
                if (new File(lastClass).getName().startsWith(Paths.DEX_SLICE_PREFIX)) {
                    // Dex slices are not sorted by modification time, so I need
                    // to look at all of them
                    for (String file : dexList) {
                        codeModified = Math.max(codeModified, new File(file).lastModified());
                    }
                } else {
                    codeModified = new File(lastClass).lastModified();
                }

                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Last code patch: " + lastClass);
                    Log.i(LOG_TAG, "APK last modified: " + apkModified + " " +
                            (apkModified > codeModified ? ">" : "<") + " " + codeModified
                            + " code patch");
                }

                if (apkModified == 0L || codeModified <= apkModified) {
                    if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                        Log.i(LOG_TAG, "Ignoring code patches, older than APK");
                    }
                    dexList = Collections.emptyList();
                }
            } catch (Throwable t) {
                Log.e(LOG_TAG, "Failed to check patch timestamps", t);
            }
        } else {
            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                Log.i(LOG_TAG, "No override .dex files found");
            }
        }

        if (!dexList.isEmpty()) {
            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                Log.i(LOG_TAG, "Bootstrapping class loader with dex list " + dexList);
            }

            String nativeLibraryPath = FileManager.getNativeLibraryFolder().getPath();
            ClassLoader classLoader = BootstrapApplication.class.getClassLoader();
            IncrementalClassLoader.inject(
                    classLoader,
                    nativeLibraryPath,
                    codeCacheDir,
                    dexList);
        }
    }

    private void createRealApplication() {
        if (AppInfo.applicationClass != null) {
            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                Log.i(LOG_TAG, "About to create real application of class name = " +
                        AppInfo.applicationClass);
            }

            try {
                @SuppressWarnings("unchecked")
                Class<? extends Application> realClass =
                        (Class<? extends Application>) Class.forName(AppInfo.applicationClass);
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Created delegate app class successfully : " + realClass +
                            " with class loader " + realClass.getClassLoader());
                }
                Constructor<? extends Application> constructor = realClass.getConstructor();
                realApplication = constructor.newInstance();
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Created real app instance successfully :" + realApplication);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } else {
            realApplication = new Application();
        }
    }

    @Override
    protected void attachBaseContext(Context context) {
        // As of Marshmallow, we use APK splits and don't need to rely on
        // reflection to inject classes and resources for coldswap
        //noinspection PointlessBooleanExpression
        if (!AppInfo.usingApkSplits) {
            String apkFile = context.getApplicationInfo().sourceDir;
            long apkModified = apkFile != null ? new File(apkFile).lastModified() : 0L;
            createResources(apkModified);
            setupClassLoaders(context.getCacheDir().getPath(), apkModified);
        }

        createRealApplication();

        // This is called from ActivityThread#handleBindApplication() -> LoadedApk#makeApplication().
        // Application#mApplication is changed right after this call, so we cannot do the monkey
        // patching here. So just forward this method to the real Application instance.
        super.attachBaseContext(context);

        if (realApplication != null) {
            try {
                Method attachBaseContext =
                        ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
                attachBaseContext.setAccessible(true);
                attachBaseContext.invoke(realApplication, context);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public void onCreate() {
        // As of Marshmallow, we use APK splits and don't need to rely on
        // reflection to inject classes and resources for coldswap
        //noinspection PointlessBooleanExpression
        if (!AppInfo.usingApkSplits) {
            MonkeyPatcher.monkeyPatchApplication(
                    BootstrapApplication.this, BootstrapApplication.this,
                    realApplication, externalResourcePath);
            MonkeyPatcher.monkeyPatchExistingResources(BootstrapApplication.this,
                    externalResourcePath, null);
        } else {
            // We still need to set the application instance in the LoadedApk etc
            // such that getApplication() returns the new application
            MonkeyPatcher.monkeyPatchApplication(
                    BootstrapApplication.this, BootstrapApplication.this,
                    realApplication, null);
        }
        super.onCreate();
        if (AppInfo.applicationId != null) {
            Server.create(AppInfo.applicationId, BootstrapApplication.this);
        }
        //CrashHandler.startCrashCatcher(this);

        if (realApplication != null) {
            realApplication.onCreate();
        }
    }
}
