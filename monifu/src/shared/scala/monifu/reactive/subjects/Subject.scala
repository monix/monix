package monifu.reactive.subjects

import monifu.reactive.{Observer, Observable}

trait Subject[T] extends Observable[T] with Observer[T]
