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

import sun.misc.Unsafe;
import java.lang.reflect.Field;

/**
 * INTERNAL API — used in the implementation of
 * `monix.execution.atomic.Atomic`.
 *
 * Being internal it can always change between minor versions,
 * providing no backwards compatibility guarantees and is only public
 * because Java does not provide the capability of marking classes as
 * "internal" to a package and all its sub-packages.
 */
abstract class Right64Java7BoxedIntImpl implements BoxedInt {
  public volatile int value;
  private static final long OFFSET;
  private static final Unsafe UNSAFE = (Unsafe) UnsafeAccess.getInstance();

  static {
    try {
      Field field = Right64Java7BoxedIntImpl.class.getDeclaredField("value");
      OFFSET = UNSAFE.objectFieldOffset(field);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  Right64Java7BoxedIntImpl(int initialValue) {
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

/**
 * INTERNAL API — used in the implementation of
 * `monix.execution.atomic.Atomic`.
 *
 * Being internal it can always change between minor versions,
 * providing no backwards compatibility guarantees and is only public
 * because Java does not provide the capability of marking classes as
 * "internal" to a package and all its sub-packages.
 */
final class Right64Java7BoxedInt extends Right64Java7BoxedIntImpl {
  public volatile long p1, p2, p3, p4, p5, p6, p7 = 7;
  public long sum() { return p1 + p2 + p3 + p4 + p5 + p6 + p7; }

  Right64Java7BoxedInt(int initialValue) {
    super(initialValue);
  }
}
