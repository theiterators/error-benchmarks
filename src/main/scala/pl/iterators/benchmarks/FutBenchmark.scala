package pl.iterators.benchmarks
import java.util.concurrent.TimeUnit

import cats.data.EitherT
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import scala.concurrent._

object FutBenchmark {
  implicit val executionContext: ExecutionContext = ExecutionContext.global

  def await[A](fut: Future[A], bh: Blackhole) = {
    while (fut.value.isEmpty) {}
    bh.consume(fut.value)
    fut.value.get
  }
}

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 30, time = 5, timeUnit = TimeUnit.SECONDS)
class FutBenchmark extends BenchmarkFunctions {
  import FutBenchmark._
  import cats.instances.future._

  @Benchmark
  @Fork(1)
  def eitherT(benchmarkState: BenchmarkState, blackhole: Blackhole) = {
    val baseTokens            = benchmarkState.baseTimeTokens
    val timeFactor            = benchmarkState.timeFactor
    val failureThreshold      = benchmarkState.failureThreshold
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val fut = EitherT
      .pure[Future, Invalid](benchmarkState.getSampleInput)
      .subflatMap(validateEitherStyle(validInvalidThreshold))
      .map(transform(baseTokens))
      .semiflatMap(fetchData(baseTokens, timeFactor))
      .flatMapF(outsideWorldEither(failureThreshold, baseTokens, timeFactor))
      .biSemiflatMap(
        {
          case err: UhOh =>
            doSomethingWithFailure(baseTokens, timeFactor)(err).map(_ => err)
          case otherwise => Future.successful(otherwise)
        },
        doSomethingWithOutput(baseTokens, timeFactor)
      )
      .value

    await(fut, blackhole)
  }

  @Benchmark
  @Fork(1)
  def either(benchmarkState: BenchmarkState, blackhole: Blackhole) = {
    val baseTokens            = benchmarkState.baseTimeTokens
    val timeFactor            = benchmarkState.timeFactor
    val failureThreshold      = benchmarkState.failureThreshold
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val fut = Future
      .successful(benchmarkState.getSampleInput)
      .map(input => validateEitherStyle(validInvalidThreshold)(input).map(transform(baseTokens)))
      .flatMap {
        case Right(data) =>
          fetchData(baseTokens, timeFactor)(data)
            .flatMap(outsideWorldEither(failureThreshold, baseTokens, timeFactor))
            .flatMap {
              case Right(output) =>
                doSomethingWithOutput(baseTokens, timeFactor)(output)
                  .map(Right(_))
              case l @ Left(err) =>
                doSomethingWithFailure(baseTokens, timeFactor)(err).map(_ => l.asInstanceOf[Either[ThisIsError, Result]])
            }
        case left =>
          Future.successful(left.asInstanceOf[Either[ThisIsError, Result]])
      }

    await(fut, blackhole)
  }

  @Benchmark
  @Fork(1)
  def exceptions(benchmarkState: BenchmarkState, blackhole: Blackhole) = {
    val baseTokens            = benchmarkState.baseTimeTokens
    val timeFactor            = benchmarkState.timeFactor
    val failureThreshold      = benchmarkState.failureThreshold
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val fut = Future {
      validateExceptionStyle(validInvalidThreshold)(benchmarkState.getSampleInput)
    }.map(transform(baseTokens))
      .flatMap(fetchData(baseTokens, timeFactor))
      .flatMap(
        data =>
          outsideWorldException(failureThreshold, baseTokens, timeFactor)(data)
            .recoverWith {
              case err: UhOhException =>
                doSomethingWithFailure(baseTokens, timeFactor)(err.uhOh).flatMap(_ => Future.failed(err))
            }
      )
      .flatMap(doSomethingWithOutput(baseTokens, timeFactor))

    await(fut, blackhole)
  }

}
