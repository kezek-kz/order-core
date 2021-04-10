package kezek.order.core

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.{Config, ConfigFactory}
import kezek.order.core.api.http.HttpServer
import kezek.order.core.scripts.SeedScript
import kezek.order.core.service.OrderService
import org.mongodb.scala.MongoClient

import scala.concurrent.ExecutionContext

object Main extends App {

  implicit val config: Config = ConfigFactory.load()

  implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](
    Behaviors.empty,
    name = config.getString("akka.actor.system"),
    config
  )

  implicit val mongoClient: MongoClient = MongoClient(config.getString("db.mongo.connection-string"))

  implicit val classicSystem: akka.actor.ActorSystem = system.classicSystem
  implicit val executionContext: ExecutionContext = classicSystem.dispatchers.lookup("akka.dispatchers.main")

  implicit val orderService: OrderService = new OrderService()

  HttpServer().run()

  SeedScript.createOrderCollectionIndexes()

}
