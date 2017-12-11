package edu.umass.cs.iesl.watr
package watrcolors
package services

import org.http4s._
import org.http4s.circe._
import _root_.io.circe
import circe._
import circe.syntax._
import circe.literal._
import TypeTags._
import geometry._
import textgrid._
import models._
import tsec.authentication._

trait LabelingServices extends AuthenticatedService { self =>

  // Mounted at /api/v1xx/labeling/..
  val labelingServiceEndpoints = Auth {

    case req @ GET -> Root / "labels" / stableIdStr asAuthed user =>
      val stableId = DocumentID(stableIdStr)
      val docId  = docStore.getDocument(stableId).getOrElse {
        sys.error(s"docId not found for ${stableId}")
      }

      val allDocZones = for {
        labelId <- docStore.getZoneLabelsForDocument(docId)
        zoneId <- docStore.getZonesForDocument(docId, labelId) if labelId.unwrap > 1  // TODO un hardcode this
      } yield {
        val zone = docStore.getZone(zoneId)
        zone.asJson
      }
      val jsonResp = Json.obj(
        ("zones", Json.arr(allDocZones:_*))
      )

      Ok(jsonResp)

    case req @ DELETE -> Root / "label"  asAuthed user =>
      println(s"Got delete label request")

      for {
        deleteReq <- decodeOrErr[DeleteZoneRequest](req.request)
      } yield {
        for { zoneId <- deleteReq.zoneIds } {
          val zone = docStore.getZone(ZoneID(zoneId))
          println(s"Deleting zones ${zone}")
          docStore.deleteZone(ZoneID(zoneId))
        }
      }
      Ok(Json.obj())

    case req @ POST -> Root / "label" / "span"  asAuthed user =>

      val handler = for {
        // gridJson <- req.request.as[Json]
        labeling <- decodeOrErr[LabelSpanReq](req.request)

        _ = {
          // val stableId = DocumentID(labeling.stableId)
          val textgrid = TextGrid.fromJson(labeling.gridJson)
          val gridBounds = textgrid.pageBounds()

          println(s"labeling span ${gridBounds}")
          docStore.labelRegions(labeling.labelChoice, gridBounds)
            .foreach{ zoneId =>
              println(s"setting zone ${zoneId} text to  ${textgrid}")
              docStore.setZoneText(zoneId, textgrid)
            }
        }
        resp <- Ok(Json.obj())
      } yield resp

      orErrorJson(handler)

    case req @ POST -> Root / "label" / "region" asAuthed user =>
      println(s"${req}")

      val handler = for {
        labeling <- decodeOrErr[LabelingReqForm](req.request)
        _ = {

          val stableId = DocumentID(labeling.stableId)
          val docId  = docStore.getDocument(stableId).getOrElse {
            sys.error(s"docId not found for ${stableId}")
          }

          val regions = labeling.selection.targets.map { ltarget =>
            val pageNum = PageNum(ltarget.page)

            val (l, t, w, h) = (
              ltarget.bbox(0), ltarget.bbox(1), ltarget.bbox(2), ltarget.bbox(3)
            )
            val bbox = LTBounds.IntReps(l, t, w, h)

            val pageRegions = for {
              pageId    <- docStore.getPage(docId, pageNum).toSeq
            } yield {
              val regionId = docStore.addTargetRegion(pageId, bbox)
              docStore.getTargetRegion(regionId)
            }
            pageRegions
          }

          docStore.labelRegions(labeling.labelChoice, regions.flatten)
        }
        resp <- Ok(Json.obj())
      } yield resp

      orErrorJson(handler)
  }
}
