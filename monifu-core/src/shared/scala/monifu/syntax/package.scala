/*
 * Copyright (c) 2014 by its authors. Some rights reserved. 
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
 
package monifu

import language.experimental.macros
import scala.reflect.macros.Context
import scala.language.reflectiveCalls

package object syntax {
  /**
   * Provides type-safe equality and inequality operators, implemented with
   * macros for efficiency reasons.
   */
  implicit class TypeSafeEquals[T](val self: T) extends AnyVal {
    def ===[U](other: U): Boolean = macro TypeSafeEquals.equalsImpl[T, U]

    def ≟[U](other: U): Boolean = macro TypeSafeEquals.equalsImpl[T, U]

    def !==[U](other: U): Boolean = macro TypeSafeEquals.notEqualsImpl[T, U]

    def ≠[U](other: U): Boolean = macro TypeSafeEquals.notEqualsImpl[T, U]
  }

  object TypeSafeEquals {
    def equalsImpl[T : c.WeakTypeTag, U : c.WeakTypeTag](c: Context { type PrefixType = TypeSafeEquals[T] })(other: c.Expr[T]): c.Expr[Boolean] = {
      import c.universe._

      def canProve[A : WeakTypeTag] =
        c.inferImplicitValue(weakTypeOf[A], silent=true) != EmptyTree

      if (canProve[T <:< U] || canProve[U <:< T])
        reify(c.prefix.splice.self == other.splice)
      else
        c.abort(c.macroApplication.pos, s"Cannot compare unrelated types ${weakTypeOf[T]} and ${weakTypeOf[U]}")
    }

    def notEqualsImpl[T : c.WeakTypeTag, U : c.WeakTypeTag](c: Context { type PrefixType = TypeSafeEquals[T] })(other: c.Expr[T]): c.Expr[Boolean] = {
      import c.universe._
      val equality = equalsImpl[T, U](c)(other)
      reify(!equality.splice)
    }
  }
}
