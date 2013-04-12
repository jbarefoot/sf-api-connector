/*
 * Copyright © 2010. Team Lazer Beez (http://teamlazerbeez.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.teamlazerbeez.crm.sf.core;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * Represents an SF record Id. Remember to use .equals(Id otherID) to check equality.
 *
 * @author Marshall Pierce <marshall@teamlazerbeez.com>
 */
@Immutable
public final class Id {

    /**
     * the sf record id string
     */
    @Nonnull
    private final String idStr;

    /**
     * The id string this Id instance was constructed with.  Useful if you want to get at the 18 character id returned by the API, which is unique even with case insensitive comparisons
     */
    @Nonnull
    private final String originalIdStr;

    /**
     * @param id the sf record id to wrap. Must not be null. It can be 15 or 18 characters long, but it will be
     *           truncated to 15 characters either way.
     */
    public Id(@Nonnull String id) {
        if (id == null) {
            throw new NullPointerException("An Id must have a non-null string");
        }
        originalIdStr = id;

        if (id.length() != 15 && id.length() != 18) {
            throw new IllegalArgumentException(
                    "Salesforce Ids must be either 15 or 18 characters, was <" + id + "> (" + id.length() + ")");
        }

        this.idStr = id.substring(0, 15);
    }

    public String getKeyPrefix() {
        return idStr.substring(0, 3);
    }

    /**
     * @return 15-character case sensitive id string
     */
    @Override
    @Nonnull
    public String toString() {
        return this.idStr;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Id) {
            return this.idStr.equals(((Id) other).idStr);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.idStr.hashCode();
    }


}
