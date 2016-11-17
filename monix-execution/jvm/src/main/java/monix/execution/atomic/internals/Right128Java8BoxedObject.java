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

abstract class Right128Java8BoxedObjectImpl implements monix.execution.atomic.internals.BoxedObject {
  public volatile Object value;
  private static final long OFFSET;
  private static final Unsafe UNSAFE = (Unsafe) UnsafeAccess.UNSAFE;

  static {
    UnsafeAccess.checkIfUnsafeIsAvailable();
    try {
      Field field = Right128Java8BoxedObjectImpl.class.getDeclaredField("value");
      OFFSET = UNSAFE.objectFieldOffset(field);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  Right128Java8BoxedObjectImpl(Object initialValue) {
    UnsafeAccess.checkIfUnsafeIsAvailable();
    this.value = initialValue;
  }

  public Object volatileGet() {
    return value;
  }

  public void volatileSet(Object update) {
    value = update;
  }

  public void lazySet(Object update) {
    UNSAFE.putOrderedObject(this, OFFSET, update);
  }

  public boolean compareAndSet(Object current, Object update) {
    return UNSAFE.compareAndSwapObject(this, OFFSET, current, update);
  }

  public Object getAndSet(Object update) {
    return UNSAFE.getAndSetObject(this, OFFSET, update);
  }
}

final class Right128Java8BoxedObject extends Right128Java8BoxedObjectImpl {
  public volatile long p1, p2, p3, p4, p5, p6, p7, p8 = 7;
  public volatile long p9, p10, p11, p12, p13, p14 = 7;
  public long sum() {
    return p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 +
      p9 + p10 + p11 + p12 + p13 + p14;
  }

  Right128Java8BoxedObject(Object initialValue) {
    super(initialValue);
  }
}
