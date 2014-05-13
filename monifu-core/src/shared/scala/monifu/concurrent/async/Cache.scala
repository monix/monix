package monifu.concurrent.async

import concurrent.duration._
import monifu.concurrent.atomic.AtomicAny
import scala.concurrent.{Promise, ExecutionContext, Future}
import monifu.concurrent.Scheduler
import scala.annotation.tailrec


trait Cache extends java.io.Closeable {
  def get[T](key: String): Option[T]
  def getOrElse[T](key: String, default: => T): T
  def add[T](key: String, value: T, expiry: Duration = Duration.Inf): Boolean
  def set[T](key: String, value: T, expiry: Duration = Duration.Inf): Unit
  def delete(key: String): Boolean
  def cachedFuture[T](key: String, expiry: Duration = Duration.Inf)(cb: => Future[T]): Future[T]

  def compareAndSet[T](key: String, expected: Option[T], update: T, expiry: Duration = Duration.Inf): Boolean
  def transformAndGet[T](key: String, expiry: Duration = Duration.Inf)(cb: Option[T] => T): T
  def getAndTransform[T](key: String, expiry: Duration = Duration.Inf)(cb: Option[T] => T): Option[T]

  def size: Int

  def realSize: Int

  /**
   * Future that completes when a maintenance window has run,
   * giving the number of items that were removed.
   * @return
   */
  def maintenance: Future[Int]

  def close(): Unit
}

object Cache {
  def apply(ec: ExecutionContext): Cache =
    new CacheImpl()(ec)
}

