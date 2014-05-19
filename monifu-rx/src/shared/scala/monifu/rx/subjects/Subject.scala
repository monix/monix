package monifu.rx.subjects

import monifu.rx.{Observer, Observable}

trait Subject[T] extends Observable[T] with Observer[T]
