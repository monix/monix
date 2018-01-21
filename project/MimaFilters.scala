/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.core.ProblemFilters.exclude

/** Backwards compatibility is preserved for public APIs only, not for
  * package-private APIs.
  *
  * These filters out package-private binary incompatible changes.
  */
object MimaFilters {
  /** `monix-execution` <= 2.2.4 */
  lazy val executionChangesFor_2_2_4 = Seq(
    exclude[IncompatibleMethTypeProblem]("monix.execution.cancelables.CompositeCancelable.this")
  )

  /** `monix-eval` <= 2.2.4 */
  lazy val evalChangesFor_2_2_4 = Seq(
    // Related to issue: https://github.com/monix/monix/issues/313
    exclude[DirectMissingMethodProblem]("monix.eval.internal.TaskFromFuture.apply")
  )

  /** `monix-eval` 2.3.0 */
  lazy val evalChangesFor_2_3_0 = Seq(
    exclude[DirectMissingMethodProblem]("monix.eval.Task.internalStartTrampolineRunLoop"),
    exclude[MissingClassProblem]("monix.eval.Coeval$BindSuspend"),
    exclude[MissingClassProblem]("monix.eval.Coeval$BindSuspend$"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task#MemoizeSuspend.execute"),
    exclude[MissingTypesProblem]("monix.eval.Task$Context$"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task#Eval.f"),
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval.trampoline")
  )

  /** `monix-reactive` <= 2.2.4 */
  lazy val reactiveChangesFor_2_2_4 = Seq(
    exclude[MissingTypesProblem]("monix.reactive.internal.operators.ConcatMapObservable$FlatMapState$WaitComplete$"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.ConcatMapObservable#FlatMapState#WaitComplete.apply"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.ConcatMapObservable#FlatMapState#WaitComplete.copy"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.ConcatMapObservable#FlatMapState#WaitComplete.this"),
    exclude[MissingTypesProblem]("monix.reactive.internal.operators.MapTaskObservable$FlatMapState$WaitComplete$"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.MapTaskObservable#FlatMapState#WaitComplete.apply"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.MapTaskObservable#FlatMapState#WaitComplete.copy"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.MapTaskObservable#FlatMapState#WaitComplete.this"),
    exclude[MissingClassProblem]("monix.reactive.internal.operators.MapAsyncParallelObservable$MapAsyncParallelSubscriber")
  )

  /** `monix-reactive` 2.3.0 */
  lazy val reactiveChangesFor_2_3_0 = Seq(
    exclude[DirectMissingMethodProblem]("monix.reactive.observers.buffers.AbstractBackPressuredBufferedSubscriber.secondaryQueue"),
    exclude[DirectMissingMethodProblem]("monix.reactive.observers.buffers.AbstractBackPressuredBufferedSubscriber.primaryQueue"),
    exclude[DirectMissingMethodProblem]("monix.reactive.observers.buffers.ConcurrentQueue.unbounded"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$CompleteConsumer$"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$HeadOptionConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$CancelledConsumer$"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$FromObserverConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$IndexedSubscriber"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$ForeachConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$HeadConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$MapAsyncConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$FoldLeftAsyncConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$State"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$ForeachAsyncConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$FirstNotificationConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$MapConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$Available"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$Waiting"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$Available$"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$Waiting$"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$FoldLeftConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$IndexedSubscriber$"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$ContraMapConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$CreateConsumer"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$LoadBalanceConsumer$AsyncQueue"),
    exclude[MissingClassProblem]("monix.reactive.Consumer$RaiseErrorConsumer"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.ReactiveObservable.this"),
    exclude[MissingTypesProblem]("monix.reactive.internal.rstreams.SubscriberAsReactiveSubscriber"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.rstreams.SubscriberAsReactiveSubscriber.onError"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.rstreams.SubscriberAsReactiveSubscriber.onSubscribe"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.rstreams.SubscriberAsReactiveSubscriber.onComplete"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.rstreams.SubscriberAsReactiveSubscriber.onNext"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.rstreams.SubscriberAsReactiveSubscriber.this"),
    exclude[MissingClassProblem]("monix.reactive.internal.rstreams.SyncSubscriberAsReactiveSubscriber$")
  )

  lazy val executionChangesFor_2_3_1 = Seq(
    exclude[MissingClassProblem]("monix.execution.schedulers.ExecutorScheduler$DeferredRunnable"),
    exclude[MissingClassProblem]("monix.execution.schedulers.AsyncScheduler$DeferredRunnable")
  )

  lazy val reactiveChangesFor_2_4_0 = Seq(
    exclude[MissingClassProblem]("monix.reactive.internal.operators.MapTaskObservable$FlatMapState$Active"),
    exclude[MissingClassProblem]("monix.reactive.internal.operators.MapTaskObservable$FlatMapState$WaitActiveTask$"),
    exclude[MissingClassProblem]("monix.reactive.internal.operators.MapTaskObservable$FlatMapState"),
    exclude[MissingClassProblem]("monix.reactive.internal.operators.MapTaskObservable$FlatMapState$WaitOnNext$"),
    exclude[MissingClassProblem]("monix.reactive.internal.operators.MapTaskObservable$FlatMapState$Active$"),
    exclude[MissingClassProblem]("monix.reactive.internal.operators.MapTaskObservable$FlatMapState$WaitComplete$"),
    exclude[MissingClassProblem]("monix.reactive.internal.operators.MapTaskObservable$FlatMapState$WaitComplete"),
    exclude[MissingClassProblem]("monix.reactive.internal.operators.MapTaskObservable$FlatMapState$Cancelled$"),
    exclude[MissingClassProblem]("monix.reactive.internal.operators.MapTaskObservable$FlatMapState$")
  )
}