package com.softwaremill.monix

import com.softwaremill.sttp.{SttpBackend, _}
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeServerBuilder
import monix.execution.Scheduler.Implicits.global
import org.http4s.implicits._

object Server1 extends App with StrictLogging {
  CorrelationId.init()

  implicit val backend: SttpBackend[Task, Nothing] = new SetCorrelationIdBackend(AsyncHttpClientCatsBackend[Task]())

  val dsl = Http4sDsl[Task]

  import dsl._

  val service = HttpRoutes.of[Task] {
    case GET -> Root / "test1" =>
      logger.info("Delegating to server 2")
      sttp.get(uri"http://localhost:8082/test2").send().flatMap { result =>
        Ok(result.unsafeBody + " " + CorrelationId().getOrElse("???"))
      }
  }

  BlazeServerBuilder[Task]
    .bindHttp(8081)
    .withHttpApp(CorrelationId.setCorrelationIdMiddleware(service).orNotFound)
    .serve
    .compile
    .drain
    .runSyncUnsafe()
}
