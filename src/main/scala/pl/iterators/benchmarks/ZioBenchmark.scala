package pl.iterators.benchmarks
import java.util.concurrent.TimeUnit

import cats.data.EitherT
import org.openjdk.jmh.annotations._
import scalaz.zio._
import scalaz.zio.blocking.Blocking
import scalaz.zio.internal.PlatformLive
import scalaz.zio.interop.CatsInstances

import scala.Function.const

object ZioBenchmark extends CatsInstances {
  import blocking._

  val runtime = new DefaultRuntime {
    override val Platform = PlatformLive.Default.withReportFailure(const(()))
  }

  def run[R1 >: runtime.Environment, A1](zio: ZIO[R1, _, A1]): A1 =
    runtime.unsafeRun(zio)

  def runCause[R1 >: runtime.Environment, E1, A1](zio: ZIO[R1, E1, A1]): Exit[E1, A1] = runtime.unsafeRunSync(zio)
  def block[R1, E1, A1](zio: ZIO[R1, E1, A1])                                         = blocking(zio)
}

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 30, time = 5, timeUnit = TimeUnit.SECONDS)
class ZioBenchmark extends BenchmarkFunctions with ZioBenchmarkFunctions {
  import ZioBenchmark._

  @Benchmark
  @Fork(1)
  def eitherT(benchmarkState: BenchmarkState) = {
    val baseTokens            = benchmarkState.baseTimeTokens
    val timeFactor            = benchmarkState.timeFactor
    val failureThreshold      = benchmarkState.failureThreshold
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val zio = EitherT
      .pure[ZIO[Blocking, Nothing, ?], Invalid](benchmarkState.getSampleInput)
      .subflatMap(validateEitherStyle(validInvalidThreshold))
      .map(transform(baseTokens))
      .flatMapF(
        input =>
          block(
            EitherT
              .right(fetchDataZio(baseTokens, timeFactor)(input))
              .flatMapF(outsideWorldEitherZio(failureThreshold, baseTokens, timeFactor))
              .biSemiflatMap(
                err => doZioWithFailure(baseTokens, timeFactor)(err).andThen(ZIO.succeed(err)),
                doZioWithOutput(baseTokens, timeFactor)
              )
              .value
          )
      )
      .value

    run(zio)
  }

  @Benchmark
  @Fork(1)
  def either(benchmarkState: BenchmarkState) = {
    val baseTokens            = benchmarkState.baseTimeTokens
    val timeFactor            = benchmarkState.timeFactor
    val failureThreshold      = benchmarkState.failureThreshold
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val zio = ZIO
      .succeed(benchmarkState.getSampleInput)
      .map(input => validateEitherStyle(validInvalidThreshold)(input).map(transform(baseTokens)))
      .flatMap {
        case Right(validInput) =>
          block {
            fetchDataZio(baseTokens, timeFactor)(validInput)
              .flatMap(outsideWorldEitherZio(failureThreshold, baseTokens, timeFactor))
              .flatMap {
                case Right(output) =>
                  doZioWithOutput(baseTokens, timeFactor)(output).map(Right(_))
                case l @ Left(err) =>
                  doZioWithFailure(baseTokens, timeFactor)(err).andThen(ZIO.succeed(l.asInstanceOf[Either[ThisIsError, Result]]))
              }
          }
        case left => ZIO.succeed(left.asInstanceOf[Either[ThisIsError, Result]])
      }

    run(zio)
  }

  @Benchmark
  @Fork(1)
  def exceptions(benchmarkState: BenchmarkState) = {
    val baseTokens            = benchmarkState.baseTimeTokens
    val timeFactor            = benchmarkState.timeFactor
    val failureThreshold      = benchmarkState.failureThreshold
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val zio = ZIO {
      validateExceptionStyle(validInvalidThreshold)(benchmarkState.getSampleInput)
    }.map(transform(baseTokens))
      .flatMap(
        input =>
          block {
            fetchDataZio(baseTokens, timeFactor)(input)
              .flatMap(
                data =>
                  outsideWorldZio(failureThreshold, baseTokens, timeFactor)(data)
                    .catchSome {
                      case err: UhOhException =>
                        doZioWithFailure(baseTokens, timeFactor)(err.uhOh).andThen(ZIO.fail(err))
                    }
              )
              .flatMap(doZioWithOutput(baseTokens, timeFactor))
          }
      )

    runCause(zio)
  }

  @Benchmark
  @Fork(1)
  def zio(benchmarkState: BenchmarkState) = {
    val baseTokens            = benchmarkState.baseTimeTokens
    val timeFactor            = benchmarkState.timeFactor
    val failureThreshold      = benchmarkState.failureThreshold
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val zio = ZIO
      .succeed(benchmarkState.getSampleInput)
      .map(input => validateEitherStyle(validInvalidThreshold)(input).map(transform(baseTokens)))
      .absolve
      .flatMap(
        validInput =>
          block {
            fetchDataZio(baseTokens, timeFactor)(validInput)
              .flatMap(
                data =>
                  outsideWorldEitherZio(failureThreshold, baseTokens, timeFactor)(data).absolve
                    .catchAll(err => doZioWithFailure(baseTokens, timeFactor)(err).andThen(ZIO.fail(err)))
              )
              .flatMap(doZioWithOutput(baseTokens, timeFactor))
          }
      )

    runCause(zio)
  }

}
