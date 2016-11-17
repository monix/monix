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
import scala.MatchError;

public final class Factory {
  public static BoxedObject newBoxedObject(Object initial, BoxPaddingStrategy padding, boolean allowJava8Intrinsics) {
    switch (padding) {
      case NO_PADDING:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new NormalJava8BoxedObject(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new NormalJava7BoxedObject(initial);
        else
          return new NormalJavaXBoxedObject(initial);

      case LEFT_64:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new Left64Java8BoxedObject(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new Left64Java7BoxedObject(initial);
        else
          return new Left64JavaXBoxedObject(initial);

      case RIGHT_64:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new Right64Java8BoxedObject(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new Right64Java7BoxedObject(initial);
        else
          return new Right64JavaXBoxedObject(initial);

      case LEFT_RIGHT_128:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new LeftRight128Java8BoxedObject(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new LeftRight128Java7BoxedObject(initial);
        else
          return new LeftRight128JavaXBoxedObject(initial);

      case LEFT_128:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new Left128Java8BoxedObject(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new Left128Java7BoxedObject(initial);
        else
          return new Left128JavaXBoxedObject(initial);

      case RIGHT_128:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new Right128Java8BoxedObject(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new Right128Java7BoxedObject(initial);
        else
          return new Right128JavaXBoxedObject(initial);

      case LEFT_RIGHT_256:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new LeftRight256Java8BoxedObject(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new LeftRight256Java7BoxedObject(initial);
        else
          return new LeftRight256JavaXBoxedObject(initial);

      default:
        throw new MatchError(padding);
    }
  }

  public static BoxedInt newBoxedInt(int initial, BoxPaddingStrategy padding, boolean allowJava8Intrinsics) {
    switch (padding) {
      case NO_PADDING:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new NormalJava8BoxedInt(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new NormalJava7BoxedInt(initial);
        else
          return new NormalJavaXBoxedInt(initial);

      case LEFT_64:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new Left64Java8BoxedInt(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new Left64Java7BoxedInt(initial);
        else
          return new Left64JavaXBoxedInt(initial);

      case RIGHT_64:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new Right64Java8BoxedInt(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new Right64Java7BoxedInt(initial);
        else
          return new Right64JavaXBoxedInt(initial);

      case LEFT_RIGHT_128:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new LeftRight128Java8BoxedInt(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new LeftRight128Java7BoxedInt(initial);
        else
          return new LeftRight128JavaXBoxedInt(initial);

      case LEFT_128:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new Left128Java8BoxedInt(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new Left128Java7BoxedInt(initial);
        else
          return new Left128JavaXBoxedInt(initial);

      case RIGHT_128:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new Right128Java8BoxedInt(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new Right128Java7BoxedInt(initial);
        else
          return new Right128JavaXBoxedInt(initial);

      case LEFT_RIGHT_256:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new LeftRight256Java8BoxedInt(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new LeftRight256Java7BoxedInt(initial);
        else
          return new LeftRight256JavaXBoxedInt(initial);

      default:
        throw new MatchError(padding);
    }
  }

  public static BoxedLong newBoxedLong(long initial, BoxPaddingStrategy padding, boolean allowJava8Intrinsics) {
    switch (padding) {
      case NO_PADDING:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new NormalJava8BoxedLong(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new NormalJava7BoxedLong(initial);
        else
          return new NormalJavaXBoxedLong(initial);

      case LEFT_64:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new Left64Java8BoxedLong(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new Left64Java7BoxedLong(initial);
        else
          return new Left64JavaXBoxedLong(initial);

      case RIGHT_64:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new Right64Java8BoxedLong(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new Right64Java7BoxedLong(initial);
        else
          return new Right64JavaXBoxedLong(initial);

      case LEFT_RIGHT_128:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new LeftRight128Java8BoxedLong(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new LeftRight128Java7BoxedLong(initial);
        else
          return new LeftRight128JavaXBoxedLong(initial);

      case LEFT_128:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new Left128Java8BoxedLong(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new Left128Java7BoxedLong(initial);
        else
          return new Left128JavaXBoxedLong(initial);

      case RIGHT_128:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new Right128Java8BoxedLong(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new Right128Java7BoxedLong(initial);
        else
          return new Right128JavaXBoxedLong(initial);

      case LEFT_RIGHT_256:
        if (allowJava8Intrinsics && UnsafeAccess.IS_JAVA_8)
          return new LeftRight256Java8BoxedLong(initial);
        else if (UnsafeAccess.UNSAFE != null)
          return new LeftRight256Java7BoxedLong(initial);
        else
          return new LeftRight256JavaXBoxedLong(initial);

      default:
        throw new MatchError(padding);
    }
  }
}
