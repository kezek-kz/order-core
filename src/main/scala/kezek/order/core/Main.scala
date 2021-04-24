package kezek.order.core

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.{Config, ConfigFactory}
import kezek.order.core.api.http.HttpServer
import kezek.order.core.domain.Order
import kezek.order.core.scripts.SeedScript
import kezek.order.core.service.{OrderService, OrderStateMachine, OrderStateMachineFactory}
import kezek.order.core.service.OrderStateMachine.Event
import org.mongodb.scala.MongoClient

import scala.concurrent.ExecutionContext

object Main extends App {

  implicit val config: Config = ConfigFactory.load()

  ActorSystem[Nothing](
    Behaviors.setup[Nothing] { context =>
      implicit val mongoClient: MongoClient = MongoClient(config.getString("db.mongo.connection-string"))

      implicit val system: ActorSystem[Nothing] = context.system
      implicit val classicSystem: akka.actor.ActorSystem = context.system.classicSystem
      implicit val executionContext: ExecutionContext = classicSystem.dispatchers.lookup("akka.dispatchers.main")

      implicit val orderStateMachineFactory: ActorRef[(Order,Event)] =
        context.spawn(OrderStateMachineFactory(), "order-state-machine-factory")

      implicit val orderService: OrderService = new OrderService()

      HttpServer().run()

      SeedScript.createOrderCollectionIndexes()
      Behaviors.empty
    },
    name = config.getString("akka.actor.system"),
    config
  )

}
