package com.example

import akka.actor.{Actor, ActorPath, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import com.example.HttpService.{Get, GetResult, SetHttpClient}
import com.example.PageTitleFetcher._
import org.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Future

class PageTitleFetcherSpec
  extends TestKit(ActorSystem("Test"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "PageTitleFetcher" should {
    "return the correct title when the http service is working" in {
      val uri = "http://www.google.com"
      val html =
        """
          |<html>
          | <head>
          |   <title>Google</title>
          | </head>
          | <body>
          |   Some text
          | </body>
          |</html>
        """.stripMargin
      val httpClient = mock[HttpClient]
      when(httpClient.get(uri)).thenReturn(Future.successful(html))
      val httpService = system.actorOf(Props[HttpService], "http-service-ok")
      httpService ! SetHttpClient(httpClient)
      val fetcher = system.actorOf(Props[PageTitleFetcher], "fetcher-ok")
      fetcher ! SetHttpService(httpService.path)

      fetcher ! GetPageTitle(uri)
      expectMsg(PageTitleResponse(Some("Google")))
    }

    "return an error when the http service is not working" in {
      val uri = "http://www.google.com"
      val reason = new Exception("Something went wrong")
      val httpClient = mock[HttpClient]
      when(httpClient.get(uri)).thenReturn(Future.failed(reason))
      val httpService = system.actorOf(Props[HttpService], "http-service-fail")
      httpService ! SetHttpClient(httpClient)
      val fetcher = system.actorOf(Props[PageTitleFetcher], "fetcher-fail")
      fetcher ! SetHttpService(httpService.path)
      fetcher ! GetPageTitle(uri)
      expectMsg(PageTitleError(reason))
    }
  }

  "return the correct title when the http actor does not respond the first few times but then succeeds" in {
    val uri = "http://www.google.com"
    val html =
      """
        |<html>
        | <head>
        |   <title>Google</title>
        | </head>
        | <body>
        |   Some text
        | </body>
        |</html>
      """.stripMargin
    val httpClient = mock[HttpClient]
    when(httpClient.get(uri)).thenReturn(Future.successful(html))
    val innerHttpService = system.actorOf(Props[HttpService], "http-service-retry")
    innerHttpService ! SetHttpClient(httpClient)
    val httpService = TestActorRef(new Actor {
      var failedTimes = 0
      def receive: Receive = {
        case msg: Get =>
          if(failedTimes < 5) {
            println("Failing")
            failedTimes += 1
          }
          else {
            innerHttpService forward msg
          }
      }
    })
    val fetcher = system.actorOf(Props[PageTitleFetcher], "fetcher-retry")
    fetcher ! SetHttpService(httpService.path)

    fetcher ! GetPageTitle(uri)
    expectMsg(PageTitleResponse(Some("Google")))
  }
}
