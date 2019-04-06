package com.example

import akka.actor.FSM.Event
import akka.actor.{ActorSystem, Props}
import Room._

object App {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("test")
    val room = system.actorOf(Props[Room], "room")
    room ! Start
    //room ! DeleteConnection(1)
    room ! AddConnection(10)
    Thread.sleep(1500)
    room ! AddConnection(5)
    room ! AddConnection(2)
    room ! GoToFakeState
    room ! DeleteConnection(2)
    room ! PrintConnections
    room ! Stop
    room ! AddConnection(3)
    Thread.sleep(4000)
    system.terminate()
  }
}
