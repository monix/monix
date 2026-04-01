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

final class LeftRight256JavaXBoxedInt extends LeftRight256JavaXBoxedIntImpl {
  public volatile long r01, r02, r03, r04, r05, r06, r07, r08 = 7;
  public volatile long r09, r10, r11, r12, r13, r14, r15, r16 = 8;
  @Override public long sum() {
    return
      p01 + p02 + p03 + p04 + p05 + p06 + p07 + p08 +
      p09 + p10 + p11 + p12 + p13 + p14 + p15 +
      r01 + r02 + r03 + r04 + r05 + r06 + r07 + r08 +
      r09 + r10 + r11 + r12 + r13 + r14 + r15 + r16;
  }

  LeftRight256JavaXBoxedInt(int initialValue) {
    super(initialValue);
  }
}

abstract class LeftRight256JavaXBoxedIntImpl extends LeftPadding120 implements VarHandleBoxedInt {
  private static final VarHandle VALUE_VH;

  static {
    try {
      VALUE_VH = MethodHandles.lookup().findVarHandle(LeftRight256JavaXBoxedIntImpl.class, "value", int.class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private int value;

  LeftRight256JavaXBoxedIntImpl(int initialValue) {
    this.value = initialValue;
  }

  public final VarHandle valueHandle() {
    return VALUE_VH;
  }
}
