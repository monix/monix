package monifu.syntax

import scala.annotation.implicitNotFound
import scala.reflect.macros.Context
import language.experimental.macros

/**
 * Type-class for proving that an implicit `T` does not exist in scope.
 *
 * Example: {{{
 *   scala> implicitly[Not[Numeric[String]]]
 *   res1: monifu.Not[Numeric[String]] = $anon$1@328a3be0
 *
 *   scala> implicitly[Not[Numeric[Int]]]
 *   <console>:9: error: cannot prove that Not[Numeric[Int]] because an implicit for Numeric[Int] exists in scope
 *                 implicitly[Not[Numeric[Int]]]
 *                           ^
 *   scala> implicitly[Int =:= Double]
 *   <console>:9: error: Cannot prove that Int =:= Double.
 *                 implicitly[Int =:= Double]
 *                           ^
 *
 *   scala> implicitly[Not[Int =:= Double]]
 *   res2: monifu.Not[=:=[Int,Double]] = $anon$1@23ccba4
 * }}}
 *
 * @tparam T is the implicit value that shouldn't exist in scope for `Not[T]` to exist
 */
@implicitNotFound("cannot prove that Not[${T}] because an implicit for ${T} exists in scope")
trait Not[T] {}

object Not {
  def notImpl[T : c.WeakTypeTag](c: Context): c.Expr[Not[T]] = {
    import c.universe._

    if (c.inferImplicitValue(weakTypeOf[T], silent=true) == EmptyTree)
      reify(new Not[T] {})
    else
      c.abort(c.macroApplication.pos, "Found an implicit, so can't find Not[T]")
  }

  implicit def not[T]: Not[T] = macro notImpl[T]
}

