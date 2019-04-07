package com.example

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.example.HttpService.SetHttpClient
import com.example.PageTitleFetcher.{GetPageTitle, PageTitleError, PageTitleResponse, SetHttpService}
//import com.example.Bank._
import com.example.AsyncBank._
import akka.pattern._
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

object App {
  class AccountCreationTracker(bank: ActorRef) extends Actor with ActorLogging {
    var nonActorSender: ActorRef = _

    override def preStart(): Unit = {
      context.system.eventStream.subscribe(self, classOf[AccountAdded])
    }

    override def postStop(): Unit = {
      context.system.eventStream.unsubscribe(self)
    }

    override def receive: Receive = {
      case msg: AddAccount =>
        nonActorSender = sender()
        bank ! msg

      case AccountAdded(accountId, _) =>
        nonActorSender ! accountId
    }
  }

  case class SetFetcher(actor: ActorRef)
  class FetchTracker extends Actor with ActorLogging {
    var _fetcher: ActorRef = _

    override def receive: Receive = {
      case SetFetcher(actor) =>
        _fetcher = actor
      case msg: GetPageTitle =>
        _fetcher ! msg
      case PageTitleResponse(maybeTitle) =>
        log.info("Title received: {}", maybeTitle.getOrElse("No title found"))
      case PageTitleError(reason) =>
        log.info("Title fetch failed: {}", reason)
    }
  }

  def main(args: Array[String]): Unit = {
    implicit val timeout: Timeout = Timeout(5, TimeUnit.MINUTES)
    implicit val system = ActorSystem("test")
    implicit val ec: ExecutionContext = system.dispatcher

//    val bank = system.actorOf(Props[Bank], "bank")
//    val accountCreationTracker = system.actorOf(
//      Props(classOf[AccountCreationTracker], bank),
//      "accountCreationTracker"
//    )
//    (accountCreationTracker ? AddAccount(Account(1000))).mapTo[AccountId].map { accountId =>
//      bank ! Deposit(accountId, 500)
//      bank ! Withdraw(accountId, 600)
//      bank ! Deposit(accountId, 3500)
//      bank ! Withdraw(accountId, 200)
//      bank ! Deposit(accountId, 5000)
//      bank ! Withdraw(accountId, 690)
//      bank ! Deposit(accountId, 510)
//      bank ! Withdraw(accountId, 620)
//      bank ! PrintState
//    }
//
//    val asyncBank = system.actorOf(Props[AsyncBank], "asyncBank")
//    val asyncAccountCreationTracker = system.actorOf(
//      Props(classOf[AccountCreationTracker], asyncBank),
//      "asyncAccountCreationTracker"
//    )
//    (asyncAccountCreationTracker ? AddAccount(Account(1000))).mapTo[AccountId].map { accountId =>
//      for(_ <- 1 to 1000) {
//        asyncBank ! Deposit(accountId, 500)
//        asyncBank ! Withdraw(accountId, 600)
//        asyncBank ! Deposit(accountId, 3500)
//        asyncBank ! Withdraw(accountId, 200)
//        asyncBank ! Deposit(accountId, 5000)
//        asyncBank ! Withdraw(accountId, 690)
//        asyncBank ! Deposit(accountId, 510)
//        asyncBank ! Withdraw(accountId, 620)
//      }
//      asyncBank ! PrintState
//    }

    val httpClient = HttpClientImpl()
    val httpService = system.actorOf(Props[HttpService], "http-service")
    httpService ! SetHttpClient(httpClient)
    val fetcher = system.actorOf(Props[PageTitleFetcher], "page-title-fetcher")
    fetcher ! SetHttpService(httpService.path)
    val fetchTracker = system.actorOf(Props[FetchTracker], "fetch-tracker")
    fetchTracker ! SetFetcher(fetcher)
    fetchTracker ! GetPageTitle("http://www.google.com")
    fetchTracker ! GetPageTitle("http://www.funtrivia.com")

    Thread.sleep(3000)
    system.terminate()
  }
}
