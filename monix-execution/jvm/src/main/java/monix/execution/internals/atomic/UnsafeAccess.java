/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
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

package monix.execution.internals.atomic;

import monix.execution.misc.NonFatal;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Provides access to `sun.misc.Unsafe`.
 *
 * DO NOT use unless you know what you're doing.
 */
public final class UnsafeAccess {
  private static final Object UNSAFE;

  /** True in case the underlying platform supports
   * `sun.misc.Unsafe`, `false` otherwise.
   *
   * In case [[IS_ALLOWED]] is set to `false`, then
   * [[IS_AVAILABLE]] is also going to be set to `false`.
   */
  public static final boolean IS_AVAILABLE;

  /**
   * True in case the `sun.misc.Unsafe` usage is disabled
   * by means of setting `-Dmonix.environment.canUseUnsafe=false`.
   *
   * In case this is set to `false`, then [[IS_AVAILABLE]] is
   * also going to be set to `false`.
   */
  public static final boolean IS_ALLOWED;

  /**
   * True in case the underlying platform supports Java 8's
   * `Unsafe` features for platform intrinsics.
   */
  public static final boolean HAS_JAVA8_INTRINSICS;

  /**
   * Some platforms do not expose a `theUnsafe` private reference
   * to a `sun.misc.Unsafe` instance, but unfortunately some libraries
   * (notably version 2.0 of JCTools) depends on this.
   *
   * This reference is set to `true` in case `Unsafe.theUnsafe` exists,
   * or `false` otherwise.
   */
  public static final boolean IS_OPENJDK_COMPATIBLE;

  /**
   * Returns a reusable reference for `sun.misc.Unsafe`.
   */
  public static Object getInstance() {
    if (UNSAFE == null)
      throw new AssertionError(
        "Platform does not support sun.misc.Unsafe, " +
        "please file a bug report for the Monix project " +
        "(see https://monix.io)"
      );

    return UNSAFE;
  }

  private static boolean isUnsafeAllowed() {
    String env = System.getProperty("monix.environment.canUseUnsafe", "").trim().toLowerCase();
    boolean disabled = env.equals("no") || env.equals("false") || env.equals("0");
    return !disabled;
  }

  static {
    Object instance = null;
    boolean isJava8 = false;
    boolean isAllowed = false;
    boolean isOpenJDKCompatible = false;

    try {
      isAllowed = isUnsafeAllowed();

      if (isAllowed) {
        Class<?> cls = Class.forName("sun.misc.Unsafe", true, UnsafeAccess.class.getClassLoader());
        try {
          Field field = cls.getDeclaredField("theUnsafe");
          field.setAccessible(true);
          instance = field.get(null);
          if (instance == null) throw null;
          isOpenJDKCompatible = true;
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
    }
    catch (Exception ex) {
      instance = null;
      isJava8 = false;
      if (!NonFatal.apply(ex))
        throw new RuntimeException(ex);
    }
    finally {
      UNSAFE = instance;
      IS_AVAILABLE = instance != null;
      IS_ALLOWED = isAllowed;
      HAS_JAVA8_INTRINSICS = isJava8;
      IS_OPENJDK_COMPATIBLE = isOpenJDKCompatible;
    }
  }
}
