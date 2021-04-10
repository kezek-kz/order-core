package kezek.order.core.scripts

import com.typesafe.config.{Config, ConfigFactory}
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Indexes.{ascending, text}
import org.mongodb.scala.{Document, MongoClient, MongoCollection, MongoDatabase}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object SeedScript {

  val config: Config = ConfigFactory.load()
  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  def createOrderCollectionIndexes()(implicit mongoClient: MongoClient,
                                       executionContext: ExecutionContext): Unit = {
    log.debug(s"createOrderCollectionIndexes() was called")
    val database: MongoDatabase = mongoClient.getDatabase(config.getString("db.mongo.database"))
    val collection: MongoCollection[Document] = database.getCollection(config.getString("db.mongo.collection.order"))

    collection.createIndex(
      ascending("id"),
      IndexOptions().unique(true)
    ).toFuture().onComplete {
      case Success(_) =>
        log.debug("createOrderCollectionIndexes() successfully created unique indexes for id")
      case Failure(exception) =>
        log.error(s"createOrderCollectionIndexes() failed to create unique indexes for id{details: $exception}")
    }

  }


}
