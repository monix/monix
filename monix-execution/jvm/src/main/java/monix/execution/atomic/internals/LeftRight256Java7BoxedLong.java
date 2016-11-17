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

package monix.execution.atomic.internals;

import monix.execution.misc.UnsafeAccess;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

abstract class LeftRight256Java7BoxedLongImpl extends LeftPadding120
  implements monix.execution.atomic.internals.BoxedLong {

  public volatile long value;
  private static final long OFFSET;
  private static final Unsafe UNSAFE = (Unsafe) UnsafeAccess.UNSAFE;

  static {
    UnsafeAccess.checkIfUnsafeIsAvailable();
    try {
      Field field = LeftRight256Java7BoxedLongImpl.class.getDeclaredField("value");
      OFFSET = UNSAFE.objectFieldOffset(field);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  LeftRight256Java7BoxedLongImpl(long initialValue) {
    UnsafeAccess.checkIfUnsafeIsAvailable();
    this.value = initialValue;
  }

  public long volatileGet() {
    return value;
  }

  public void volatileSet(long update) {
    value = update;
  }

  public void lazySet(long update) {
    UNSAFE.putOrderedLong(this, OFFSET, update);
  }

  public boolean compareAndSet(long current, long update) {
    return UNSAFE.compareAndSwapLong(this, OFFSET, current, update);
  }

  public long getAndSet(long update) {
    long current = value;
    while (!UNSAFE.compareAndSwapLong(this, OFFSET, current, update))
      current = value;
    return current;
  }

  public long getAndAdd(long delta) {
    long current;
    do {
      current = UNSAFE.getLongVolatile(this, OFFSET);
    } while (!UNSAFE.compareAndSwapLong(this, OFFSET, current, current+delta));
    return current;
  }
}

final class LeftRight256Java7BoxedLong extends LeftRight256Java7BoxedLongImpl {
  public volatile long r1, r2, r3, r4, r5, r6, r7, r8 = 7;
  public volatile long r9, r10, r11, r12, r13, r14, r15 = 7;
  public long sum() {
    return p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 +
      p9 + p10 + p11 + p12 + p13 + p14 + p15 +
      r1 + r2 + r3 + r4 + r5 + r6 + r7 + r8 +
      r9 + r10 + r11 + r12 + r13 + r14 + r15;
  }

  LeftRight256Java7BoxedLong(long initialValue) {
    super(initialValue);
  }
}
