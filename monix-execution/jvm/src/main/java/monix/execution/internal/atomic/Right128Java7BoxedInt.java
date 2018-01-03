/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.execution.internal.atomic;

import sun.misc.Unsafe;
import java.lang.reflect.Field;

final class Right128Java7BoxedInt extends Right128Java7BoxedIntImpl {
  public volatile long p1, p2, p3, p4, p5, p6, p7, p8 = 7;
  public volatile long p9, p10, p11, p12, p13, p14, p15 = 7;
  public long sum() {
    return p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 +
      p9 + p10 + p11 + p12 + p13 + p14 + p15;
  }

  Right128Java7BoxedInt(int initialValue) {
    super(initialValue);
  }
}

abstract class Right128Java7BoxedIntImpl implements BoxedInt {
  public volatile int value;
  private static final long OFFSET;
  private static final Unsafe UNSAFE = (Unsafe) UnsafeAccess.getInstance();

  static {
    try {
      Field field = Right128Java7BoxedIntImpl.class.getDeclaredField("value");
      OFFSET = UNSAFE.objectFieldOffset(field);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  Right128Java7BoxedIntImpl(int initialValue) {
    this.value = initialValue;
  }

  public int volatileGet() {
    return value;
  }

  public void volatileSet(int update) {
    value = update;
  }

  public void lazySet(int update) {
    UNSAFE.putOrderedInt(this, OFFSET, update);
  }

  public boolean compareAndSet(int current, int update) {
    return UNSAFE.compareAndSwapInt(this, OFFSET, current, update);
  }

  public int getAndSet(int update) {
    int current = value;
    while (!UNSAFE.compareAndSwapInt(this, OFFSET, current, update))
      current = value;
    return current;
  }

  public int getAndAdd(int delta) {
    int current;
    do {
      current = UNSAFE.getIntVolatile(this, OFFSET);
    } while (!UNSAFE.compareAndSwapInt(this, OFFSET, current, current+delta));
    return current;
  }
}