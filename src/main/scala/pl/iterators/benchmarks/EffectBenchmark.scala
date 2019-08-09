package pl.iterators.benchmarks
import java.util.concurrent.TimeUnit

import cats.Monad
import cats.data.EitherT
import cats.effect._
import cats.instances.AllInstances
import org.openjdk.jmh.annotations._
import scalaz.zio.internal.PlatformLive
import scalaz.zio.interop.CatsInstances
import scalaz.zio.{IO => _, _}

import scala.Function.const
import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object EffectBenchmark extends CatsInstances with AllInstances {
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  implicit val cs: ContextShift[IO]               = IO.contextShift(executionContext)
  private val runtime = new DefaultRuntime {
    override val Platform = PlatformLive.Default.withReportFailure(const(()))
  }

  def runZio[R1 >: runtime.Environment, A1](zio: ZIO[R1, _, A1]): A1 =
    runtime.unsafeRun(zio)
}

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 30, time = 5, timeUnit = TimeUnit.SECONDS)
class EffectBenchmark
    extends BenchmarkFunctions
    with IoBenchmarkFunctions
    with ZioBenchmarkFunctions {
  import EffectBenchmark._

  @noinline
  private def eitherTF[F[_]: Monad: ContextShift](
      benchmarkState: BenchmarkState)(
      fetch: ValidInput => F[Data],
      outsideWorld: Data => F[Either[UhOh, Output]],
      onFailure: UhOh => F[Unit],
      onOutput: Output => F[Result]) = {
    val baseTokens            = benchmarkState.baseTimeTokens
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val F = Monad[F]

    EitherT
      .pure[F, Invalid](benchmarkState.getSampleInput)
      .subflatMap(validateEitherStyle(validInvalidThreshold))
      .map(transform(baseTokens))
      .flatMapF(
        input =>
          F.productR(ContextShift[F].shift)(
            EitherT
              .right(fetch(input))
              .flatMapF(outsideWorld)
              .biSemiflatMap(
                err => F.as(onFailure(err), err.asInstanceOf[ThisIsError]),
                onOutput
              )
              .value))
      .value
  }

  @Benchmark
  @Fork(1)
  def eitherTIo(benchmarkState: BenchmarkState) = {
    val baseTokens       = benchmarkState.baseTimeTokens
    val timeFactor       = benchmarkState.timeFactor
    val failureThreshold = benchmarkState.failureThreshold

    val io = eitherTF[IO](benchmarkState)(
      fetchDataIo(baseTokens, timeFactor),
      outsideWorldEitherIo(failureThreshold, baseTokens, timeFactor),
      doIoWithFailure(baseTokens, timeFactor),
      doIoWithOutput(baseTokens, timeFactor)
    )

    io.unsafeRunSync()
  }

  @Benchmark
  @Fork(1)
  def eitherTZio(benchmarkState: BenchmarkState) = {
    val baseTokens       = benchmarkState.baseTimeTokens
    val timeFactor       = benchmarkState.timeFactor
    val failureThreshold = benchmarkState.failureThreshold

    val uio = eitherTF[UIO](benchmarkState)(
      fetchDataZio(baseTokens, timeFactor),
      outsideWorldEitherZio(failureThreshold, baseTokens, timeFactor),
      doZioWithFailure(baseTokens, timeFactor),
      doZioWithOutput(baseTokens, timeFactor)
    )

    runZio(uio)
  }

  @noinline
  private def feitherNoSyntax[F[_]: Monad: ContextShift](
      benchmarkState: BenchmarkState)(
      fetch: ValidInput => F[Data],
      outsideWorld: Data => F[Either[UhOh, Output]],
      onFailure: UhOh => F[Unit],
      onOutput: Output => F[Result]) = {
    val baseTokens            = benchmarkState.baseTimeTokens
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val F = Monad[F]

    F.flatMap(
      F.map(F.pure(benchmarkState.getSampleInput))(input =>
        validateEitherStyle(validInvalidThreshold)(input)
          .map(transform(baseTokens)))) {
      case Right(validInput) =>
        F.productR(ContextShift[F].shift)(
          F.flatMap(F.flatMap(fetch(validInput))(outsideWorld)) {
            case Right(output) =>
              F.map(onOutput(output))(Right(_): Either[ThisIsError, Result])
            case l @ Left(err) =>
              F.as(onFailure(err), l.asInstanceOf[Either[ThisIsError, Result]])
          })
      case left => F.pure(left.asInstanceOf[Either[ThisIsError, Result]])
    }
  }

  @Benchmark
  @Fork(1)
  def eitherIoNoSyntax(benchmarkState: BenchmarkState) = {
    val baseTokens       = benchmarkState.baseTimeTokens
    val timeFactor       = benchmarkState.timeFactor
    val failureThreshold = benchmarkState.failureThreshold

    val io = feitherNoSyntax[IO](benchmarkState)(
      fetchDataIo(baseTokens, timeFactor),
      outsideWorldEitherIo(failureThreshold, baseTokens, timeFactor),
      doIoWithFailure(baseTokens, timeFactor),
      doIoWithOutput(baseTokens, timeFactor)
    )

    io.unsafeRunSync()
  }

  @Benchmark
  @Fork(1)
  def eitherZioNoSyntax(benchmarkState: BenchmarkState) = {
    val baseTokens       = benchmarkState.baseTimeTokens
    val timeFactor       = benchmarkState.timeFactor
    val failureThreshold = benchmarkState.failureThreshold

    val uio = feitherNoSyntax[UIO](benchmarkState)(
      fetchDataZio(baseTokens, timeFactor),
      outsideWorldEitherZio(failureThreshold, baseTokens, timeFactor),
      doZioWithFailure(baseTokens, timeFactor),
      doZioWithOutput(baseTokens, timeFactor)
    )

    runZio(uio)
  }

  @noinline
  private def feitherSyntax[F[_]: Monad: ContextShift](
      benchmarkState: BenchmarkState)(
      fetch: ValidInput => F[Data],
      outsideWorld: Data => F[Either[UhOh, Output]],
      onFailure: UhOh => F[Unit],
      onOutput: Output => F[Result]) = {
    import cats.syntax.apply._
    import cats.syntax.flatMap._
    import cats.syntax.functor._

    val baseTokens            = benchmarkState.baseTimeTokens
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val F = Monad[F]

    F.pure(benchmarkState.getSampleInput)
      .map(input =>
        validateEitherStyle(validInvalidThreshold)(input).map(
          transform(baseTokens)))
      .flatMap {
        case Right(validInput) =>
          ContextShift[F].shift *> {
            fetch(validInput).flatMap(outsideWorld).flatMap {
              case Right(output) =>
                onOutput(output).map(Right(_): Either[ThisIsError, Result])
              case l @ Left(err) =>
                onFailure(err).as(l.asInstanceOf[Either[ThisIsError, Result]])
            }
          }
        case left => F.pure(left.asInstanceOf[Either[ThisIsError, Result]])
      }
  }

  @Benchmark
  @Fork(1)
  def eitherIoSyntax(benchmarkState: BenchmarkState) = {
    val baseTokens       = benchmarkState.baseTimeTokens
    val timeFactor       = benchmarkState.timeFactor
    val failureThreshold = benchmarkState.failureThreshold

    val io = feitherSyntax[IO](benchmarkState)(
      fetchDataIo(baseTokens, timeFactor),
      outsideWorldEitherIo(failureThreshold, baseTokens, timeFactor),
      doIoWithFailure(baseTokens, timeFactor),
      doIoWithOutput(baseTokens, timeFactor)
    )

    io.unsafeRunSync()
  }

  @Benchmark
  @Fork(1)
  def eitherZioSyntax(benchmarkState: BenchmarkState) = {
    val baseTokens       = benchmarkState.baseTimeTokens
    val timeFactor       = benchmarkState.timeFactor
    val failureThreshold = benchmarkState.failureThreshold

    val uio = feitherSyntax[UIO](benchmarkState)(
      fetchDataZio(baseTokens, timeFactor),
      outsideWorldEitherZio(failureThreshold, baseTokens, timeFactor),
      doZioWithFailure(baseTokens, timeFactor),
      doZioWithOutput(baseTokens, timeFactor)
    )

    runZio(uio)
  }
  /*
  @noinline
  private def fexceptions[F[_]: MonadError[?[_], Throwable]: ContextShift](benchmarkState: BenchmarkState)(
      fetch: ValidInput => F[Data],
      outsideWorld: Data => F[Output],
      onFailure: UhOh => F[Unit],
      onOutput: Output => F[Result]) = {
    import cats.syntax.apply._
    import cats.syntax.flatMap._
    import cats.syntax.functor._

    val baseTokens            = benchmarkState.baseTimeTokens
    val validInvalidThreshold = benchmarkState.validInvalidThreshold

    val F = MonadError[F, Throwable]

    F.pure(benchmarkState.getSampleInput)
      .flatMap(input => F.fromTry(Try(validateExceptionStyle(validInvalidThreshold)(input))))
      .map(transform(baseTokens))
      .flatMap { validInput =>
        ContextShift[F].shift *>
          fetch(validInput)
            .flatMap(data =>
              F.handleErrorWith(outsideWorld(data)) {
                case err: UhOhException => onFailure(err.uhOh).flatMap(_ => F.raiseError(err))
                case otherwise          => F.raiseError(otherwise)
            })
            .flatMap(onOutput)
      }

  }

  @Benchmark
  @Fork(1)
  def exceptionsIo(benchmarkState: BenchmarkState) = {
    val baseTokens       = benchmarkState.baseTimeTokens
    val timeFactor       = benchmarkState.timeFactor
    val failureThreshold = benchmarkState.failureThreshold

    val io = fexceptions[IO](benchmarkState)(
      fetchDataIo(baseTokens, timeFactor),
      outsideWorldIo(failureThreshold, baseTokens, timeFactor),
      doIoWithFailure(baseTokens, timeFactor),
      doIoWithOutput(baseTokens, timeFactor)
    )

    io.attempt.unsafeRunSync()
  }*/
}
