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

package com.android.repository.impl.meta;

import com.android.annotations.NonNull;
import com.android.repository.api.ElementFactory;
import com.android.repository.api.Repository;

import javax.xml.bind.JAXBElement;

/**
 * Factory for creating types used by the generic schema.
 */
public abstract class GenericFactory extends ElementFactory<Repository> {

    @NonNull
    public abstract TypeDetails.GenericType createGenericDetailsType();

    @NonNull
    @Override
    public abstract JAXBElement<Repository> generateElement(@NonNull Repository value);
}