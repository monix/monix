package monix.execution.misc


/**
  * A [[monix.execution.misc.TracingContext TracingContext]] is local
  * context that should be used for tracing purposes. Any class that
  * extends this trait should define a companion object that extends
  * [[monix.execution.misc.TracingContextCompanion TracingContextCompanion]]
  */
trait TracingContext extends Local.LocalContext {

  /**
    * To execute code within a given context
    * @param f the block of code to be executed
    * @tparam T The type of the block to be executed
    * @return a result of type T
    */
  def asCurrent[T](f: => T): T
}

/**
  * Should be extended to TracingContext companion object. This helps
  * define a single local variable for the companion object, which will
  * manage the access of instances of [[monix.execution.misc.TracingContext TracingContext]]
  * to the local state. Using the let function will always guarantee any leaks of
  * the local context because the previous state will always be restored.
  * {{{
  *   case class Traced extends TracingContext {
  *     def asCurrent[T](f: => T): T = Traced.let(Some(this))
  *   }
  *
  *   object Traced extends TracingContextCompanion[Traced]
  * }}}
  * @tparam T bounded to a TracingContext
  */
trait TracingContextCompanion[T <: TracingContext] {

  val local: Local[T] = new Local[T]

  def current: Option[T] =
    local()

  private[monix] def let[R](ctx: Option[T])(f: => R): R = {
    ctx match {
      case Some(cid) => local.withContext(cid)(f)
      case None => local.withClearContext(f)
    }
  }
}