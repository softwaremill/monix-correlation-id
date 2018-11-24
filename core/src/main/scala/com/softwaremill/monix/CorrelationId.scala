package com.softwaremill.monix

import cats.data.Kleisli
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import com.softwaremill.monix.CorrelationId.CorrelationIdHeader
import com.softwaremill.sttp
import com.softwaremill.sttp.{MonadError, Response, SttpBackend}
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.misc.Local
import org.http4s.util.CaseInsensitiveString
import org.http4s.{HttpRoutes, Request}
import org.slf4j.MDC

import scala.util.Random

object CorrelationId extends StrictLogging {
  private val localCid = new Local[Option[String]](() => {
    None
  })

  def apply(): Option[String] = localCid()

  val CorrelationIdHeader = "X-Correlation-ID"

  def setCorrelationIdMiddleware(service: HttpRoutes[Task]): HttpRoutes[Task] = Kleisli { req: Request[Task] =>
    val cid = req.headers.get(CaseInsensitiveString(CorrelationIdHeader)) match {
      case None => newCorrelationId()
      case Some(cidHeader) => cidHeader.value
    }

    localCid.update(Some(cid)) // TODO: not cleared
    logger.info("Starting request")
    service(req)
  }

  private val random = new Random()

  private def newCorrelationId(): String = {
    def randomUpperCaseChar() = (random.nextInt(91 - 65) + 65).toChar

    def segment = (1 to 3).map(_ => randomUpperCaseChar()).mkString

    s"$segment-$segment-$segment"
  }

  def withNew[T](th: Task[T]): Task[T] = {
    Task(localCid.update(Some(newCorrelationId()))).flatMap { _ =>
      th.doOnFinish { _ =>
        Task(localCid.clear())
      }
    }
  }
}

class SetCorrelationIdBackend(delegate: SttpBackend[Task, Nothing]) extends SttpBackend[Task, Nothing] {
  override def send[T](request: sttp.Request[T, Nothing]): Task[Response[T]] = {
    // suspending the calculation of the correlation id until the request send is evaluated
    Task {
      CorrelationId() match {
        case Some(cid) => request.header(CorrelationIdHeader, cid)
        case None => request
      }
    }.flatMap(delegate.send)
  }

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[Task] = delegate.responseMonad
}

class SetCorrelationIdInMDCFilter extends Filter[ILoggingEvent] {
  override def decide(event: ILoggingEvent): FilterReply = {
    CorrelationId().foreach(MDC.put("cid", _)) // TODO: not cleared
    FilterReply.NEUTRAL
  }
}