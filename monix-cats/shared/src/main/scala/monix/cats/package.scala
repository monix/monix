package monix

import _root_.cats._
import scala.language.higherKinds

/** Package exposing integration with the Cats library.
  *
  * See: [[http://typelevel.org/cats/ typelevel.org/cats/]]
  */
package object cats extends AllInstances {
  type Deferrable[F[_]] = MonadError[F, Throwable] with CoflatMap[F]
  type Evaluable[F[_]] = Deferrable[F] with Bimonad[F]

  type Sequenceable[F[_]] = MonadFilter[F]
    with MonadError[F, Throwable]
    with CoflatMap[F]
    with MonadCombine[F]
}
