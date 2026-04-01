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

final class LeftRight128JavaXBoxedLong extends LeftRight128JavaXBoxedLongImpl {
  public volatile long r1, r2, r3, r4, r5, r6, r7, r8 = 11;
  @Override public long sum() {
    return p1 + p2 + p3 + p4 + p5 + p6 + p7 +
      r1 + r2 + r3 + r4 + r5 + r6 + r7 + r8;
  }

  LeftRight128JavaXBoxedLong(long initialValue) {
    super(initialValue);
  }
}

abstract class LeftRight128JavaXBoxedLongImpl extends LeftPadding56 implements VarHandleBoxedLong {
  private static final VarHandle VALUE_VH;

  static {
    try {
      VALUE_VH = MethodHandles.lookup().findVarHandle(LeftRight128JavaXBoxedLongImpl.class, "value", long.class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private long value;

  LeftRight128JavaXBoxedLongImpl(long initialValue) {
    this.value = initialValue;
  }

  public final VarHandle valueHandle() {
    return VALUE_VH;
  }
}
