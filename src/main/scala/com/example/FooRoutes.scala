package com.example

import java.io.File
import java.nio.file.{ Files, Paths }

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.server.{ RequestContext, Route, RouteResult }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.RouteResult.{ Complete, Rejected }
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging

import scala.util.{ Failure, Success, Try }

trait FooRoutes extends AroundDirectives with LazyLogging {
  implicit def system: ActorSystem

  def timeRequest(ctx: RequestContext): Try[RouteResult] => Unit = {
    val start = System.currentTimeMillis()

    {
      case Success(Complete(resp)) =>
        val d = System.currentTimeMillis() - start
        logger.info(s"[${resp.status.intValue()}] ${ctx.request.method.name} " +
          s"${ctx.request.uri} took: ${d}ms")
      case Success(Rejected(_)) =>
      case Failure(_) =>
    }
  }

  lazy val fooRoutes: Route = aroundRequest(timeRequest) {
    path("foo" ~ Slash.?) {
      complete(HttpEntity(ContentTypes.`application/octet-stream`, ByteString(Files.readAllBytes(Paths.get("foo")))))
    } ~ path("fooc" ~ Slash.?) {
      complete(HttpEntity.fromFile(ContentTypes.`application/octet-stream`, new File("foo")))
    }
  }
}
