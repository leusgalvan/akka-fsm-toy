package com.example

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, SnapshotOffer}

import scala.collection.immutable.SortedMap

object Bank {
  case class SnapshotData(nextId: Int, accountsById: SortedMap[AccountId, Account])
  case class Account(balance: Double) {
    def deposit(amount: Double) = Account(balance + amount)
    def withdraw(amount: Double) = Account(balance - amount)
  }

  type AccountId = Int

  trait BankCommand
  case class AddAccount(account: Account) extends BankCommand
  case class Deposit(account: AccountId, amount: Double) extends BankCommand
  case class Withdraw(account: AccountId, amount: Double) extends BankCommand
  case object PrintState extends BankCommand

  trait BankEvent
  case class AccountAdded(id: AccountId, account: Account) extends BankEvent
  case class Deposited(id: AccountId, account: Account, amount: Double) extends BankEvent
  case class Withdrawn(id: AccountId, account: Account, amount: Double) extends BankEvent
}

class Bank extends PersistentActor with ActorLogging {
  import Bank._

  override def persistenceId: String = "bank"
  private var _lastId = 1
  private var _accountsById = SortedMap.empty[AccountId, Account]
  private val snapshotInterval = 3
  private var eventSeqNr = 0

  def saveSnapshotIfNeeded() = {
    if(eventSeqNr % snapshotInterval == 0 && eventSeqNr != 0) {
      val snapshot = SnapshotData(_lastId, _accountsById)
      log.info("Saving snapshot: {}", snapshot)
      saveSnapshot(snapshot)
    }
    eventSeqNr += 1
  }

  def updateState(evt: BankEvent) = evt match {
    case AccountAdded(id, account) =>
      log.info("Account added: {} {}", id, account)
      _accountsById += (id -> account)
      _lastId += 1

    case Deposited(id, account, amount) =>
      _accountsById = _accountsById.updated(id, account.deposit(amount))

    case Withdrawn(id, account, amount) =>
      _accountsById = _accountsById.updated(id , account.withdraw(amount))
  }

  override def receiveRecover: Receive = {
    case evt: BankEvent =>
      log.info("Replaying event: {}", evt)
      updateState(evt)

    case snapshot@SnapshotOffer(_, SnapshotData(lastId, accountsById)) =>
      log.info("Replaying snapshot: {}", snapshot)
      _lastId = lastId
      _accountsById = accountsById
  }

  override def receiveCommand: Receive = {
    case AddAccount(account: Account) =>
      log.info("Adding account: {}", account)
      persist(AccountAdded(_lastId, account)) { accountAdded =>
        updateState(accountAdded)
        context.system.eventStream.publish(accountAdded)
        saveSnapshotIfNeeded()
      }

    case Deposit(accountId, amount: Double) =>
      log.info("Depositing into account: {} {}", accountId, amount)
      persist(Deposited(accountId, _accountsById(accountId), amount)) { deposited =>
        updateState(deposited)
        context.system.eventStream.publish(deposited)
        saveSnapshotIfNeeded()
      }

    case Withdraw(accountId, amount: Double) =>
      log.info("Withdrawing from account: {} {}", accountId, amount)
      persist(Withdrawn(accountId, _accountsById(accountId), amount)) { withdrawn =>
        updateState(withdrawn)
        context.system.eventStream.publish(withdrawn)
        saveSnapshotIfNeeded()
      }

    case PrintState =>
      log.info("Current state: {}", _accountsById)
  }
}
