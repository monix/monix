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

/**
 * Provides access to `sun.misc.Unsafe`.
 *
 * DO NOT use unless you know what you're doing.
 */
public final class UnsafeAccess {
  /**
   * Initialized and reusable reference for `sun.misc.Unsafe`.
   */
  public static final Object UNSAFE;

  public static void checkIfUnsafeIsAvailable() {
    if (UNSAFE == null)
      throw new AssertionError(
        "Platform does not support sun.misc.Unsafe, " +
        "please file a bug report for the Monix project " +
        "(see https://monix.io)"
      );
  }

  /**
   * True in case the underlying platform supports Java 8's
   * `Unsafe` features for platform intrinsics.
   */
  public static final boolean IS_JAVA_8;

  static {
    Object instance = null;
    boolean isJava8 = false;

    try {
      Class<?> cls = Class.forName("sun.misc.Unsafe", true, UnsafeAccess.class.getClassLoader());
      try {
        Field field = cls.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        instance = field.get(null);
      }
      catch (Exception ex) {
        if (!NonFatal.apply(ex)) {
          throw ex;
        }
        else {
          // Workaround for older Android versions or other non-OpenJDK
          // implementations that may not have a `theUnsafe` instance
          Constructor<?> c = cls.getDeclaredConstructor();
          c.setAccessible(true);
          instance = c.newInstance();
        }
      }

      boolean supportsGetAndSet = false;
      try {
        cls.getMethod("getAndSetObject", Object.class, Long.TYPE, Object.class);
        supportsGetAndSet = true;
      }
      catch (Exception e) {
        if (!NonFatal.apply(e)) throw e;
      }

      boolean supportsGetAndAddInt = false;
      try {
        cls.getMethod("getAndAddInt", Object.class, Long.TYPE, Integer.TYPE);
        supportsGetAndAddInt = true;
      }
      catch (Exception e) {
        if (!NonFatal.apply(e)) throw e;
      }

      boolean supportsGetAndAddLong = false;
      try {
        cls.getMethod("getAndAddLong", Object.class, Long.TYPE, Long.TYPE);
        supportsGetAndAddLong = true;
      }
      catch (Exception e) {
        if (!NonFatal.apply(e)) throw e;
      }

      isJava8 = supportsGetAndSet &&
        supportsGetAndAddInt &&
        supportsGetAndAddLong;
    }
    catch (Exception ex) {
      instance = null;
      isJava8 = false;
      if (!NonFatal.apply(ex))
        throw new RuntimeException(ex);
    }
    finally {
      UNSAFE = instance;
      IS_JAVA_8 = isJava8;
    }
  }
}
