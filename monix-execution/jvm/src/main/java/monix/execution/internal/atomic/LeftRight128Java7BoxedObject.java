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

final class LeftRight128Java7BoxedObject extends LeftRight128Java7BoxedObjectImpl {
  public volatile long r1, r2, r3, r4, r5, r6, r7, r8 = 11;
  @Override public long sum() {
    return p1 + p2 + p3 + p4 + p5 + p6 + p7 +
      r1 + r2 + r3 + r4 + r5 + r6 + r7 + r8;
  }

  LeftRight128Java7BoxedObject(Object initialValue) {
    super(initialValue);
  }
}

abstract class LeftRight128Java7BoxedObjectImpl extends LeftPadding56 implements BoxedObject {

  public volatile Object value;
  private static final long OFFSET;
  private static final Unsafe UNSAFE = (Unsafe) UnsafeAccess.getInstance();

  static {
    try {
      Field field = LeftRight128Java7BoxedObjectImpl.class.getDeclaredField("value");
      OFFSET = UNSAFE.objectFieldOffset(field);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  LeftRight128Java7BoxedObjectImpl(Object initialValue) {
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
    Object current = value;
    while (!UNSAFE.compareAndSwapObject(this, OFFSET, current, update))
      current = value;
    return current;
  }
}