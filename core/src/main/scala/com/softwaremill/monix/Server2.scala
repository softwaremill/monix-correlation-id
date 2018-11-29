package com.softwaremill.monix

import com.softwaremill.sttp.{SttpBackend, _}
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder

object Server2 extends App with StrictLogging {
  CorrelationId.init()

  implicit val backend: SttpBackend[Task, Nothing] = new SetCorrelationIdBackend(AsyncHttpClientCatsBackend[Task]())

  val dsl = Http4sDsl[Task]

  import dsl._

  val service = HttpRoutes.of[Task] {
    case GET -> Root / "test2" =>
      logger.info("Delegating to server 3")
      Task.gatherUnordered((1 to 2).map(_ => sttp.get(uri"http://localhost:8083/test3").send())).flatMap { results =>
        Ok(results.map(_.unsafeBody).mkString(" ") + " " + CorrelationId().getOrElse("???"))
      }
  }

  BlazeServerBuilder[Task]
    .bindHttp(8082)
    .withHttpApp(CorrelationId.setCorrelationIdMiddleware(service).orNotFound)
    .serve
    .compile
    .drain
    .runSyncUnsafe()
}
