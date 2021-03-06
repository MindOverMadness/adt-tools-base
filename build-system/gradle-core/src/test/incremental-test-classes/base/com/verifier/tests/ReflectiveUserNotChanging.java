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

package com.verifier.tests;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reflective API user class that will only change its authorized API.
 */
public class ReflectiveUserNotChanging {

    public String useReflectionOnPublicMethod()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method hiddenGem = getClass().getDeclaredMethod("hiddenGem", String.class);
        return (String) hiddenGem.invoke(this, "stressed");
    }

    public String hiddenGem(String param) {
        return new StringBuilder(param).reverse().toString();
    }
}
