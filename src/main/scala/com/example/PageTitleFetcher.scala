package com.example

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorPath, ActorSystem}
import akka.persistence.{AtLeastOnceDelivery, PersistentActor}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.example.HttpService.{Get, GetError, GetResult, SetHttpClient}
import com.example.PageTitleFetcher._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object PageTitleFetcher{
  case class GetPageTitle(uri: String)
  case class PageTitleResponse(maybeTitle: Option[String])
  case class PageTitleError(reason: Throwable)
  case class SetHttpService(service: ActorPath)

  trait PageEvent
  case class GetHtmlSent(uri: String, replyTo: ActorPath) extends PageEvent
  case class GetHtmlReceived(deliveryId: Long, html: String) extends PageEvent
  case class GetPageTitleError(deliveryId: Long, reason: Throwable) extends PageEvent
  case class HttpServiceSet(service: ActorPath) extends PageEvent
}

class PageTitleFetcher extends PersistentActor with AtLeastOnceDelivery with ActorLogging {
  private var _httpService: ActorPath = _

  override def persistenceId: String = s"page-title-fetcher+${self.path}"

  override def redeliverInterval: FiniteDuration = FiniteDuration(2, TimeUnit.SECONDS)

  private def extractTitle(html: String): Option[String] = {
    val regex = "<title>(.*?)</title>".r
    regex.findFirstMatchIn(html).map(_.group(1))
  }

  private def updateState(event: PageEvent): Unit = event match {
    case GetHtmlSent(uri, replyTo) =>
      deliver(_httpService) { deliveryId =>
        val msg = Get(deliveryId, uri, replyTo)
        log.debug("Sending {}", msg)
        msg
      }
    case GetHtmlReceived(deliveryId, _) =>
      log.debug("Confirming delivery received: {}", deliveryId)
      confirmDelivery(deliveryId)
    case GetPageTitleError(deliveryId, _) =>
      log.debug("Confirming delivery error: {}", deliveryId)
      confirmDelivery(deliveryId)
    case HttpServiceSet(service) =>
      _httpService = service
  }

  override def receiveCommand: Receive = {
    case GetPageTitle(uri) =>
      persist(GetHtmlSent(uri, sender.path))(updateState)
    case GetResult(deliveryId, html, replyTo) =>
      val replyToActor = context.actorSelection(replyTo)
      persist(GetHtmlReceived(deliveryId, html)) { event =>
        updateState(event)
        replyToActor ! PageTitleResponse(extractTitle(event.html))
      }
    case GetError(deliveryId, reason, replyTo) =>
      val replyToActor = context.actorSelection(replyTo)
      persist(GetPageTitleError(deliveryId, reason)) { event =>
        updateState(event)
        replyToActor ! PageTitleError(event.reason)
      }
    case SetHttpService(service) =>
      persist(HttpServiceSet(service))(updateState)
  }

  override def receiveRecover: Receive = {
    case e: PageEvent =>
      updateState(e)
  }
}

object HttpService {
  case class Get(deliveryId: Long, uri: String, replyTo: ActorPath)
  case class GetResult(deliveryId: Long, html: String, replyTo: ActorPath)
  case class GetError(deliveryId: Long, reason: Throwable, replyTo: ActorPath)
  case class SetHttpClient(httpClient: HttpClient)
}

class HttpService extends Actor {
  implicit val ec = context.dispatcher
  implicit val as = context.system
  var _httpClient: HttpClient = _

  override def receive: Receive = {
    case Get(deliveryId, uri, replyTo) =>
      val currentSender = sender()
      _httpClient.get(uri) onComplete {
        case Success(html) =>
          currentSender ! GetResult(deliveryId, html, replyTo)
        case Failure(reason) =>
          currentSender ! GetError(deliveryId, reason, replyTo)
      }
    case SetHttpClient(httpClient) =>
      _httpClient = httpClient
  }
}

trait HttpClient {
  def get(uri: String): Future[String]
}

case class HttpClientImpl()(implicit actorSystem: ActorSystem) extends HttpClient {
  implicit val ec = actorSystem.dispatcher
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(actorSystem))
  def get(uri: String): Future[String] = {
    for {
      response <- Http().singleRequest(HttpRequest(uri = uri))
      byteString <- response.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
    } yield byteString.utf8String
  }
}