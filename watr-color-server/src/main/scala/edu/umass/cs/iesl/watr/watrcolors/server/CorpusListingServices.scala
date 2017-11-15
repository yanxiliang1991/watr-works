package edu.umass.cs.iesl.watr
package watrcolors
package server

// import corpora._

import org.http4s._
import org.http4s.dsl._
import _root_.io.circe
import circe._
import circe.syntax._
import circe.literal._
// import watrmarks.Label

trait CorpusListingServices extends ServiceCommons with WorkflowCodecs { self =>
  // Mounted at /api/v1xx/corpus/..


  val corpusListingEndpoints = HttpService {
    // case req @ GET -> Root / "overview" => ???
    case req @ GET -> Root / "entries" :? StartQP(start) +& LengthQP(len) =>

      val skip = start.getOrElse(0)
      val get = len.getOrElse(100)

      val docCount = docStore.getDocumentCount()

      val entries = (for {
        (stableId, i) <- docStore.getDocuments(get, skip).zipWithIndex
      } yield {

        val docLabels = (for {
          docId <- docStore.getDocument(stableId).toList
        } yield for {
          labelId <- docStore.getZoneLabelsForDocument(docId)
        } yield {
          docStore.getLabel(labelId).asJson
        }).asJson

        Json.obj(
          ("num", Json.fromInt(skip+i)),
          ("stableId", Json.fromString(stableId.unwrap)),
          ("labels", docLabels)
        )

      }).asJson

      okJson(
        Json.obj(
          ("corpusSize", Json.fromInt(docCount)),
          ("entries", entries),
          ("start", Json.fromInt(skip))
        )
      )
  }
}