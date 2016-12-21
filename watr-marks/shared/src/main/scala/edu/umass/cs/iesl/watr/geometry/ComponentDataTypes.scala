package edu.umass.cs.iesl.watr
package geometry

import scalaz._
import Scalaz._
import watrmarks._

import EnrichGeometricFigures._
import GeometricFigure._

case class TargetRegion(
  id: Int@@RegionID,
  target: Int@@PageID,
  bbox: LTBounds
) {
  override def toString = s"""<reg.${id} pg.${target} ${bbox.prettyPrint}>"""
}

// TODO this should fully replace TargetRegion:
case class TargetFigure(
  id: Int@@RegionID,
  page: Int@@PageID,
  figure: GeometricFigure
) {
  override def toString = s"""<fig.${id} pg.${page} ${figure.toString}>"""
}

case class Zone(
  id: Int@@ZoneID,
  regions: Seq[TargetRegion],
  label: Label
)

case class PageGeometry(
  id: Int@@PageID,
  bounds: LTBounds
)


sealed trait PageAtom {
  def targetRegion: TargetRegion
}

class CharAtom(
  val targetRegion: TargetRegion,
  val char: String,
  val wonkyCharCode: Option[Int] = None
) extends PageAtom {
  override def toString = s"CharAtom($char, $targetRegion)"
}

object CharAtom {
  def unapply(r: CharAtom): Option[(TargetRegion, String, Option[Int])] =
    Some((r.targetRegion, r.char, r.wonkyCharCode))


  def apply(region: TargetRegion,
     char: String,
     wonkyCharCode: Option[Int] = None
  ): CharAtom = new CharAtom(region, char, wonkyCharCode)

}


class ImgAtom(
  val targetRegion: TargetRegion
) extends PageAtom

object ImgAtom {
  def unapply(rg: ImgAtom): Option[(TargetRegion)] = Some(rg.targetRegion)
  def apply(region: TargetRegion): ImgAtom = new ImgAtom(region)

}

case class GlyphInstance(
  glyphHash: String
)


case class GlyphClass(
  glyphHash: String,
  char: Char,
  fontInfo: FontClass
)

case class FontClass(
  name: String,
  familyName: String,
  vendorName: String,
  italicAngle: Int, // = rnd(italic angle * 100)
  weight: String
)


object ComponentTypeEnrichments {
  import TypeTags._
  import GeometricFigure._

  implicit val EqualCharAtom: Equal[CharAtom] = Equal.equal((a, b)  => (a, b) match {
    case (CharAtom(t, c, w), CharAtom(t2, c2, w2)) =>
      t===t2 && c==c2 && w==w2
    case (_, _) => false

  })

  implicit val EqualPageAtom: Equal[PageAtom] = Equal.equal((a, b)  => (a, b) match {
    case (CharAtom(t, c, w), CharAtom(t2, c2, w2)) =>
      t===t2 && c==c2 && w==w2
    case (_, _) => false

  })

  implicit val EqualTargetRegion: Equal[TargetRegion] = Equal.equal((a, b) => (a, b) match {
    case (TargetRegion(id, targetPage, bbox), TargetRegion(id2, targetPage2, bbox2)) =>
      id.unwrap==id2.unwrap && targetPage.unwrap==targetPage2.unwrap && (bbox: GeometricFigure) === bbox2
    case (_, _) => false

  })
  implicit class RicherZone(val zone: Zone) extends AnyVal {

    def area(): Double = {
      zone.regions.foldLeft(0d){ case (acc, a) =>
        a.bbox.area
      }
    }

  }
  implicit class RicherPageAtom(val pageRegion: PageAtom) extends AnyVal {

    def prettyPrint: String = pageRegion match {
      case b: CharAtom => b.prettyPrint
      case b: ImgAtom => "<img>"
    }

    def bestGuessChar: String = pageRegion match {
      case b: CharAtom => b.bestGuessChar
      case b: ImgAtom => ""
    }

    def isSpace: Boolean = pageRegion match {
      case b: CharAtom => b.isSpace
      case b: ImgAtom => false
    }

    def isChar: Boolean = pageRegion match {
      case b: CharAtom => true
      case b: ImgAtom => false
    }

  }

  implicit class RicherTargetRegion(val targetRegion: TargetRegion) extends AnyVal {
    def union(r: TargetRegion): TargetRegion = {
      if (targetRegion.target != r.target) {
        sys.error(s"""cannot union targetRegions from different pages: ${targetRegion} + ${r}""")
      }
      targetRegion.copy(bbox = targetRegion.bbox union r.bbox)
    }

    def intersects(r: TargetRegion): Boolean = {
      val samePage = targetRegion.target == r.target
      samePage && (targetRegion.bbox intersects r.bbox)
    }

    def prettyPrint(): String = {
      val pg = targetRegion.target
      val bbox = targetRegion.bbox.prettyPrint
      s"""<target pg:${pg} ${bbox}"""
    }
  }

  implicit class RicherCharAtom(val charRegion: CharAtom) extends AnyVal {

    def debugPrint: String = {
      val bbox = charRegion.targetRegion.bbox.prettyPrint

      val wonk = charRegion.wonkyCharCode
        .map({ code =>
          if (code==32) { "<sp>"  }
          else { s"?:#${code}?" }
        }) getOrElse { "" }

      // val subs = if (!charRegion.subs.isEmpty()) s"@`charRegion.subs`" else ""


      s"""${charRegion.char} ${wonk} ${bbox}"""
    }

    def prettyPrint: String = {
      charRegion.wonkyCharCode
        .map({ code =>
          if (code==32) { "_"  }
          else { s"#${code}?" }
        })
        .getOrElse({
          charRegion.char
        })
    }

    def bestGuessChar: String = {
      charRegion.wonkyCharCode
        .map({ code =>
          if (code==32) { s" "  }
          else { s"#{${code}}" }
        })
        .getOrElse({
          // if (charRegion)
          charRegion.char
        })
    }

    def isWonky: Boolean = charRegion.wonkyCharCode.isDefined

    def isSpace: Boolean = charRegion.wonkyCharCode.exists(_==32)

  }
}
