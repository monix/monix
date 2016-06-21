/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.scalaz

object implicits
  extends monix.scalaz.AllInstances
    with _root_.scalaz.StateFunctions           // Functions related to the state monad
    with _root_.scalaz.syntax.ToTypeClassOps    // syntax associated with type classes
    with _root_.scalaz.syntax.ToDataOps         // syntax associated with Scalaz data structures
    with _root_.scalaz.std.AllInstances         // Type class instances for the standard library types
    with _root_.scalaz.std.AllFunctions         // Functions related to standard library types
    with _root_.scalaz.syntax.std.ToAllStdOps   // syntax associated with standard library types
    with _root_.scalaz.IdInstances              // Identity type and instances

