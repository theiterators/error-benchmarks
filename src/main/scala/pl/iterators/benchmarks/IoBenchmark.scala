package pl.iterators.benchmarks
import java.util.concurrent.TimeUnit

import cats.data.EitherT
import cats.effect.IO
import org.openjdk.jmh.annotations._

import scala.concurrent._

object IoBenchmark {
  implicit val executionContext: ExecutionContext = ExecutionContext.global

  def shift[A](io: IO[A])(implicit ec: ExecutionContext) =
    IO.shift(ec).flatMap(_ => io)
}

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 30, time = 5, timeUnit = TimeUnit.SECONDS)
class IoBenchmark extends BenchmarkFunctions with IoBenchmarkFunctions {
  import IoBenchmark._

  @Benchmark
  @Fork(1)
  def eitherT(benchmarkState: BenchmarkState) = {
    val baseTokens            = benchmarkState.baseTimeTokens
    val timeFactor            = benchmarkState.timeFactor
    val failureThreshold      = benchmarkState.failureThreshold
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val io = EitherT
      .pure[IO, Invalid](benchmarkState.getSampleInput)
      .subflatMap(validateEitherStyle(validInvalidThreshold))
      .map(transform(baseTokens))
      .flatMapF(input =>
        shift {
          EitherT
            .right(fetchDataIo(baseTokens, timeFactor)(input))
            .flatMapF(
              outsideWorldEitherIo(failureThreshold, baseTokens, timeFactor))
            .biSemiflatMap(
              err => doIoWithFailure(baseTokens, timeFactor)(err).map(_ => err),
              doIoWithOutput(baseTokens, timeFactor)
            )
            .value
      })
      .value

    io.unsafeRunSync()
  }

  @Benchmark
  @Fork(1)
  def either(benchmarkState: BenchmarkState) = {
    val baseTokens            = benchmarkState.baseTimeTokens
    val timeFactor            = benchmarkState.timeFactor
    val failureThreshold      = benchmarkState.failureThreshold
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val io = IO
      .pure(benchmarkState.getSampleInput)
      .map(input =>
        validateEitherStyle(validInvalidThreshold)(input).map(
          transform(baseTokens)))
      .flatMap {
        case Right(validInput) =>
          shift {
            fetchDataIo(baseTokens, timeFactor)(validInput)
              .flatMap(
                outsideWorldEitherIo(failureThreshold, baseTokens, timeFactor))
              .flatMap {
                case Right(output) =>
                  doIoWithOutput(baseTokens, timeFactor)(output).map(Right(_))
                case l @ Left(err) =>
                  doIoWithFailure(baseTokens, timeFactor)(err).map(_ =>
                    l.asInstanceOf[Either[ThisIsError, Result]])
              }
          }
        case left => IO.pure(left.asInstanceOf[Either[ThisIsError, Result]])
      }

    io.unsafeRunSync()
  }

  @Benchmark
  @Fork(1)
  def exceptions(benchmarkState: BenchmarkState) = {
    val baseTokens            = benchmarkState.baseTimeTokens
    val timeFactor            = benchmarkState.timeFactor
    val failureThreshold      = benchmarkState.failureThreshold
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val io = IO {
      validateExceptionStyle(validInvalidThreshold)(
        benchmarkState.getSampleInput)
    }.map(transform(baseTokens))
      .flatMap(input =>
        shift {
          fetchDataIo(baseTokens, timeFactor)(input)
            .flatMap(outsideWorldIo(failureThreshold, baseTokens, timeFactor))
            .redeemWith(
              {
                case err: UhOhException =>
                  doIoWithFailure(baseTokens, timeFactor)(err.uhOh).flatMap(_ =>
                    IO.raiseError(err))
                case otherThrowable => IO.raiseError(otherThrowable)
              },
              doIoWithOutput(baseTokens, timeFactor)
            )
      })

    io.attempt.unsafeRunSync()
  }

}
