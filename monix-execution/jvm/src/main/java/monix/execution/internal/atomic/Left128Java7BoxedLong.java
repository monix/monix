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

final class Left128Java7BoxedLong extends LeftPadding120 implements BoxedLong {
  public volatile long value;
  private static final long OFFSET;
  private static final Unsafe UNSAFE = (Unsafe) UnsafeAccess.getInstance();

  static {
    try {
      Field field = Left128Java7BoxedLong.class.getDeclaredField("value");
      OFFSET = UNSAFE.objectFieldOffset(field);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  Left128Java7BoxedLong(long initialValue) {
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