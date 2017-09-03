package monix.execution.misc

import java.util.UUID

object LocalTracing {

  type Context = scala.collection.mutable.Map[String, TracingContext]

  trait TracingContext

  private[this] def context(k: String, v: TracingContext): Context =
    scala.collection.mutable.Map(k -> v)

  private[this] val localContext = ThreadLocal[Context]

  private def register(): String =
    UUID.randomUUID().toString


  /**
    * Return a snapshot of the current Local state.
    */
  def getContext(): Context = localContext.get

  /**
    * Restore the Local state to a given Context of values.
    */
  def setContext(ctx: Context): Unit = localContext.set(ctx)

  def clearContext(): Unit = localContext.reset()

  /**
    * Execute a block of code using the specified state of ContextLocals
    * and restore the current state when complete (success or failure)
    */
  def withContext[T](ctx: Context)(f: => T): T = {
    val saved = getContext()
    setContext(ctx)
    try f
    finally setContext(saved)
  }

  /**
    * Execute a block of code with a clear state of ContextLocals
    * and restore the current state when complete (success or failure)
    */
  def withClearContext[T](f: => T): T = withContext(null)(f)

  private def save(id: String, tctx: TracingContext): Unit = {
    val newCtx = Option(localContext.get) match {
      case Some(ctx) =>
        ctx += (id -> tctx)
      case None =>
        context(id, tctx)
    }
    localContext.set(newCtx)
  }

  private def get(id: String): Option[TracingContext] = {
    Option(localContext.get).flatMap(_.get(id))
  }

  private def clear(id: String): Unit =
    Option(localContext.get).foreach(_ -= id)

}

final class LocalTracing {
  import LocalTracing._
  private[this] val id: String = LocalTracing.register()

  def update(value: TracingContext): Unit =
    set(Some(value))

  private def set(value: Option[TracingContext]): Unit =
    value.foreach(save(id, _))

  def apply(): Option[TracingContext] =
    get(id)

  /**
    * Execute a block with a TracingContext, restoring the current state
    * upon completion.
    */
  def withContext[U](value: TracingContext)(f: => U): U = {
    val saved = apply()
    set(Some(value))
    try f
    finally set(saved)
  }
}
