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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.impl.meta.Archive;

/**
 * An package available for download. In addition to what's provided by {@link RepoPackage},
 * {@code RemotePackage} has an associated {@link RepositorySource} and archives.
 */
public interface RemotePackage extends RepoPackage {

    /**
     * @return The {@code RepositorySource} from which we got this package.
     */
    @NonNull
    RepositorySource getSource();

    /**
     * @param source The {@code RepositorySource} from which we got this package.
     */
    void setSource(@NonNull RepositorySource source);

    /**
     * @return The archive in this package compatible with the current hardware, OS, and JDK,
     * or {@code null} if there is none.
     */
    @Nullable
    Archive getArchive();
}