package com.example

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.{FutureTask, TimeUnit}

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker

import scala.concurrent.{blocking, Await, ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

object FutureTest {

  def main(args: Array[String]): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    Thread.currentThread().setName("Main Thread")

    withAwait()
    withFutureTask()
    val breaker: CircuitBreaker = getCircuitBreaker(ActorSystem("TheOne"))
    withCircuitBreaker(breaker)
    Thread.sleep(10000)
    withCircuitBreaker(breaker)
    Thread.sleep(10000)
    withCircuitBreaker(breaker)

    while (true) {}
  }

  def withAwait()(implicit executionContext: ExecutionContext): Unit = {
    Future {
      Thread.currentThread().setName("Waiting for the Timer")
      Await.result(Future(longFunction("await")), FiniteDuration(5, TimeUnit.SECONDS))
    } recoverWith {
      case e: Exception =>
        println("withAwait: " + e)
        Future.failed(e)
    }
  }

  def withFutureTask()(implicit executionContext: ExecutionContext): Unit = {
    timed(longFunction("futuretask"), FiniteDuration(5, TimeUnit.SECONDS)).andThen {
      case result => println("withFutureTask: " + result)
    }
  }

  def timed[T](fn: => T, duration: FiniteDuration)(implicit executionContext: ExecutionContext): Future[T] = {
    val task: FutureTask[T] = new FutureTask(() => fn)
    executionContext.execute(task)

    Future(
      blocking { // probably extraneous
        Thread.currentThread().setName("future task get")

        try {
          task.get(duration.toMillis, TimeUnit.MILLISECONDS)
        }
        finally {
          // make sure to actually interrupt our running code
          task.cancel(true)
        }
      }
    )
  }

  def getCircuitBreaker(actorSystem: ActorSystem)(implicit executionContext: ExecutionContext): CircuitBreaker =
    new CircuitBreaker(executor = executionContext,
      scheduler = actorSystem.scheduler,
      maxFailures = 1,
      callTimeout = Duration.of(5, ChronoUnit.SECONDS),
      resetTimeout = Duration.of(1, ChronoUnit.MINUTES))
      .onOpen(println("My CircuitBreaker is now open, and will not close for one minute"))
      .onClose(println("My CircuitBreaker is now closed"))

  def withCircuitBreaker(breaker: CircuitBreaker)(implicit executionContext: ExecutionContext): Unit = {
    breaker.withCircuitBreaker(Future(longFunction("CircuitBreaker"))) recoverWith {
      case e: Exception =>
        println("withCircuitBreaker: " + e)
        Future.failed(e)
    }
  }

  def longFunction(name: String): Int = {
    Thread.currentThread().setName(s"Long Function Thread: $name")

    var last: Long = System.currentTimeMillis();
    while (!Thread.interrupted()) {
      val current: Long = System.currentTimeMillis()
      if (current - last > 1000) {
        last = current
        println(s"$name: still working")
      }
    }
    1
  }
}
