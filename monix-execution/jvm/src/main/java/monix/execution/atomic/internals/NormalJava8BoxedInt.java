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

final class NormalJava8BoxedInt implements monix.execution.atomic.internals.BoxedInt {
  public volatile int value;
  private static final long OFFSET;
  private static final Unsafe UNSAFE = (Unsafe) UnsafeAccess.UNSAFE;

  static {
    UnsafeAccess.checkIfUnsafeIsAvailable();
    try {
      Field field = NormalJava8BoxedInt.class.getDeclaredField("value");
      OFFSET = UNSAFE.objectFieldOffset(field);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  NormalJava8BoxedInt(int initialValue) {
    UnsafeAccess.checkIfUnsafeIsAvailable();
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
    return UNSAFE.getAndSetInt(this, OFFSET, update);
  }

  public int getAndAdd(int delta) {
    return UNSAFE.getAndAddInt(this, OFFSET, delta);
  }
}