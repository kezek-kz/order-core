package kezek.order.core.service

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import kezek.order.core.domain.Order
import kezek.order.core.service.OrderStateMachine.Event

object OrderStateMachineFactory {

  def apply(): Behavior[(Order, Event)] = {
    Behaviors.setup { context =>

      val stateMachines: collection.mutable.Map[Long, ActorRef[Event]] = collection.mutable.Map.empty

      Behaviors.receiveMessage[(Order, Event)] {
        case (order, event) => {
          stateMachines.get(order.id) match {
            case Some(stateMachine) => stateMachine ! event
            case None =>
              val newStateMachine = context.spawn(OrderStateMachine(order), order.id.toString)
              stateMachines.put(order.id, newStateMachine)
              newStateMachine ! event
          }
          Behaviors.same
        }
      }
    }
  }



}
