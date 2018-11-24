package com.softwaremill.monix

import com.softwaremill.sttp.SttpBackend
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import org.http4s.server.blaze.BlazeBuilder
import monix.execution.Scheduler.Implicits.global

// -Dmonix.environment.localContextPropagation=1
object Server2 extends App with StrictLogging {
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

  BlazeBuilder[Task]
    .bindHttp(8082)
    .mountService(CorrelationId.setCorrelationIdMiddleware(service), "/")
    .serve
    .compile
    .drain
    .runSyncUnsafe()
}
