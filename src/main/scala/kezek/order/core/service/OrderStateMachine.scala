package kezek.order.core.service

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import io.circe.Json
import kezek.order.core.domain.Order
import kezek.order.core.domain.dto.UpdateOrderDTO
import kezek.order.core.service.OrderStateMachine.Events._
import kezek.order.core.service.OrderStateMachine.States._

import scala.concurrent.{ExecutionContext, Future}

object OrderStateMachine {

  object States {

    final val CREATED = "СОЗДАН"
    final val CANCELED = "ОТМЕНЕН"
    final val PAID = "ОПЛАЧЕН"
    final val COOKING = "ГОТОВИТЬСЯ"
    final val READY = "ГОТОВО"
    final val COMPLETED = "ЗАВЕРШЕН"

  }

  sealed trait Event {
    val replyTo: ActorRef[StatusReply[Order]]
  }

  object Events {

    final val CANCEL = "cancel"
    final val COOK = "cook"
    final val COOKED = "cooked"
    final val CHECKOUT = "checkout"
    final val TAKEN = "taken"

    case class Checkout(replyTo: ActorRef[StatusReply[Order]], paymentDetails: Json) extends Event

    case class Cancel(replyTo: ActorRef[StatusReply[Order]], reason: String) extends Event

    case class Cook(replyTo: ActorRef[StatusReply[Order]]) extends Event

    case class Cooked(replyTo: ActorRef[StatusReply[Order]]) extends Event

    case class Taken(replyTo: ActorRef[StatusReply[Order]]) extends Event

  }



  def apply(order: Order): Behavior[Event] = {

    def created(): Behavior[Event] = {
      Behaviors.receiveMessage {
        case event: Checkout =>
          event.replyTo ! StatusReply.Success(order.copy(status = PAID, paymentDetails = Some(event.paymentDetails)))
          paid()
        case event: Cancel =>
          event.replyTo ! StatusReply.Success(order.copy(status = CANCELED, cancelReason = Some(event.reason)))
          canceled()
        case event =>
          event.replyTo ! StatusReply.Error(s"There is no transition from CREATED state for event ${event.getClass.getSimpleName}")
          Behaviors.same
      }
    }

    def paid(): Behavior[Event] = {
      Behaviors.receiveMessage {
        case event: Cancel =>
          event.replyTo ! StatusReply.Success(order.copy(status = CANCELED, cancelReason = Some(event.reason)))
          canceled()
        case event: Cook =>
          event.replyTo ! StatusReply.Success(order.copy(status = COOKING))
          cooking()
        case event =>
          event.replyTo ! StatusReply.Error(s"There is no transition from PAID state for event ${event.getClass.getSimpleName}")
          Behaviors.same
      }
    }

    def canceled(): Behavior[Event] = {
      Behaviors.receiveMessage { event =>
        StatusReply.Error(s"There is no transition from COOKING state for event ${event.getClass.getSimpleName}")
        Behaviors.same
      }
    }

    def cooking(): Behavior[Event] = {
      Behaviors.receiveMessage {
        case event: Cooked =>
          event.replyTo ! StatusReply.Success(order.copy(status = READY))
          ready()
        case event =>
          event.replyTo ! StatusReply.Error(s"There is no transition from COOKING state for event ${event.getClass.getSimpleName}")
          Behaviors.same
      }
    }

    def ready(): Behavior[Event] = {
      Behaviors.receiveMessage {
        case event: Taken =>
          event.replyTo ! StatusReply.Success(order.copy(status = COMPLETED))
          completed()
        case event =>
          event.replyTo ! StatusReply.Error(s"There is no transition from READY state for event ${event.getClass.getSimpleName}")
          Behaviors.same
      }
    }

    def completed(): Behavior[Event] = {
      Behaviors.receiveMessage { event =>
        event.replyTo ! StatusReply.Error(s"There is no transition from COMPLETED state for event ${event.getClass.getSimpleName}")
        Behaviors.same
      }
    }

    order.status match {
      case CREATED => created()
      case PAID => paid()
      case CANCELED => canceled()
      case COOKING => cooking()
      case READY => ready()
      case COMPLETED => completed()
    }
  }

}

