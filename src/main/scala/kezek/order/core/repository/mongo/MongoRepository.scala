package kezek.order.core.repository.mongo

import kezek.order.core.util.{ASC, SortType}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Sorts.{ascending, descending, orderBy}

object MongoRepository {
  final val DUPLICATED_KEY_ERROR_CODE = 11000
}

trait MongoRepository {

  def fromSortParamsToBson(sortParams: Map[String, SortType]): Bson = {
    orderBy(
      sortParams
        .filter(sortParam => sortingFields.contains(sortParam._1))
        .map(sortParam => if (sortParam._2 == ASC) ascending(sortParam._1) else descending(sortParam._1)).toSeq: _*
    )
  }

  def sortingFields: Seq[String] = Seq.empty
}

