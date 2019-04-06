package com.example

import java.util.concurrent.TimeUnit

import akka.actor.{FSM, LoggingFSM}
import com.example.Room._

import scala.concurrent.duration.Duration

object Room {
  trait State
  case object Inactive extends State
  case object Active extends State
  case object FakeState extends State

  trait RoomData
  case object Uninitialized extends RoomData
  case class Connections(connections: Set[Int] = Set.empty[Int]) extends RoomData

  trait RoomAction
  case object Start extends RoomAction
  case object Stop extends RoomAction
  case class AddConnection(id: Int) extends RoomAction
  case class DeleteConnection(id: Int) extends RoomAction
  case object PrintConnections extends RoomAction
  case object GoToFakeState extends RoomAction
}

class Room extends FSM[State, RoomData] {
//  override def postStop(): Unit = {
//    super.postStop()
//    log.info("Stopping...")
//  }
//
//  override def preRestart(reason: scala.Throwable, message: _root_.scala.Option[Any]): Unit = {
//    log.info("Restarting...")
//  }

  startWith(Inactive, Uninitialized)

  when(Inactive) {
    case e@Event(Start, _) =>
      log.info("Receiving event: {}", e)
      goto(Active)
  }

  when(Active, stateTimeout = Duration(1, TimeUnit.SECONDS))(transform({
    case e@Event(Stop, _) =>
      log.info("Receiving event: {}", e)
      goto(Inactive)
    case e@Event(AddConnection(id), Uninitialized) =>
      log.info("Receiving event: {}", e)
      stay() using Connections(Set(id))
    case e@Event(DeleteConnection(id), Uninitialized) =>
      log.info("Receiving event: {}", e)
      log.error("Cannot delete inexisting connection with id {}", id)
      stop(FSM.Failure(new Exception("Deleting connection in uninitialized state")))
    case e@Event(AddConnection(id), Connections(connections)) =>
      log.info("Receiving event: {}", e)
      stay() using Connections(connections + id)
    case e@Event(DeleteConnection(id), Connections(connections)) =>
      log.info("Receiving event: {}", e)
      if(connections contains id) {
        stay() using Connections(connections - id)
      } else {
        log.error("Cannot delete inexisting connection with id {}", id)
        stop(FSM.Normal)
      }
    case e@Event(PrintConnections, connections) =>
      log.info("Receiving event: {}", e)
      log.info("Connections: {}", connections)
      stay()
    case e@Event(StateTimeout, _) =>
      log.info("Receiving event: {}", e)
      log.info("Send a message, I'm bored!")
      stay()

  }).using({
    case FSM.State(_, Connections(connections), _, _, _) if connections.size > 10 =>
      log.info("Going to inactive because of number of connections exceeded: {}", connections)
      goto(Inactive) using Connections(connections)
  }))

//  when(FakeState)(FSM.NullFunction)

  whenUnhandled {
    case Event(AddConnection(id), _) =>
      log.info("Impossible to add connection with id {} because room is Inactive", id)
      stay()
    case Event(DeleteConnection(id), _) =>
      log.info("No need to delete connection with id {} because room is Inactive", id)
      stay()
    case Event(GoToFakeState, _) =>
      log.info("Going into fake state")
      goto(FakeState)
  }

  onTransition {
    case Inactive -> Active => log.info("Activating")
    case Active -> Inactive => log.info("Deactivating")
  }

  onTermination {
    case StopEvent(FSM.Normal, currentState, stateData) =>
      log.info("Shutting down by normal causes with state {} and data {}", currentState, stateData)
    case StopEvent(FSM.Shutdown, currentState, stateData) =>
      log.info("Shutting down by shutdown with state {} and data {} and reason {}", currentState, stateData)
    case StopEvent(FSM.Failure(reason), currentState, stateData) =>
      log.info("Shutting down by failure with state {} and data {} and reason {}", currentState, stateData, reason)
  }

  initialize()
}
