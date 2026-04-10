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

import scala.MatchError;

/**
 * INTERNAL API — used in the implementation of
 * `monix.execution.atomic.Atomic`.
 *
 * Being internal it can always change between minor versions,
 * providing no backwards compatibility guarantees and is only public
 * because Java does not provide the capability of marking classes as
 * "internal" to a package and all its sub-packages.
 */
public final class Factory {
  public static BoxedObject newBoxedObject(Object initial, BoxPaddingStrategy padding, boolean allowUnsafe, boolean allowJava8Intrinsics) {
    switch (padding) {
      case NO_PADDING:
        return new NormalJavaXBoxedObject(initial);

      case LEFT_64:
        return new Left64JavaXBoxedObject(initial);

      case RIGHT_64:
        return new Right64JavaXBoxedObject(initial);

      case LEFT_RIGHT_128:
        return new LeftRight128JavaXBoxedObject(initial);

      case LEFT_128:
        return new Left128JavaXBoxedObject(initial);

      case RIGHT_128:
        return new Right128JavaXBoxedObject(initial);

      case LEFT_RIGHT_256:
        return new LeftRight256JavaXBoxedObject(initial);

      default:
        throw new MatchError(padding);
    }
  }

  public static BoxedInt newBoxedInt(int initial, BoxPaddingStrategy padding, boolean allowUnsafe, boolean allowJava8Intrinsics) {
    switch (padding) {
      case NO_PADDING:
        return new NormalJavaXBoxedInt(initial);

      case LEFT_64:
        return new Left64JavaXBoxedInt(initial);

      case RIGHT_64:
        return new Right64JavaXBoxedInt(initial);

      case LEFT_RIGHT_128:
        return new LeftRight128JavaXBoxedInt(initial);

      case LEFT_128:
        return new Left128JavaXBoxedInt(initial);

      case RIGHT_128:
        return new Right128JavaXBoxedInt(initial);

      case LEFT_RIGHT_256:
        return new LeftRight256JavaXBoxedInt(initial);

      default:
        throw new MatchError(padding);
    }
  }

  public static BoxedLong newBoxedLong(long initial, BoxPaddingStrategy padding, boolean allowUnsafe, boolean allowJava8Intrinsics) {
    switch (padding) {
      case NO_PADDING:
        return new NormalJavaXBoxedLong(initial);

      case LEFT_64:
        return new Left64JavaXBoxedLong(initial);

      case RIGHT_64:
        return new Right64JavaXBoxedLong(initial);

      case LEFT_RIGHT_128:
        return new LeftRight128JavaXBoxedLong(initial);

      case LEFT_128:
        return new Left128JavaXBoxedLong(initial);

      case RIGHT_128:
        return new Right128JavaXBoxedLong(initial);

      case LEFT_RIGHT_256:
        return new LeftRight256JavaXBoxedLong(initial);

      default:
        throw new MatchError(padding);
    }
  }
}
