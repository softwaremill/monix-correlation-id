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
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.implicits._

// -Dmonix.environment.localContextPropagation=1
object Server3 extends App with StrictLogging {
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
    CorrelationId()
  }

  val dsl = Http4sDsl[Task]

  import dsl._

  transactor.use { xa =>
    val service = HttpRoutes.of[Task] {
      case GET -> Root / "test3" =>
        Task.gatherUnordered((1 to 2).map(_ => doWork)).map(_.map(_.getOrElse("???")).mkString(" " )).flatMap { results1 =>
          sql"SELECT COUNT(*) FROM TEST".query[Option[Int]].unique.transact(xa).flatMap { dbResult =>
            logger.info(s"Number of tests in the database: still $dbResult")
            Ok(results1 + " " + CorrelationId().getOrElse("???"))
          }
        }
    }

    BlazeServerBuilder[Task]
      .bindHttp(8083)
      .withHttpApp(CorrelationId.setCorrelationIdMiddleware(service).orNotFound)
      .serve
      .compile
      .drain
  }.runSyncUnsafe()
}
