package edu.umass.cs.iesl.watr
package spindex


import watrmarks.{StandardLabels => LB, Label}
import utils.Histogram
import utils.Histogram._
import textboxing.{TextBoxing => TB}
import TB._

import EnrichGeometricFigures._
import utils.SlicingAndDicing._
import utils.{CompassDirection => Compass}
import utils.VisualTracer._
import spindex.GeometricFigure._
import scala.collection.mutable
import ComponentRendering.PageAtom
import utils.EnrichNumerics._
import ComponentRendering.VisualLine

object ComponentOperations {
  def centerX(cb: PageAtom) = cb.region.bbox.toCenterPoint.x
  def centerY(cb: PageAtom) = cb.region.bbox.toCenterPoint.y

  def spaceWidths(cs: Seq[CharAtom]): Seq[Double] = {
    // pairwiseSpaceWidths(cs.map(Component(_)))
    val cpairs = cs.sliding(2).toList

    val dists = cpairs.map({
      case Seq(c1, c2)  => c2.region.bbox.left - c1.region.bbox.right
      case _  => 0d
    })

    dists :+ 0d
  }

  def pairwiseSpaceWidths(cs: Seq[Component]): Seq[Double] = {
    val cpairs = cs.sliding(2).toList

    val dists = cpairs.map({
      case Seq(c1, c2)  => c2.bounds.left - c1.bounds.right
      case _  => 0d
    })

    dists :+ 0d
  }

  def determineCharSpacings(chars: Seq[CharAtom]): Seq[Double] = {
    val dists = spaceWidths(chars)
    val resolution = 0.5d

    val hist = histogram(dists, resolution)

    val spaceDists = hist.getFrequencies
      .sortBy(_.frequency)
      .dropWhile(_.frequency==0)
      .map(_.value)
      .reverse

    spaceDists
  }

