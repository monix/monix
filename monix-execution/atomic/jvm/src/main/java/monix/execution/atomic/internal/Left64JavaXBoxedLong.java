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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

final class Left64JavaXBoxedLong extends LeftPadding56 implements VarHandleBoxedLong {
  private static final VarHandle VALUE_VH;

  static {
    try {
      VALUE_VH = MethodHandles.lookup().findVarHandle(Left64JavaXBoxedLong.class, "value", long.class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private long value;

  Left64JavaXBoxedLong(long initialValue) {
    this.value = initialValue;
  }

  public VarHandle valueHandle() {
    return VALUE_VH;
  }
}
