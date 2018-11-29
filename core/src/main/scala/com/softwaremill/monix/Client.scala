package com.softwaremill.monix

import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

object Client extends App with StrictLogging {
  CorrelationId.init()

  implicit val backend: SttpBackend[Task, Nothing] = new SetCorrelationIdBackend(AsyncHttpClientCatsBackend[Task]())

  CorrelationId.withNew {
    Task(logger.info("Result: " + sttp.get(uri"http://localhost:8081/test1").send().runSyncUnsafe().unsafeBody))
  }.runSyncUnsafe()

  backend.close()
}

object StressClient extends App with StrictLogging {
  CorrelationId.init()

  implicit val backend: SttpBackend[Task, Nothing] = new SetCorrelationIdBackend(AsyncHttpClientCatsBackend[Task]())

  def doTest: Task[Unit] = {
    CorrelationId.withNew {
      sttp.get(uri"http://localhost:8081/test1").send().map { resp =>
        val cid = CorrelationId().getOrElse("-")
        if (resp.unsafeBody.split(" ").forall(_ == cid)) {
          logger.info("Test OK")
        } else {
          logger.info("Test failed: " + resp.unsafeBody)
        }
      }
    }
  }

  Task.gatherUnordered((1 to 100).map(_ => doTest)).runSyncUnsafe()

  backend.close()
}