  implicit class RicherComponent(val selfComponent: Component) extends AnyVal {

    def zoneIndex = selfComponent.zoneIndex
    def vtrace = selfComponent.zoneIndex.vtrace

    def height: Double  = selfComponent.bounds.height

    def hasLabel(l: Label): Boolean = selfComponent.getLabels.contains(l)

    def vdist(other: Component): Double = {
      selfComponent.bounds.toPoint(Compass.W).vdist(
        other.bounds.toPoint(Compass.W)
      )
    }

    def columnContains(other: Component): Boolean = {
      val slopFactor = 4.5d // XXX test this magic number?

      val left = selfComponent.bounds.toWesternPoint.x
      val right = selfComponent.bounds.toEasternPoint.x

      val otherLeft = other.bounds.toWesternPoint.x + slopFactor
      val otherRight = other.bounds.toEasternPoint.x - slopFactor

      left <= otherLeft && otherRight <= right

    }

    def columnIntersects(other: Component): Boolean = {
      val slopFactor = 0.31d // XXX what is this magic number?

      val otherx0 = other.bounds.toWesternPoint.x-slopFactor
      val otherx1 = other.bounds.toEasternPoint.x+slopFactor
      val candx0 = selfComponent.bounds.toWesternPoint.x
      val candx1 = selfComponent.bounds.toEasternPoint.x
      val candRightInside = otherx0 <= candx1 && candx1 <= otherx1
      val candLeftOutside = candx0 < otherx0
      val candLeftInside = otherx0 <= candx0 && candx0 <= otherx1
      val candRightOutside = otherx1 < candx1

      val crossesLeft = candRightInside && candLeftOutside
      val crossesRight = candLeftInside && candRightOutside


      crossesLeft || crossesRight
    }

    def isOverlappedVertically(other: Component): Boolean = {
      !(selfComponent.isStrictlyAbove(other) || selfComponent.isStrictlyBelow(other))
    }

    def isStrictlyAbove(other: Component): Boolean = {
      val y1 = selfComponent.bounds.toPoint(Compass.S).y
      val y2 = other.bounds.toPoint(Compass.N).y
      y1 < y2
    }
    def isStrictlyBelow(other: Component): Boolean = {
      val y1 = selfComponent.bounds.toPoint(Compass.N).y
      val y2 = other.bounds.toPoint(Compass.S).y
      y1 > y2
    }

    def isStrictlyLeftOf(other: Component): Boolean = {
      val rightEdge = selfComponent.bounds.toEasternPoint.x
      val otherLeftEdge = other.bounds.toWesternPoint.x
      rightEdge < otherLeftEdge
    }

    def isStrictlyRightOf(other: Component): Boolean = {
      val leftEdge = selfComponent.bounds.toEasternPoint.x
      val otherRightEdge = other.bounds.toWesternPoint.x
      otherRightEdge < leftEdge
    }

    def candidateIsOutsideLineBounds(other: Component): Boolean = {
      selfComponent.isStrictlyLeftOf(other) || selfComponent.isStrictlyRightOf(other)
    }




    def isBelow(other: Component) = selfComponent.bounds.top > other.bounds.top
    def isAbove(other: Component) = selfComponent.bounds.top < other.bounds.top

    def hasSameVCenterPoint(tolerance: Double=0.1)(other: Component) =
      selfComponent.bounds.toCenterPoint.x.eqFuzzy(tolerance)(other.bounds.toCenterPoint.x)

    def hasSameLeftEdge(tolerance: Double=0.3)(other: Component) =
      selfComponent.bounds.toPoint(Compass.W).x.eqFuzzy(tolerance)(other.bounds.toPoint(Compass.W).x)

    def isEqualWidth(tolerance: Double=0.1)(other: Component) =
      selfComponent.bounds.width.eqFuzzy(tolerance)(other.bounds.width)


    import utils.TraceLog

    def vtraceHistogram(hist: Histogram): TraceLog = {
      vtraceHistogram(
        hist.getFrequencies
          .sortBy(_.frequency)
          .reverse
          .takeWhile(_.frequency > 0)
          .map{b=>(b.value, b.frequency)},
        hist.getStartingResolution, hist.getComputedResolution
      )
    }

    def vtraceHistogram(vfs: Seq[(Double, Double)], resStart: Double, resComputed: Double): TraceLog = {
        message(
          vjoin()(
            s"histogram: res(in)=${resStart}, res(computed):${resComputed}", indent(2)(
            hjoins(sep=" ")(
              vfs.map({case d =>
                vjoin(center1)(
                  "v="+d._1.pp,
                  "f="+d._2.pp
                )
              })
            ))
          )
        )
    }

    def getMostFrequentValues(in: Seq[Double], resolution: Double, msg: String=""): Seq[Double] = {
      val hist = histogram(in, resolution)

      val vfs = hist.getFrequencies
        .sortBy(_.frequency)
        .reverse
        .takeWhile(_.frequency > 0)
        .map{b=>(b.value, b.frequency)}

      vtrace.trace(vtraceHistogram(hist))

      vfs.map(_._1)
    }



    def atoms = selfComponent.queryInside(LB.PageAtom)

    def findCommonToplines(): Seq[Double] = {
      vtrace.trace(message("findCommonToplines"))
      getMostFrequentValues(
        atoms.map({c => c.bounds.top}),
        0.01d
      )
    }

    def findCommonBaselines(): Seq[Double] = {
      vtrace.trace(message("findCommonBaselines"))
      getMostFrequentValues(
        atoms.map({c => c.bounds.bottom}),
        0.01d
      )
    }

    // List of avg distances between chars, sorted largest (inter-word) to smallest (intra-word)
    def determineSpacings(): Seq[Double] = {
      val dists = pairwiseSpaceWidths(atoms)
      val resolution = 0.3d

      val hist = Histogram.histogram(dists, resolution)

      val spaceDists = hist.getFrequencies
        .sortBy(_.frequency)
        .reverse
        .takeWhile(_.frequency > 0d)

      vtrace.trace("most frequent space dists" withTrace vtraceHistogram(hist))

      spaceDists.map(_.value)
    }



    def labelSuperAndSubscripts(): Unit = {
      vtrace.trace(begin("labelSuperAndSubscripts()"))
      vtrace.trace("Starting Tree" withInfo VisualLine.renderRoleTree(selfComponent))

      val tops = findCommonToplines()
      val bottoms = findCommonBaselines()
      val modalTop = tops.head // - 0.01d
      val modalBottom = bottoms.head // + 0.01d
      val modalCenterY = (modalBottom + modalTop)/2

      // indicate a set of h-lines inside selfComponent.targetRegion
      def indicateHLine(y: Double): TargetFigure = y.toHLine
        .clipTo(selfComponent.targetRegion.bbox)
        .targetTo(selfComponent.targetRegion.target)

      vtrace.trace(
        "modal top     " withTrace indicateHLine(modalTop),
        "modal bottom  " withTrace indicateHLine(modalBottom),
        "modal center Y" withTrace indicateHLine(modalCenterY)
      )


      selfComponent.getChildren(LB.TextSpan).foreach({ textSpan =>

        val supOrSubList = textSpan.atoms.map { atom =>
          val cctr = atom.bounds.toCenterPoint
          val cbottom = atom.bounds.bottom
          val supSubTolerance = selfComponent.bounds.height / 20.0

          if (atom.bounds.top < modalTop && atom.bounds.bottom > modalBottom) {
            LB.CenterScript
          } else if (atom.bounds.bottom.eqFuzzy(supSubTolerance)(modalBottom)) {
            LB.CenterScript
          } else if (cctr.y < modalCenterY) {
            LB.Sup
          } else {
            LB.Sub
          }
        }

        val labelSpans = supOrSubList.groupByPairs({(l1, l2) => l1 == l2 })
          .map({lls => lls.head})

        val supSubRegions = textSpan
          .groupAtomsIf({(atom1, atom2, pairIndex) =>
            supOrSubList(pairIndex) == supOrSubList(pairIndex+1)
            // vtrace.trace(message(s"splitIf ${regionStartIndexes.toList} contains pairIndex=$pairIndex"))
            // regionStartIndexes.contains(pairIndex)
            true
          }, {(region, regionIndex) =>
            // vtrace.trace(message(s"splitIf (true) r:${region}, i:${regionIndex}"))
            region.addLabel(labelSpans(regionIndex))
          })

        textSpan.setChildren(LB.TextSpan, supSubRegions)

      })

      vtrace.trace("Ending Tree" withInfo VisualLine.renderRoleTree(selfComponent))

      vtrace.trace(end("labelSuperAndSubscripts()"))
    }


    def groupTokens(): Unit = {
      vtrace.trace(begin("Split On Whitespace"))
      vtrace.trace(message(s"chars: ${selfComponent.chars}"))
      // TODO assert selfComponent roleLabel structure is TextSpan/TextSpan*/PageAtom

      // Scan text in line to determine most common distances between consecutive chars
      val charDists = determineSpacings()
      // Most frequent space is assumed to be the space between chars within a word:
      val modalLittleGap = charDists.head
      // The next most frequent space (that is larger than the within-word space) is assumed to be the space between words:
      val modalBigGap = charDists
        .drop(1)
        .filter(_ > modalLittleGap)
        .headOption.getOrElse(modalLittleGap)

      val splitValue = (modalBigGap*2+modalLittleGap)/3
      // val splittable = charDists.length > 1

      vtrace.trace(
        begin("Char Distance Metrics"),
        message(s"""| top char dists    = ${charDists.map(_.pp).mkString(", ")}
                    | modal little gap  = ${modalLittleGap.pp} modal big gap = ${modalBigGap.pp}
                    | splitValue        = ${splitValue.pp}
                    |""".stripMargin.mbox)
      )

      selfComponent.addLabel(LB.Tokenized)
      selfComponent.groupAtomsIf({ (c1, c2, pairIndex) =>

        // case Seq(c1, c2)  => c2.bounds.left - c1.bounds.right
        val pairwiseDist = c2.bounds.left - c1.bounds.right

        vtrace.trace(
          showRegions(Seq(c1.targetRegion, c2.targetRegion)),
          message(
            vcat(left)(Seq(
              hcat(center1)(Seq(PageAtom.boundsBox(c1) + " <-> " + PageAtom.boundsBox(c2))),
              s"""|  pairwisedist: ${pairwiseDist}  east-west dist: ${pairwiseDist}
                  |  split value: ${splitValue},
                  |  Will split? : ${pairwiseDist > splitValue}
                  |""".stripMargin.mbox
            ))
          )
        )

        pairwiseDist < splitValue

      }, { (newRegion, regionIndex) =>
        newRegion.addLabel(LB.Token)
        selfComponent.addChild(newRegion)
      })

      vtrace.trace("Tree after split whitespace" withInfo VisualLine.renderRoleTree(selfComponent))

      vtrace.trace(end("Split On Whitespace"))
    }


    def tokenizeLine(): Unit = {
      if (!selfComponent.getLabels.contains(LB.TokenizedLine)) {

        vtrace.trace(begin("Tokenize Line"), focusOn(selfComponent.targetRegion))
        vtrace.trace(message(s"Line chars: ${selfComponent.chars}"))

        labelSuperAndSubscripts()
        // Structure is: VisualLine/TextSpan/[TextSpan[sup/sub/ctr]]
        // Group each TextSpan w/sup/sub/ctr-script label into tokens
        selfComponent.getDescendants(LB.TextSpan)
          .filterNot(_.getLabels
            .intersect( Set(LB.CenterScript, LB.Sub, LB.Sup) )
            .isEmpty)
          .foreach{ _.groupTokens() }

        // Now figure out how the super/sub/normal text spans should be joined together token-wise

        selfComponent.addLabel(LB.TokenizedLine)

        vtrace.trace("Final Tokenization" withInfo
          ComponentRendering.VisualLine.render(selfComponent))

        vtrace.trace(end("Tokenize Line"))
      }
    }


    def determineNormalTextBounds: LTBounds = {
      val mfHeights = Histogram.getMostFrequentValues(selfComponent.getChildren.map(_.bounds.height), 0.1d)
      val mfTops = Histogram.getMostFrequentValues(selfComponent.getChildren.map(_.bounds.top), 0.1d)


      val mfHeight= mfHeights.headOption.map(_._1).getOrElse(0d)
      val mfTop = mfTops.headOption.map(_._1).getOrElse(0d)

      selfComponent.getChildren
        .map({ c =>
          val cb = c.bounds
          LTBounds(
            left=cb.left, top=mfTop,
            width=cb.width, height=mfHeight
          )
        })
        .foldLeft(selfComponent.getChildren().head.bounds)( { case (b1, b2) =>
          b1 union b2
        })
    }

  }
}






    // def tokenizeLineOrig(): Component = {
    //   if (!component.getLabels.contains(LB.TokenizedLine)) {

    //     vtrace.trace(
    //       begin("Tokenize Line"),
    //       focusOn(component.targetRegion),
    //       message(s"chars:`${component.chars}`")
    //       // all(component.children.map(c => showRegion(c.targetRegion)))
    //     )

    //     val tops = findCommonToplines()
    //     val bottoms = findCommonBaselines()
    //     val modalTop = tops.head // - 0.01d

    //     val modalBottom = bottoms.head // + 0.01d

    //     val modalCenterY = (modalBottom + modalTop)/2

    //     // indicate a set of h-lines inside component.targetRegion
    //     def indicateHLine(y: Double): TargetFigure = y.toHLine
    //       .clipTo(component.targetRegion.bbox)
    //       .targetTo(component.targetRegion.target)

    //     vtrace.trace(
    //       "modal top" withTrace indicateHLine(modalTop),
    //       "modal bottom" withTrace indicateHLine(modalBottom),
    //       "modal center Y" withTrace indicateHLine(modalCenterY)
    //     )


    //     // label individual chars as super/sub if char.ctr fall above/below centerline
    //     val supSubs = component.children.map({c =>
    //       val cctr = c.bounds.toCenterPoint
    //       val cbottom = c.bounds.bottom
    //       val supSubTolerance = component.bounds.height / 10.0

    //       // vtrace.trace(
    //       //   "char center" withTrace cctr.targetTo(component.targetRegion.target)
    //       // )

    //       val maybeLabel: Option[Label] = {
    //         // vtrace.trace()
    //         // println(s"""Line: ${component.chars}""")
    //         // println(s"""(sub)  ${c.chars}>  cctr.toCenterPoint: ${cctr.prettyPrint} modalCenterY: ${modalCenterY}""")
    //         // println(s"""modal bottom: ${modalBottom}, c.bottom = ${cbottom}""")

    //         if (c.bounds.top < modalTop && c.bounds.bottom > modalBottom) {
    //           None // if our child's top/bottom extends beyond modal top/bottom, it is a larger font and not super/sub
    //         }
    //         else if (c.bounds.bottom.eqFuzzy(supSubTolerance)(modalBottom)) None
    //         else if (cctr.y < modalCenterY) LB.Sup.some
    //         else LB.Sub.some
    //       }

    //       maybeLabel.foreach { c.addLabel(_) }
    //       c
    //     })


    //     def isLabelBoundary(l: Label, a: Component, b: Component): Boolean = {
    //       ((a.hasLabel(l) && !b.hasLabel(l)) ||
    //         (!a.hasLabel(l) && b.hasLabel(l)))
    //     }

    //     val supSubGroups = supSubs.splitOnPairs { (ca, cb) =>
    //       isLabelBoundary(LB.Sup, ca, cb) || isLabelBoundary(LB.Sub, ca, cb)
    //     }

    //     def concatLabels(l: Label, cs: Seq[Component]): Seq[Component] = {
    //       cs.headOption.map({ c0 =>
    //         if (c0.hasLabel(l)) {
    //           cs.foreach(_.removeLabel(l))
    //           Seq(zoneIndex.connectComponents(cs, l))
    //         } else { cs }
    //       }).getOrElse(cs)
    //     }

    //     val connectedSupSubs = (for {
    //       connSpan <- supSubGroups
    //     } yield {
    //       connSpan |>
    //         (concatLabels(LB.Sup, _)) |>
    //         (concatLabels(LB.Sub, _))
    //     }).flatten


    //     vtrace.trace(begin("Split On Whitespace"))
    //     val charDists = determineSpacings()
    //     // Most frequent space is assumed to be the space between chars within a word:
    //     val modalLittleGap = charDists.head
    //     // The next most frequent space (that is larger than the within-word space) is assumed to be the space between words:
    //     val modalBigGap = charDists
    //       .drop(1)
    //       .filter(_ > modalLittleGap)
    //       .headOption.getOrElse(modalLittleGap)

    //     val splitValue = (modalBigGap*2+modalLittleGap)/3
    //     val splittable = charDists.length > 1


    //     // hist display

    //     vtrace.trace(
    //       begin("Char Distance Metrics"),
    //       message(s"""| top char dists: ${charDists.map(_.pp).mkString(", ")}
    //                   | modalTop = ${modalTop.pp} modalBottom = ${modalBottom.pp}
    //                   | modalCenter: ${modalCenterY.pp}
    //                   | modal little gap = ${modalLittleGap.pp} modal big gap = ${modalBigGap.pp}
    //                   | splitValue = ${splitValue.pp}
    //                   |""".stripMargin.mbox)
    //     )

    //     val tokenSpans = (connectedSupSubs
    //       .zip(pairwiseSpaceWidths(connectedSupSubs)))
    //       .splitOnPairs({ case ((c1, d1), (c2, d2)) =>
    //         val dist = c2.bounds.left - c1.bounds.right

    //         // val effectiveDist = d1*1.1
    //         val effectiveDist = dist


    //         def boundsBox(c: Component): TB.Box = {
    //           vcat(center1)(Seq(
    //             c.chars,
    //             c.bounds.top.pp,
    //             c.bounds.left.pp +| c.bounds.right.pp,
    //             c.bounds.bottom.pp,
    //             "(w=" + c.bounds.width.pp + ")"
    //           ))

    //         }


    //         vtrace.trace(
    //           showRegions(Seq(c1.targetRegion, c2.targetRegion)),
    //           message(
    //             vcat(left)(Seq(
    //               hcat(center1)(Seq(boundsBox(c1) + " <-> " + boundsBox(c2))),
    //               s"""|  pairwisedist: ${d1}  east-west dist: ${dist} effective dist: ${effectiveDist}
    //                   |  split value: ${splitValue}, splittable? ${splittable}
    //                   |  Will split? : ${splittable && effectiveDist > splitValue}
    //                   |""".stripMargin.mbox
    //             ))
    //           )
    //         )

    //         splittable && effectiveDist > splitValue

    //       })



    //     val asTokens = tokenSpans.map(_.map(_._1))
    //       .map({cs => zoneIndex.connectComponents(cs, LB.Token) })

    //     vtrace.trace(
    //       "final tokenization" withTrace showRegions(asTokens.map(_.targetRegion))
    //     )

    //     vtrace.trace(end("Split On Whitespace"))

    //     component.replaceChildren(asTokens)
    //     component.addLabel(LB.TokenizedLine)

    //     vtrace.trace(end("Tokenize Line"))

    //   }
    //   component

    // }

      // // (start, len, label)
      // val labeledRegions = mutable.Stack[(Int, Int, Label)]()
      // def extendOrBegin(lb: Label, c: Component): Unit = {
      //   if (!labeledRegions.isEmpty && labeledRegions.top._3 == lb) {
      //     val t = labeledRegions.pop()
      //     labeledRegions.push((t._1, t._2+1, t._3))
      //     vtrace.trace(s"extend ${lb} over" withTrace message(labeledRegions.top.toString()))
      //   } else {
      //     val (st, len, _) = if (!labeledRegions.isEmpty) labeledRegions.top else (-1, 1, lb)
      //     labeledRegions.push((st+len, 1, lb))
      //     vtrace.trace(s"begin ${lb} at" withTrace message(labeledRegions.top.toString()))
      //   }
      // }

      // component.getChildren(LB.TextSpan).foreach({ textSpan =>
      //   textSpan.atoms.foreach { atom =>
      //     val cctr = atom.bounds.toCenterPoint
      //     val cbottom = atom.bounds.bottom
      //     val supSubTolerance = component.bounds.height / 20.0


      //     if (atom.bounds.top < modalTop && atom.bounds.bottom > modalBottom) {
      //       extendOrBegin(LB.CenterScript, atom)
      //     } else if (atom.bounds.bottom.eqFuzzy(supSubTolerance)(modalBottom)) {
      //       extendOrBegin(LB.CenterScript, atom)
      //     } else if (cctr.y < modalCenterY){
      //       extendOrBegin(LB.Sup, atom)
      //     } else {
      //       extendOrBegin(LB.Sub, atom)
      //     }
      //   }
      // })


      // vtrace.trace(message(s"${textSpan.chars.mkString} textSpan.bounds.top < modalTop && textSpan.bounds.bottom > modalBottom"))
      // vtrace.trace(message(s"${textSpan.chars.mkString} bottom ~= modalBottom: ${textSpan.bounds.bottom.pp} ~= ${modalBottom.pp}"))
      // vtrace.trace(message(s"^ ${textSpan.chars.mkString} => ctr.y < modalCenterY: ${cctr.y.pp} < ${modalCenterY.pp}"))
      // vtrace.trace(message(s"_ ${textSpan.chars.mkString}"))
      // val regions = labeledRegions.toList.reverse
      // val regionStartIndexes = regions.map(_._1)
      // val labels = regions.map(_._3)
      // vtrace.trace("Final span labeling " withTrace
      //   message(
      //     regions.map({case (a, b, c) => s"(s:$a len:$b $c)"}).mkString(", ")
      //   ))

      // component.getChildren(LB.TextSpan)
      //   .foreach({ startingTextSpan =>
      //     startingTextSpan
      //       .splitAtomsIf({(c1, c2, pairIndex) =>
      //         vtrace.trace(message(s"splitIf ${regionStartIndexes.toList} contains pairIndex=$pairIndex"))
      //         regionStartIndexes.contains(pairIndex)
      //       }, {(region, regionIndex) =>
      //         vtrace.trace(message(s"splitIf (true) r:${region}, i:${regionIndex}"))
      //         region.addLabel(labels(regionIndex))
      //       })
      //   })
