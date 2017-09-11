package monix.execution.misc


/**
  * A local context that should be used for tracing purposes
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
  * Should be extended to TracingContext companion object
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