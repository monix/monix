/*
 * Copyright (c) 2016 by its authors. Some rights reserved.
 * See the project homepage at: https://sincron.org
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

package monix.execution.misc;

import scala.util.control.NonFatal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public final class UnsafeAccess {
    public static final sun.misc.Unsafe UNSAFE;
    public static final boolean SUPPORTS_GET_AND_SET;

    static {
        sun.misc.Unsafe instance;
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            instance = (sun.misc.Unsafe) field.get(null);
        } catch (Exception e) {
            if (!NonFatal.apply(e)) throw new RuntimeException(e);
            else try {
                Constructor<sun.misc.Unsafe> c = sun.misc.Unsafe.class.getDeclaredConstructor();
                c.setAccessible(true);
                instance = c.newInstance();
            } catch (Exception again) {
                throw new RuntimeException(again);
            }
        }

        UNSAFE = instance;
        boolean supportsGetAndSet = false;
        try {
            sun.misc.Unsafe.class.getMethod("getAndSetObject", Object.class, Long.TYPE,Object.class);
            supportsGetAndSet = true;
        } catch (Exception e) {
            if (!NonFatal.apply(e)) throw new RuntimeException(e);
        }

        SUPPORTS_GET_AND_SET = supportsGetAndSet;
    }
}
