package monix.eval

import cats.functor.Contravariant

import scala.util.{Failure, Success}

object CallbackSuite extends BaseTestSuite {

  case class TestCallback(success: Int => Unit = _ => (),
                          error: Throwable => Unit = _ => ()) extends Callback[Int] {
    var successCalled = false
    var errorCalled = false

    override def onSuccess(value: Int): Unit = {
      successCalled = true
      success(value)
    }

    override def onError(ex: Throwable): Unit = {
      errorCalled = true
      error(ex)
    }
  }

  test("onValue should invoke onSuccess") { implicit s =>
    val callback = TestCallback()
    callback.onValue(1)
    assert(callback.successCalled)
  }

  test("apply Success(value) should invoke onSuccess") { implicit s =>
    val callback = TestCallback()
    callback(Success(1))
    assert(callback.successCalled)
  }

  test("apply Failure(ex) should invoke onError") { implicit s =>
    val callback = TestCallback()
    callback(Failure(new IllegalStateException()))
    assert(callback.errorCalled)
  }

  test("contramap should invoke function before invoking callback") { implicit s =>
    val callback = TestCallback()
    val stringCallback = callback.contramap[String](_.toInt)
    stringCallback.onSuccess("1")
    assert(callback.successCalled)
  }

  test("contramap should invoke onError if the function throws") { implicit s =>
    val callback = TestCallback()
    val stringCallback = callback.contramap[String](_.toInt)
    stringCallback.onSuccess("not a int")
    assert(callback.errorCalled)
  }

  test("contramp has a cats Contramap instance") { implicit s =>
    val instance = implicitly[Contravariant[Callback]]
    val callback = TestCallback()
    val stringCallback = instance.contramap(callback)((x: String) => x.toInt)
    stringCallback.onSuccess("1")
    assert(callback.successCalled)
  }
}
