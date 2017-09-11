package monix.execution.misc

import java.util.UUID

object Local {

  type Context = scala.collection.immutable.Map[String, Option[LocalContext]]

  trait LocalContext

  private[this] def context(k: String, v: Option[LocalContext]): Context =
    scala.collection.immutable.Map(k -> v)

  private[this] val localContext = ThreadLocal[Context]()

  private def register(): String = synchronized {
    UUID.randomUUID().toString
  }

  /**
    * Return the state of the current Local state.
    */
  def getContext(): Context =
    localContext.get

  /**
    * Restore the Local state to a given Context.
    */
  def setContext(ctx: Context): Unit =
    localContext.set(ctx)

  /**
    * Clear the Local state.
    */
  def clearContext(): Unit =
    localContext.set(null)

  /**
    * Execute a block of code using the specified state of Local
    * Context and restore the current state when complete
    */
  def withContext[T](ctx: Context)(f: => T): T = {
    val saved = getContext()
    setContext(ctx)
    try f
    finally setContext(saved)
  }

  /**
    * Execute a block of code with a clear state of Local
    * Context and restore the current state when complete
    */
  def withClearContext[T](f: => T): T = withContext(null)(f)

  private def save(id: String, tctx: Option[LocalContext]): Unit = {
    val newCtx = Option(localContext.get) match {
      case Some(ctx) =>
        ctx + (id -> tctx)
      case None =>
        context(id, tctx)
    }
    localContext.set(newCtx)
  }

  private def get(id: String): Option[_ <: LocalContext] = {
    Option(localContext.get).flatMap(_.get(id).flatten)
  }

  private def clear(id: String): Unit =
    Option(localContext.get).foreach { m =>
      localContext.set(m - id)
    }

}

final class Local[T <: Local.LocalContext] {
  private[this] val id: String = Local.register()

  def update(value: T): Unit =
    set(Some(value))

  private def set(value: Option[T]): Unit =
    Local.save(id, value)

  def apply(): Option[T] =
    Local.get(id).asInstanceOf[Option[T]]

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

  def clear(): Unit =
    Local.clear(id)
}
