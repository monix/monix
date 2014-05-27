package monifu.reactive.api

import monifu.reactive.Observer
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Promise, Future}
import monifu.reactive.api.Ack.{Done, Continue}
import monifu.concurrent.Scheduler


final class ConnectableObserver[-T](val underlying: Observer[T])(implicit s: Scheduler)
  extends Observer[T] {

  private[this] val observer = SafeObserver(underlying)
  private[this] val lock = new AnyRef

  // MUST BE protected by `lock` if isConnected == false
  private[this] var isDone = false
  // MUST BE protected by `lock` if isConnected == false
  private[this] var errorThrown = null : Throwable
  // MUST BE protected by `lock`
  private[this] var lastAck = Continue : Future[Ack]
  // MUST BE protected by `lock`
  private[this] var queue = new ListBuffer[T]()

  // volatile that is set to true once the buffer is drained
  // once visible as true, it implies that the queue is empty
  // and has been drained and thus the onNext/onError/onComplete
  // can take the fast path
  @volatile private[this] var isConnected = false

  // promise guaranteed to be fulfilled once isConnected=true
  private[this] val connectedPromise = Promise[Ack]()

  def scheduleFirst(elems: T*): Unit = {
    lock.synchronized {
      if (!isConnected)
        queue.prepend(elems : _*)
      else
        throw new IllegalStateException("Cannot scheduleFirst after isConnected")
    }
  }

  def schedulerError(ex: Throwable): Unit = {
    lock.synchronized {
      if (!isConnected) {
        isDone = true
        errorThrown = ex
      }
      else
        throw new IllegalStateException("Cannot scheduleError after isConnected")
    }
  }

  def scheduleComplete(): Unit = {
    lock.synchronized {
      if (!isConnected)
        isDone = true
      else
        throw new IllegalStateException("Cannot scheduleComplete after isConnected")
    }
  }

  def connect(): Unit = {
    lock.synchronized {
      // method connect() should be idempotent, calling it multiple times
      // should have the same effect as calling it once
      if (!isConnected) {
        // taking current elements from Queue and building a sequence of
        // onNext events from them ... must be based on the last acknowledgement
        // sent by the observer
        lastAck = queue.foldLeft(lastAck) { (acc, elem) =>
          acc.flatMap {
            case Continue => observer.onNext(elem)
            case Done => Done
          }
        }

        // clearing the queue of current elements, safe since we are
        // synchronized under lock - not setting to null yet, since
        // concurrent onNext events can enqueue in it
        queue.clear()

        lastAck.onSuccess {
          case Continue =>
            lock.synchronized {
              if (queue.nonEmpty) {
                // the publisher has pushed new items in our queue
                // that must be processed before the connection happens
                connect()
              }
              else if (errorThrown ne null) {
                // onError signaled from upstream before the connection
                // happened, so it is the responsibility of connect() to
                // signal it downstream - after which we are done
                observer.onError(errorThrown)
                queue.clear()
                queue = null
                connectedPromise.success(Done)
                isConnected = true
              }
              else if (isDone) {
                // onComplete signaled from upstream before the connection
                // happened, so it is the responsibility of connect() to
                // signal it downstream - after which we are done
                observer.onComplete()
                queue.clear()
                queue = null
                connectedPromise.success(Done)
                isConnected = true
              }
              else {
                // publishing isConnect, letting the upstream loose on the observer
                connectedPromise.success(Continue)
                isConnected = true
              }
            }
          case Done =>
            lock.synchronized {
              // downstream has terminated the subscription and doesn't want any
              // more items, so clearing the queue, since we are done
              queue.clear()
              queue = null
              isDone = true
              connectedPromise.success(Done)
              isConnected = true
            }
        }
      }
    }
  }

  def onNext(elem: T): Future[Ack] = {
    if (!isConnected) {
      // slow path - isConnected is not visible, so must synchronize
      lock.synchronized {
        // race condition could happen, checking again, as otherwise
        // the queue could be empty
        if (!isConnected) {
          // if still not connected, enqueue element
          queue.append(elem)
          connectedPromise.future
        }
        else if (!isDone) {
          // race condition happened, can send the event
          // directly if not completed by connect()
          observer.onNext(elem)
        }
        else {
          // observer was already completed in connect()
          // so we are done
          Done
        }
      }
    }
    else if (!isDone) {
      // isConnected is visible, so here the queue is definitely empty,
      // taking the fast path
      observer.onNext(elem)
    }
    else {
      // isDone happened either in connect(), in which case it is visible
      // because isConnected is visible, or in onComplete/onError
      Done
    }
  }

  def onError(ex: Throwable): Unit =
    if (isConnected) {
      // connection happened, taking the fast path
      // isDone could have been signaled during connection, in which case
      // we must not send anything
      if (!isDone) {
        isDone = true
        errorThrown = ex
        observer.onError(ex)
      }
    }
    else {
      // slow path
      lock.synchronized {
        // as long as isConnected is false, we cannot trust the value of isDone
        // so this must be synchronized
        if (!isDone) {
          errorThrown = ex
          isDone = true

          // we could have had a race condition here and changes to isConnected
          // are definitely visible within the lock
          if (isConnected)
            observer.onError(ex)
        }
      }
    }

  def onComplete(): Unit =
    if (isConnected) {
      // connection happened, taking the fast path
      // isDone could have been signaled during connection, in which case
      // we must not send anything
      if (!isDone) {
        isDone = true
        observer.onComplete()
      }
    }
    else {
      // slow path
      lock.synchronized {
        // as long as isConnected is false, we cannot trust the value of isDone
        // so this must be synchronized
        if (!isDone) {
          isDone = true
          // we could have had a race condition here and changes to isConnected
          // are definitely visible within the lock
          if (isConnected)
            observer.onComplete()
        }
      }
    }
}
