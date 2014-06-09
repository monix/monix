package monifu.syntax

import scala.annotation.implicitNotFound
import scala.reflect.macros.whitebox.Context
import language.experimental.macros

/**
 * Type-class for proving that an implicit `T` does not exist in scope.
 *
 * Example: {{{
 *   // works
 *   implicitly[Not[Numeric[String]]]
 *
 *   implicitly[Not[Numeric[Int]]]
 *   //=> <console>:9: error: cannot prove that Not[Numeric[Int]] because an implicit for Numeric[Int] exists in scope
 *   //=>               implicitly[Not[Numeric[Int]]]
 *   //=>                         ^
 *
 *   implicitly[Int =:= Double]
 *   //=> <console>:9: error: Cannot prove that Int =:= Double.
 *   //=>               implicitly[Int =:= Double]
 *   //=>                         ^
 *
 *   // works
 *   implicitly[Not[Int =:= Double]]
 * }}}
 *
 * @tparam T is the implicit value that shouldn't exist in scope for `Not[T]` to exist
 */
@implicitNotFound("cannot prove that Not[${T}] because an implicit for ${T} exists in scope")
trait Not[T] {}

object Not {
  def notImpl[T : c.WeakTypeTag](c: Context): c.Tree = {
    import c.universe._

    if (c.inferImplicitValue(weakTypeOf[T], silent=true) == EmptyTree)
      reify(new Not[T] {}).tree
    else
      c.abort(c.macroApplication.pos, "Found an implicit, so can't prove Not[T]")
  }

  implicit def not[T]: Not[T] = macro notImpl[T]
}

