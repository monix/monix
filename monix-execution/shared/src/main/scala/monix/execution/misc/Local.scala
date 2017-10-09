package monix.execution.misc

object Local {

  type Context = scala.collection.immutable.Map[Key, Option[LocalContext]]

  trait LocalContext extends Serializable

  final class Key extends Serializable

  private[this] def context(k: Key, v: Option[LocalContext]): Context =
    scala.collection.immutable.Map(k -> v)

  private[this] val localContext = ThreadLocal[Context]()

  private def register(): Key =
    new Key

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

  private def save(key: Key, tctx: Option[LocalContext]): Unit = {
    val newCtx = localContext.get match {
      case null =>
        context(key, tctx)
      case ctx =>
        ctx + (key -> tctx)
    }
    localContext.set(newCtx)
  }

  private def get(key: Key): Option[_ <: LocalContext] = {
    val l = localContext.get
    if (l == null) None else l.get(key).flatten
  }

  private def clear(key: Key): Unit = {
    val l = localContext.get
    if (l != null) localContext.set(l - key)
  }
}

/**
  * An instance of [[Local]] is used to register a context of a thread using a
  * [[monix.execution.misc.ThreadLocal ThreadLocal]] with the help of a global
  * state bound to its companion object where all the registered contexts are stored.
  * The type parameter T is bounded to [[monix.execution.misc.Local.LocalContext LocalContext]]
  * so the user can create local contexts and define the rules of its behaviour.
  * {{{
  *
  *   case class TestLocalContext(id: Int) extends LocalContext
  *
  *   val local = new Local[TestLocalContext]
  *
  *   // To get the current state, should yield None
  *   local()
  *
  *   // To set the value
  *   local.set(Some(TestLocalContext(1)))
  *
  *   // Should yield TestLocalContext(1)
  *   local()
  *
  *   // To get the global state
  *   // Should yield Map(Key@5de5e95 -> Some(TestLocalContext(1)))
  *   Local.getContext()
  *
  *   val local2 = new Local[TestLocalContext]
  *
  *   // To set new LocalContext of local2
  *   local2.set(Some(TestLocalContext(2)))
  *
  *   // To get the global state
  *   // Should yield:
  *   // Map(Key@5de5e95 -> Some(TestLocalContext(1)), Key@2f677247 -> Some(TestLocalContext(2)))
  *   Local.getContext()
  *
  * }}}
  * @tparam T bounded to the [[monix.execution.misc.Local.LocalContext LocalContext]]
  */
final class Local[T <: Local.LocalContext] {
  private[this] val key: Local.Key = Local.register()

  def update(value: T): Unit =
    set(Some(value))

  private def set(value: Option[T]): Unit =
    Local.save(key, value)

  def apply(): Option[T] =
    Local.get(key).asInstanceOf[Option[T]]

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
    Local.clear(key)
}
