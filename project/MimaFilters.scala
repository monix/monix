/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

  /** `monix-execution` 3.0.0 */
  lazy val execChangesFor_3_0_0 = Seq(
    exclude[DirectMissingMethodProblem]("monix.execution.Scheduler#Extensions.executeLocal"),
    exclude[DirectMissingMethodProblem]("monix.execution.cancelables.StackedCancelable.popAndCollapse"),
    exclude[DirectMissingMethodProblem]("monix.execution.schedulers.ExecuteExtensions.executeLocal"),
    exclude[MissingClassProblem]("monix.execution.schedulers.package"),
    exclude[MissingClassProblem]("monix.execution.schedulers.package$"),
    exclude[MissingClassProblem]("monix.execution.schedulers.package$ExecutionModel$")
  )

  /** `monix-eval` 3.0.0 */
  lazy val evalChangesFor_3_0_0 = Seq(
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval#Once.runAttempt"),
    exclude[MissingClassProblem]("monix.eval.Coeval$Attempt"),
    exclude[MissingClassProblem]("monix.eval.TaskInstances$TypeClassInstances"),
    exclude[IncompatibleResultTypeProblem]("monix.eval.TaskInstances.nondeterminism"),
    exclude[MissingClassProblem]("monix.eval.Coeval$TypeClassInstances"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task.typeClassInstances"),
    exclude[IncompatibleResultTypeProblem]("monix.eval.Task.nondeterminism"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task.dematerializeAttempt"),
    exclude[DirectMissingMethodProblem]("monix.eval.Task.materializeAttempt"),
    exclude[IncompatibleMethTypeProblem]("monix.eval.Task.runAsync"),
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval#Always.runAttempt"),
    exclude[MissingTypesProblem]("monix.eval.Coeval$Error"),
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval#Error.runAttempt"),
    exclude[MissingClassProblem]("monix.eval.Coeval$Attempt$"),
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval.dematerializeAttempt"),
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval.runAttempt"),
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval.materializeAttempt"),
    exclude[MissingTypesProblem]("monix.eval.Coeval$Now"),
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval#Now.runAttempt"),
    exclude[DirectMissingMethodProblem]("monix.eval.Coeval.typeClassInstances"),
    exclude[IncompatibleResultTypeProblem]("monix.eval.internal.CoevalRunLoop.start")
  )

  /** `monix-react` 3.0.0 */
  lazy val reactiveChangesFor_3_0_0 = Seq(
    exclude[DirectMissingMethodProblem]("monix.reactive.Observable.typeClassInstances"),
    exclude[MissingClassProblem]("monix.reactive.Observable$TypeClassInstances"),
    exclude[DirectMissingMethodProblem]("monix.reactive.Observable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.subjects.Subject.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.EchoObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.UpstreamTimeoutObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.DoOnSubscribeObservable#After.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.OnErrorRetryCountedObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.DownstreamTimeoutObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.BufferWithSelectorObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.DebounceObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.FoldWhileObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.DelaySubscriptionByTimespanObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.MapAsyncParallelObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.DelaySubscriptionWithTriggerObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.MergeMapObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.SubscribeOnObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.ThrottleLastObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.OnErrorRetryIfObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.ExecuteOnObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.FoldLeftObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.BufferIntrospectiveObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.SwitchMapObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.ScanObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.RepeatSourceObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.RestartUntilObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.ObserveOnObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.OnErrorRecoverWithObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.ConcatMapObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.TakeLeftByTimespanObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.SwitchIfEmptyObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.DropByTimespanObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.FlatScanObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.DelayOnCompleteObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.DelayBySelectorObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.OnCancelTriggerErrorObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.BufferTimedObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.WithLatestFromObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.DoOnSubscribeObservable#Before.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.DelayByTimespanObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.TakeUntilObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.DropUntilObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.MapTaskObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.PipeThroughObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.DoOnSubscriptionCancelObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.operators.DumpObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.Zip5Observable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.ExecuteWithForkObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.IteratorAsObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.CombineLatest3Observable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.ErrorObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.PipeThroughSelectorObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.CombineLatest4Observable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.CombineLatest6Observable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.EmptyObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.RepeatObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.StateActionObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.InputStreamObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.CombineLatest5Observable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.AsyncStateActionObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.IntervalFixedRateObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.IterableAsObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.CharsReaderObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.Zip6Observable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.RepeatedValueObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.DeferObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.RunnableObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.RepeatEvalObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.CreateObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.UnsafeCreateObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.CombineLatest2Observable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.Zip3Observable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.FirstStartedObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.ReactiveObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.IntervalFixedDelayObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.RepeatOneObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.EvalAlwaysObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.ExecuteWithModelObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.RangeObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.EvalOnceObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.NowObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.Zip4Observable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.TailRecMObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.Interleave2Observable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.Zip2Observable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.ConsObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.TaskAsObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.FutureAsObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.LinesReaderObservable.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.internal.builders.NeverObservable.runWith"),
    exclude[MissingClassProblem]("monix.reactive.exceptions.MultipleSubscribersException"),
    exclude[MissingClassProblem]("monix.reactive.exceptions.CompositeException"),
    exclude[MissingClassProblem]("monix.reactive.exceptions.CompositeException$"),
    exclude[MissingClassProblem]("monix.reactive.exceptions.MultipleSubscribersException$"),
    exclude[MissingClassProblem]("monix.reactive.exceptions.BufferOverflowException"),
    exclude[MissingClassProblem]("monix.reactive.exceptions.DownstreamTimeoutException"),
    exclude[MissingClassProblem]("monix.reactive.exceptions.UpstreamTimeoutException"),
    exclude[MissingClassProblem]("monix.reactive.exceptions.UpstreamTimeoutException$"),
    exclude[MissingClassProblem]("monix.reactive.exceptions.BufferOverflowException$"),
    exclude[MissingClassProblem]("monix.reactive.exceptions.DownstreamTimeoutException$"),
    exclude[DirectMissingMethodProblem]("monix.reactive.observables.CachedObservable.runWith"),
    exclude[MissingClassProblem]("monix.reactive.observables.ObservableLike$DeprecatedMethods$"),
    exclude[DirectMissingMethodProblem]("monix.reactive.observables.ObservableLike.DeprecatedMethods"),
    exclude[DirectMissingMethodProblem]("monix.reactive.observables.GroupedObservable#Implementation.runWith"),
    exclude[DirectMissingMethodProblem]("monix.reactive.observables.RefCountObservable.runWith"),
    exclude[MissingClassProblem]("monix.reactive.observables.ObservableLike$DeprecatedMethods")
  )
}
