package kezek.order.core.service

import akka.Done
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.StatusReply
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.scalaland.chimney.dsl.TransformerOps
import kezek.order.core.codec.MainCodec
import kezek.order.core.domain.OrderFilter._
import kezek.order.core.domain._
import kezek.order.core.domain.dto.{CreateOrderDTO, OrderListWithTotalDTO}
import kezek.order.core.exception.ApiException
import kezek.order.core.repository.OrderRepository
import kezek.order.core.repository.mongo.MongoRepository.DUPLICATED_KEY_ERROR_CODE
import kezek.order.core.repository.mongo.OrderMongoRepository
import kezek.order.core.service.OrderStateMachine.Events._
import kezek.order.core.service.OrderStateMachine.States.CREATED
import kezek.order.core.service.OrderStateMachine._
import kezek.order.core.util.SortType
import org.joda.time.DateTime
import org.mongodb.scala.{MongoClient, MongoWriteException}
import org.slf4j.{Logger, LoggerFactory}

import java.time.Duration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object OrderService extends MainCodec {

  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  def generateFilters(categoryId: Option[String] = None,
                      title: Option[String] = None,
                      description: Option[String] = None): Seq[OrderFilter] = {
    var filters: Seq[OrderFilter] = Seq.empty
    if (categoryId.isDefined) filters = filters :+ ByCategoryIdFilter(categoryId.get)
    if (title.isDefined) filters = filters :+ ByTitleFilter(title.get)
    if (description.isDefined) filters = filters :+ ByDescriptionFilter(description.get)
    filters
  }


}

class OrderService()(implicit val mongoClient: MongoClient,
                     implicit val orderStateMachineFactory: ActorRef[(Order, Event)],
                     implicit val executionContext: ExecutionContext,
                     implicit val system: ActorSystem[_]) extends MainCodec {

  val config: Config = ConfigFactory.load()
  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)
  val orderRepository: OrderRepository = new OrderMongoRepository()

  implicit private val timeout: Timeout = Timeout.create(Duration.ofSeconds(30))

  def paginate(filters: Seq[OrderFilter],
               page: Option[Int],
               pageSize: Option[Int],
               sortParams: Map[String, SortType]): Future[OrderListWithTotalDTO] = {
    log.debug(s"paginate() was called {filters: $filters, page: $page, pageSize: $pageSize, sortParams: $sortParams}")
    (for (
      orders <- orderRepository.paginate(filters, page, pageSize, sortParams);
      count <- orderRepository.count(filters)
    ) yield OrderListWithTotalDTO(
      collection = orders,
      total = count
    )).recover { exception =>
      log.error(s"paginate() failed to paginate orders {exception: $exception, filters: $filters, page: $page, pageSize: $pageSize, sortParams: $sortParams}")
      throw new RuntimeException(s"Failed to paginate orders: $exception")
    }
  }

  def create(createOrderDTO: CreateOrderDTO): Future[Order] = {
    log.debug(s"create() was called {createOrderDTO: ${createOrderDTO.asJson.noSpaces}}")
    val dateTimeNow = DateTime.now()
    //    val initState = Created(CREATED, dateTimeNow)
    (for (
      _ <- orderRepository.incrementCounter();
      count <- orderRepository.getCounter();
      order <- Future {
        createOrderDTO.into[Order]
          .withFieldConst(_.id, count)
          .withFieldConst(_.status, CREATED)
          .withFieldConst(_.createdAt, dateTimeNow)
          .withFieldConst(_.updatedAt, dateTimeNow)
          .enableOptionDefaultsToNone
          .transform
      };
      _ <- orderRepository.create(order).recover {
        case ex: MongoWriteException if ex.getCode == DUPLICATED_KEY_ERROR_CODE =>
          log.error(s"create() failed to create order due to duplicate key {ex: $ex, order: ${order.asJson.noSpaces}")
          throw ApiException(StatusCodes.Conflict, s"Failed to create, order with id: ${order.id} already exists")
        case ex: Exception =>
          log.error(s"create() failed to create order {ex: $ex, order: ${order.asJson.noSpaces}}")
          throw ApiException(StatusCodes.ServiceUnavailable, ex.getMessage)
      }
    ) yield order).recover {
      case ex: ApiException => throw ex
      case exception: Exception =>
        log.error(s"create() failed to create order {exception: $exception, createOrderDTO: ${createOrderDTO.asJson.noSpaces}}")
        throw ApiException(StatusCodes.ServiceUnavailable, s"Failed to create order: $exception")
    }
  }

  def delete(id: Long): Future[Done] = {
    log.debug(s"delete() was called {id: $id}")
    orderRepository.delete(id)
  }

  def handleEvent(id: Long, event: String, paymentDetails: Json = null, cancelReason: String = null): Future[Order] = {
    log.debug(s"handleEvent() was called {id $id, event: $event}")

    val result = Promise[Order]

    getById(id).flatMap { order =>
      event match {
        case Events.CANCEL if cancelReason != null =>
          orderStateMachineFactory.askWithStatus[Order](replyTo => (order, Cancel(replyTo, cancelReason)))
        case Events.CANCEL if cancelReason == null =>
          throw ApiException(StatusCodes.BadRequest, "Invalid data, cancel reason not provided")
        case Events.COOK =>
          orderStateMachineFactory.askWithStatus[Order](replyTo => (order, Cook(replyTo)))
        case Events.COOKED =>
          orderStateMachineFactory.askWithStatus[Order](replyTo => (order, Cooked(replyTo)))
        case Events.CHECKOUT if paymentDetails != null =>
          orderStateMachineFactory.askWithStatus[Order](replyTo => (order, Checkout(replyTo, paymentDetails)))
        case Events.CHECKOUT if paymentDetails == null =>
          throw ApiException(StatusCodes.BadRequest, "Invalid data, payment details not provided")
        case Events.TAKEN =>
          orderStateMachineFactory.askWithStatus[Order](replyTo => (order, Taken(replyTo)))
        case _ => throw ApiException(StatusCodes.BadRequest, s"No such event $event")
      }
    } onComplete {
      case Success(order) =>
        update(id, order).onComplete {
          case Success(_) => result.success(order)
          case Failure(exception) => result.failure(exception)
        }
      case Failure(StatusReply.ErrorMessage(message)) => result.failure(ApiException(StatusCodes.BadRequest, message))
      case Failure(exception) => result.failure(exception)
    }

    result.future
  }

  def update(id: Long, order: Order): Future[Order] = {
    log.debug(s"update() was called {id: $id, updateOrderDTO: $order}")
    orderRepository.update(id, order)
  }

  def getById(id: Long): Future[Order] = {
    log.debug(s"getById() was called {id: $id}")
    orderRepository.findById(id).map {
      case Some(order) => order
      case None =>
        log.error(s"getById() failed to find order {id: $id}")
        throw ApiException(StatusCodes.NotFound, s"Failed to find order with id: $id")
    }
  }
}

