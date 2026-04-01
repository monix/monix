/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.execution.atomic.internal;

public final class UnsafeAccess {
  public static final boolean IS_AVAILABLE = false;
  public static final boolean IS_ALLOWED = false;
  public static final boolean HAS_JAVA8_INTRINSICS = false;
  public static final boolean IS_OPENJDK_COMPATIBLE = false;

  public static Object getInstance() {
    throw new UnsupportedOperationException(
      "Legacy unsafe access is not used by monix.execution.atomic.internal on JDK 17+"
    );
  }

  private UnsafeAccess() {}
}
