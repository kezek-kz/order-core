package kezek.order.core.repository.mongo

import akka.Done
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto._
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import kezek.order.core.codec.MainCodec
import kezek.order.core.domain.OrderFilter._
import kezek.order.core.domain.{Order, OrderFilter}
import kezek.order.core.exception.ApiException
import kezek.order.core.repository.OrderRepository
import kezek.order.core.repository.mongo.OrderMongoRepository.{Counter, fromFiltersToBson}
import kezek.order.core.util.{PaginationUtil, SortType}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.model.Updates.{combine, inc, setOnInsert}
import org.mongodb.scala.{Document, MongoClient, MongoCollection, MongoDatabase}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object OrderMongoRepository {

  private def fromFiltersToBson(filters: Seq[OrderFilter]): Bson = {
    if (filters.isEmpty) Document()
    else and(
      filters.map {
        case ByCategoryIdFilter(categoryId) => equal("categories", categoryId)
        case other =>
          throw new RuntimeException(s"Failed to generate bson filter: $other not implemented")
      }: _*
    )
  }

  case class Counter(id: String, count: Long)

}

class OrderMongoRepository()(implicit val mongoClient: MongoClient,
                             implicit val executionContext: ExecutionContext)
  extends OrderRepository with MainCodec with MongoRepository {

  override val sortingFields: Seq[String] = Seq("phoneNumber", "firstName")
  val config: Config = ConfigFactory.load()
  val database: MongoDatabase = mongoClient.getDatabase(config.getString("db.mongo.database"))
  val collection: MongoCollection[Document] = database.getCollection(config.getString("db.mongo.collection.order"))
  val counterCollection: MongoCollection[Document] = database.getCollection(config.getString("db.mongo.collection.counter"))

  override def  incrementCounter(): Future[Unit] = {
    counterCollection.updateOne(
      equal("id", "order"),
      combine(
        setOnInsert("id", "order"),
        inc("count", 1),
      ),
      UpdateOptions().upsert(true)
    ).head().map(_ => ())
  }

  override def getCounter(): Future[Long] = {
    counterCollection
      .find(equal("id", "order"))
      .headOption()
      .map {
        case Some(document) => parse(document.toJson()).toTry.get.hcursor.get[Long]("count").toTry.get
        case None => 0L
      }
  }

  override def create(order: Order): Future[Order] = {
    collection.insertOne(toDocument(order)).head().map(_ => order)
  }

  private def toDocument(order: Order): Document = {
    Document(order.asJson.noSpaces)
  }

  override def update(id: Long, order: Order): Future[Order] = {
    collection.replaceOne(equal("id", id), toDocument(order)).head().map { updateResult =>
      if (updateResult.wasAcknowledged()) {
        order
      } else {
        throw new RuntimeException(s"Failed to replace order with id: $id")
      }
    }
  }

  override def findById(id: Long): Future[Option[Order]] = {
    collection
      .find(equal("id", id))
      .first()
      .headOption()
      .map {
        case Some(document) => Some(fromDocumentToOrder(document))
        case None => None
      }
  }

  private def fromDocumentToOrder(document: Document): Order = {
    parse(document.toJson()).toTry match {
      case Success(json) =>
        json.as[Order].toTry match {
          case Success(order) => order
          case Failure(exception) => throw exception
        }
      case Failure(exception) => throw exception
    }
  }

  override def paginate(filters: Seq[OrderFilter],
                        page: Option[Int],
                        pageSize: Option[Int],
                        sortParams: Map[String, SortType]): Future[Seq[Order]] = {
    val filtersBson = fromFiltersToBson(filters)
    val sortBson = fromSortParamsToBson(sortParams)
    val limit = pageSize.getOrElse(10)
    val offset = PaginationUtil.offset(page = page.getOrElse(1), size = limit)

    collection
      .find(filtersBson)
      .sort(sortBson)
      .skip(offset)
      .limit(limit)
      .toFuture()
      .map(documents => documents map fromDocumentToOrder)
  }

  override def count(filters: Seq[OrderFilter]): Future[Long] = {
    collection.countDocuments(fromFiltersToBson(filters)).head()
  }

  override def delete(id: Long): Future[Done] = {
    collection.deleteOne(equal("id", id)).head().map { deleteResult =>
      if (deleteResult.wasAcknowledged() && deleteResult.getDeletedCount == 1) {
        Done
      } else {
        throw ApiException(StatusCodes.NotFound, "Failed to delete order")
      }
    }
  }
}