private[async] final class CacheImpl(implicit ec: ExecutionContext) extends Cache {
  private[this] val scheduler = Scheduler.fromContext(ec)

  def get[T](key: String): Option[T] = {
    val currentState = stateRef.get

    currentState.values.get(key) match {
      case Some(value) if value.expiresAt > System.currentTimeMillis() =>
        Some(value.value.asInstanceOf[T])
      case _ =>
        None
    }
  }

  def getOrElse[T](key: String, default: => T): T =
    get[T](key) match {
      case Some(value) => value
      case None => default
    }

  @tailrec
  def add[T](key: String, value: T, expiry: Duration = Duration.Inf): Boolean = {
    val ts = getExpiryTS(expiry)
    val currentTS = System.currentTimeMillis()
    val currentState = stateRef.get

    val itemExists = currentState.values.get(key) match {
      case Some(item) if item.expiresAt > currentTS =>
        true
      case _ =>
        false
    }

    if (itemExists || ts <= currentTS)
      false
    else {
      val firstExpiry = if (currentState.firstExpiry == 0) ts else math.min(currentState.firstExpiry, ts)
      val values = currentState.values.updated(key, CacheValue(value, ts))
      val newState = currentState.copy(values = values, firstExpiry = firstExpiry)

      if (stateRef.compareAndSet(currentState, newState))
        true
      else
        add(key, value, expiry)
    }
  }

  def set[T](key: String, value: T, expiry: Duration = Duration.Inf) = {
    val ts = getExpiryTS(expiry)

    stateRef.transform { current =>
      val firstExpiry = if (current.firstExpiry == 0) ts else math.min(current.firstExpiry, ts)
      val values = current.values.updated(key, CacheValue(value, ts))
      current.copy(values = values, firstExpiry = firstExpiry)
    }
  }

  @tailrec
  def delete(key: String): Boolean = {
    val currentState = stateRef.get

    currentState.values.get(key) match {
      case Some(value) =>
        val values = currentState.values - key
        val newState = currentState.copy(values = values)

        if (stateRef.compareAndSet(currentState, newState))
          value.expiresAt > System.currentTimeMillis()
        else
          delete(key)
      case None =>
        false
    }
  }

  @tailrec
  def cachedFuture[T](key: String, expiry: Duration = Duration.Inf)(cb: => Future[T]): Future[T] = {
    val currentState = stateRef.get

    val currentValue = currentState.values.get(key) match {
      case Some(value) if value.expiresAt > System.currentTimeMillis() =>
        Some(value.value.asInstanceOf[Future[T]])
      case _ =>
        None
    }

    currentValue match {
      case Some(value) =>
        value
      case None =>
        val ts = getExpiryTS(expiry)
        val promise = Promise[T]()
        val future = promise.future

        val values = currentState.values.updated(key, CacheValue(future, ts))
        val firstExpiry = if (currentState.firstExpiry == 0) ts else math.min(currentState.firstExpiry, ts)
        val newState = currentState.copy(values, firstExpiry)

        if (stateRef.compareAndSet(currentState, newState)) {
          promise.completeWith(cb)
          future
        }
        else
          cachedFuture(key, expiry)(cb)
    }
  }


  def compareAndSet[T](key: String, expected: Option[T], update: T, expiry: Duration): Boolean = {
    val current = stateRef.get
    val ts = getExpiryTS(expiry)

    val currentValue = current.values.get(key) match {
      case Some(value) if value.expiresAt > System.currentTimeMillis() =>
        Some(value.value.asInstanceOf[T])
      case _ =>
        None
    }


    if (currentValue != expected)
      false
    else {
      val values = current.values.updated(key, CacheValue(update, ts))
      val firstExpiry = if (current.firstExpiry == 0) ts else math.min(current.firstExpiry, ts)
      val newState = current.copy(values, firstExpiry)
      stateRef.compareAndSet(current, newState)
    }
  }

  def transformAndGet[T](key: String, expiry: Duration)(cb: (Option[T]) => T): T =
    stateRef.transformAndExtract { current =>
      val ts = getExpiryTS(expiry)

      val currentValue = current.values.get(key) match {
        case Some(value) if value.expiresAt > System.currentTimeMillis() =>
          Some(value.value.asInstanceOf[T])
        case _ =>
          None
      }

      val newValue = cb(currentValue)
      val values = current.values.updated(key, CacheValue(newValue, ts))
      val firstExpiry = if (current.firstExpiry == 0) ts else math.min(current.firstExpiry, ts)
      (newValue, current.copy(values, firstExpiry))
    }

  def getAndTransform[T](key: String, expiry: Duration)(cb: (Option[T]) => T): Option[T] =
    stateRef.transformAndExtract { current =>
      val ts = getExpiryTS(expiry)

      val currentValue = current.values.get(key) match {
        case Some(value) if value.expiresAt > System.currentTimeMillis() =>
          Some(value.value.asInstanceOf[T])
        case _ =>
          None
      }

      val newValue = cb(currentValue)
      val values = current.values.updated(key, CacheValue(newValue, ts))
      val firstExpiry = if (current.firstExpiry == 0) ts else math.min(current.firstExpiry, ts)
      (currentValue, current.copy(values, firstExpiry))
    }

  def clean() = {
    val (promise, difference) = stateRef.transformAndExtract { currentState =>
      val currentTS = System.currentTimeMillis()

      if (currentState.firstExpiry <= currentTS) {
        val values = currentState.values.filterNot(value => value._2.expiresAt <= currentTS)
        val difference = currentState.values.size - values.size

        val firstExpiry = values.foldLeft(0L) { (acc, elem) =>
          if (acc == 0 || acc < elem._2.expiresAt)
            elem._2.expiresAt
          else
            acc
        }

        val newState = CacheState(values, firstExpiry)
        ((currentState.maintenancePromise, difference), newState)
      }
      else {
        val newState = currentState.copy(maintenancePromise = Promise())
        ((currentState.maintenancePromise, 0), newState)
      }
    }

    promise.trySuccess(difference)
  }


  def size: Int = {
    val ts = System.currentTimeMillis()
    stateRef.get.values.count(_._2.expiresAt <= ts)
  }

  def realSize: Int = stateRef.get.values.size

  /**
   * Future that completes when a maintenance window has run,
   * giving the number of items that were removed.
   * @return
   */
  def maintenance: Future[Int] =
    stateRef.get.maintenancePromise.future

  def close(): Unit = {
    task.cancel()
    val state = stateRef.getAndSet(CacheState())
    state.maintenancePromise.trySuccess(0)
  }

  protected def getExpiryTS(expiry: Duration) =
    if (expiry.isFinite())
      System.currentTimeMillis() + expiry.toMillis
    else
      System.currentTimeMillis() + 365.days.toMillis

  private[this] val task = scheduler.scheduleRepeated(3.seconds, 3.seconds, {
    clean()
  })

  private[this] case class CacheValue(
    value: Any,
    expiresAt: Long
  )

  private[this] case class CacheState(
    values: Map[String, CacheValue] = Map.empty,
    firstExpiry: Long = 0,
    maintenancePromise: Promise[Int] = Promise[Int]()
  )

  private[this] val stateRef = AtomicAny(CacheState())
}

