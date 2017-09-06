package monix.execution.misc

import java.util.UUID

object LocalContext {

  type Context = scala.collection.immutable.Map[String, TracingContext]

  trait TracingContext
  object TracingContext {

    case object Empty extends TracingContext
  }

  private[this] def context(k: String, v: TracingContext): Context =
    scala.collection.immutable.Map(k -> v)

  private[this] val localContext = ThreadLocal[Context]

  private def register(): String = synchronized {
    UUID.randomUUID().toString
  }


  /**
    * Return a snapshot of the current LocalTracing state.
    */
  def getContext(): Context =
    localContext.get

  /**
    * Restore the LocalTracing state to a given Context of values.
    */
  def setContext(ctx: Context): Unit =
    localContext.set(ctx)

  /**
    * Clear the LocalTracing state.
    */
  def clearContext(): Unit =
    localContext.reset()

  /**
    * Execute a block of code using the specified state of LocalTracing
    * Context and restore the current state when complete
    */
  def withContext[T](ctx: Context)(f: => T): T = {
    val saved = getContext()
    setContext(ctx)
    try f
    finally setContext(saved)
  }

  /**
    * Execute a block of code with a clear state of LocalTracing
    * Context and restore the current state when complete
    */
  def withClearContext[T](f: => T): T = withContext(null)(f)

  private def save(id: String, tctx: TracingContext): Unit = {
    val newCtx = Option(localContext.get) match {
      case Some(ctx) =>
        ctx + (id -> tctx)
      case None =>
        context(id, tctx)
    }
    localContext.set(newCtx)
  }

  private def get(id: String): Option[_ <: TracingContext] = {
    Option(localContext.get).flatMap(_.get(id))
  }

  private def clear(id: String): Unit =
    Option(localContext.get).foreach { m =>
      localContext.set(m - id)
    }

}

final class LocalContext[T <: LocalContext.TracingContext] {
  //import LocalContext._
  private[this] val id: String = LocalContext.register()

  def update(value: T): Unit =
    set(Some(value))

  private def set(value: Option[T]): Unit =
    value.foreach(LocalContext.save(id, _))

  def apply(): Option[T] =
    LocalContext.get(id).asInstanceOf[Option[T]]

  /**
    * Execute a block with a TracingContext, restoring the current state
    * upon completion.
    */
  def withContext[U](value: T)(f: => U): U = {
    val saved = apply()
    set(Some(value))
    try f
    finally set(saved)
  }

  def withClearContext[U](f: => U): U = {
    val saved = apply()
    clear()
    try f
    finally set(saved)
  }

  def clear(): Unit = LocalContext.clear(id)
}
