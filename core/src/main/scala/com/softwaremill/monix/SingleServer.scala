package com.softwaremill.monix

import cats.effect.Resource
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import doobie.h2.H2Transactor
import doobie.implicits._
import doobie.util.ExecutionContexts
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.flywaydb.core.Flyway
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder

// -Dmonix.environment.localContextPropagation=1
object SingleServer extends App with StrictLogging {
  Flyway
    .configure()
    .dataSource("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")
    .load()
    .migrate()

  val transactor: Resource[Task, H2Transactor[Task]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[Task](32)
      te <- ExecutionContexts.cachedThreadPool[Task]
      xa <- H2Transactor.newH2Transactor[Task]("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "", ce, te
      )
    } yield xa

  //

  val doWork = Task {
    logger.info(s"Doing hard work!")
  }

  val dsl = Http4sDsl[Task]

  import dsl._

  transactor.use { xa =>
    val service = HttpRoutes.of[Task] {
      case GET -> Root / "test" =>
        Task.gatherUnordered((1 to 3).map(_ => doWork)) *>
          sql"SELECT COUNT(*) FROM TEST".query[Option[Int]].unique.transact(xa).flatMap { result =>
            logger.info(s"Number of tests in the database: still $result")
            Ok(result.toString)
          }
    }

    BlazeBuilder[Task]
      .bindHttp(8080)
      .mountService(CorrelationId.setCorrelationIdMiddleware(service), "/")
      .resource.use { _ =>
      Task {
        import com.softwaremill.sttp._
        import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
        implicit val backend: SttpBackend[Task, Nothing] = AsyncHttpClientCatsBackend[Task]()
        println(sttp.get(uri"http://localhost:8080/test").send().runSyncUnsafe())
        println("-")
        println(sttp.get(uri"http://localhost:8080/test").send().runSyncUnsafe())
        backend.close()
      }
    }
  }.runSyncUnsafe()

  logger.info("Server done")
}
