package edu.umass.cs.iesl.watr
package watrcolors

sealed trait GeometricFigure
sealed trait Area
sealed trait SharedJs

object GeometricFigure {

  case class LTBounds(
    left: Double,
    top: Double,
    width: Double,
    height: Double
  ) extends GeometricFigure with Area with SharedJs

  case class LBBounds(
    left: Double,
    bottom: Double,
    width: Double,
    height: Double
  ) extends GeometricFigure with Area with SharedJs


  case class Point(
    x: Double, y: Double
  ) extends GeometricFigure with SharedJs

  case class Line(
    p1: Point, p2: Point
  ) extends GeometricFigure with SharedJs

}

import GeometricFigure._

case class PageGeometry(
  id: Int, //@@PageID,
  bounds: LTBounds
) extends SharedJs

case class TargetRegion(
  id: Int,     // @@RegionID,
  target: Int, // @@PageID,
  bbox: LTBounds
) extends SharedJs

case class Zone(
  id: Int, // @@ZoneID,
  regions: Seq[TargetRegion]
) extends SharedJs

case class Component(
  id: Int, // @@ComponentID,
  targetRegion: TargetRegion
) extends SharedJs

case class Label(
  ns: String,
  key: String,
  value: Option[String]=None
) extends SharedJs

sealed trait TraceLog

object TraceLog {

  case object Noop                                   extends TraceLog
  case class SetPageGeometries(b: Seq[PageGeometry]) extends TraceLog
  case class Show(s: Seq[TargetRegion])              extends TraceLog
  case class ShowZone(s: Zone)                       extends TraceLog
  case class ShowComponent(s: Component)                       extends TraceLog
  case class ShowLabel(l:Label)                      extends TraceLog
  case class ShowVDiff(d1: Double, d2: Double)       extends TraceLog
  case class FocusOn(s: GeometricFigure)             extends TraceLog
  case class HRuler(s: Double)                       extends TraceLog
  case class VRuler(s: Double)                       extends TraceLog
  case class Message(s: String)                      extends TraceLog
  case class All(ts: Seq[TraceLog])                  extends TraceLog
  case class Link(ts: Seq[TraceLog])                 extends TraceLog

}



trait ShapeDataTypePicklers {
  import boopickle.DefaultBasic._

  implicit val pGeometricFigure = compositePickler[GeometricFigure]
  implicit val pLTBounds = PicklerGenerator.generatePickler[LTBounds]
  implicit val pLBBounds = PicklerGenerator.generatePickler[LBBounds]
  implicit val pPoint= PicklerGenerator.generatePickler[Point]
  implicit val pLine = PicklerGenerator.generatePickler[Line]

  pGeometricFigure
    .addConcreteType[LTBounds]
    .addConcreteType[LBBounds]
    .addConcreteType[Point]
    .addConcreteType[Line]

  implicit val pSharedJs = compositePickler[SharedJs]
  implicit val pPageGeometry = PicklerGenerator.generatePickler[PageGeometry]
  implicit val pTargetRegion = PicklerGenerator.generatePickler[TargetRegion]
  implicit val pLabel = PicklerGenerator.generatePickler[Label]
  implicit val pZone = PicklerGenerator.generatePickler[Zone]
  implicit val pComponent = PicklerGenerator.generatePickler[Component]

  pSharedJs
    .addConcreteType[PageGeometry]
    .addConcreteType[TargetRegion]
    .addConcreteType[Label]
    .addConcreteType[Zone]
    .addConcreteType[Component]

  import TraceLog._
  implicit val pDSL = compositePickler[TraceLog]

  implicit val pSetPageGeometry = PicklerGenerator.generatePickler[SetPageGeometries]
  implicit val pShow            = PicklerGenerator.generatePickler[Show]
  implicit val pShowZone        = PicklerGenerator.generatePickler[ShowZone]
  implicit val pShowComponent        = PicklerGenerator.generatePickler[ShowComponent]
  implicit val pShowLabel       = PicklerGenerator.generatePickler[ShowLabel]
  implicit val pShowVDiff       = PicklerGenerator.generatePickler[ShowVDiff]
  implicit val pFocusOn         = PicklerGenerator.generatePickler[FocusOn]
  implicit val pHRuler          = PicklerGenerator.generatePickler[HRuler]
  implicit val pVRuler          = PicklerGenerator.generatePickler[VRuler]
  implicit val pMessage         = PicklerGenerator.generatePickler[Message]
  implicit val pAll             = PicklerGenerator.generatePickler[All]
  implicit val pLink            = PicklerGenerator.generatePickler[Link]

  pDSL
    .addConcreteType[SetPageGeometries]
    .addConcreteType[Show]
    .addConcreteType[ShowZone]
    .addConcreteType[ShowComponent]
    .addConcreteType[ShowLabel]
    .addConcreteType[ShowVDiff]
    .addConcreteType[FocusOn]
    .addConcreteType[HRuler]
    .addConcreteType[VRuler]
    .addConcreteType[Message]
    .addConcreteType[All]
    .addConcreteType[Link]

}
