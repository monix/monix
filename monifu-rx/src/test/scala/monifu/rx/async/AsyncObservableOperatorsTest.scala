package monifu.rx.async

import scala.concurrent.ExecutionContext.Implicits.global

class AsyncObservableOperatorsTest
  extends monifu.rx.GenericObservableOperatorsTest[Observable](Observable.Builder)
