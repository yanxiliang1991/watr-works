package edu.umass.cs.iesl.watr
package spindex

import scala.collection.mutable

import geometry._
import watrmarks._

// import PageComponentImplicits._
import utils.IdGenerator
import geometry.syntax._

import tracing.VisualTracer
// import textreflow._
import textreflow.data._
import TypeTags._
import corpora._

import watrmarks.{StandardLabels => LB}
import utils.ExactFloats._


/**

  MultiPageIndex manages:
    - PageIndexes, one per pdf page
    - TextReflow -> Component mapping (e.g., text contained in VisualLine, TextBlock) (TODO perhaps TextReflow should be Zone-associated)
    - Zones, each of which is a list of Components, potentially crossing PageIndexes
    - Relations and Props, e.g., {ClusterID -hasMember-> MentionID}, {id hasProp isTargetEntity}
      - nb. this is a kludge that will be removed at some point

    - BioLabeling, a BIOLU format list of TextBlock regions, for labeling Sections, Headers, and a few other things. Obsolete and on the chopping block

    - Interface to individual PageIndex functions

    - RegionComponent creation and indexing, per PageIndex
      - Creates a new bounding box around lists of other PageIndex components

    - Add/remove Pages/PageAtoms/ConnectedComponents


    TODO:
      - Handle PageIndexes across PDFs (or create another layered class over this one)
      - Caching, either within this class or attachable


  */

class MultiPageIndex(
  stableId: String@@DocumentID,
  val docStore: DocumentZoningApi
) {
  lazy val docId: Int@@DocumentID =
    docStore.getDocument(stableId).getOrElse(sys.error("MultiPageIndex created for non-existent document"))


  def getStableId(): String@@DocumentID = stableId

  val vtrace: VisualTracer = new VisualTracer()

  val pageIndexes = mutable.HashMap[Int@@PageNum, PageIndex]()

  def dbgFilterComponents(pg: Int@@PageNum, include: LTBounds): Unit ={
    pageIndexes.get(pg).foreach ({ pageIndex =>
      val keep = pageIndex.componentIndex.queryForIntersects(include).map(_.id.unwrap)
      pageIndex.componentIndex.getItems
        .filterNot(c => keep.contains(c.id.unwrap))
        .foreach(c => pageIndex.componentIndex.remove(c))
    })
  }
  def dbgFilterPages(pg: Int@@PageNum): Unit ={
    println(s"dbgFilterPages: $pg")
    getPages
      .filterNot(_.unwrap == pg.unwrap)
      .foreach ({ p =>
        println(s"removing page num $p")
        pageIndexes.remove(p)
      })
  }

  // ID generators
  val componentIdGen = IdGenerator[ComponentID]()
  val labelIdGen = IdGenerator[LabelID]()
  // NB this is only for Components, and it a kludge until Component/DocumentZoningApi systems are unified
  val regionIdGen = IdGenerator[RegionID]()


  def getTextReflow(zoneId: Int@@ZoneID): Option[TextReflow] = {
    docStore.getTextReflowForZone(zoneId)
  }

  val relations = mutable.ArrayBuffer[Relation.Record]()
  val props = mutable.ArrayBuffer[Prop.PropRec]()

  def addRelations(rs: Seq[Relation.Record]): Unit = {
    relations ++= rs
  }
  def addProps(rs: Seq[Prop.PropRec]): Unit = {
    props ++= rs
  }


  // type BioLabeling = mutable.MutableList[BioNode]
  // val bioLabelings = mutable.Map[String, BioLabeling]()

  // def bioLabeling(name: String): BioLabeling = {
  //   bioLabelings.getOrElseUpdate(name, mutable.MutableList[BioNode]())
  // }


  // def setChildrenWithLabel(c: Component, l: Label, tree: Seq[Int@@ComponentID]):Unit = {
  //   val pageIndex = pageIndexes(getPageForComponent(c))
  //   pageIndex.setChildrenWithLabel(c.id, l, tree)
  // }

  // def getChildrenWithLabel(c: Component, l: Label): Option[Seq[Int@@ComponentID]] = {
  //   val pageIndex = pageIndexes(getPageForComponent(c))
  //   pageIndex.getChildrenWithLabel(c.id, l)
  // }

  // def getChildren(c: Component, l: Label): Option[Seq[Component]] = {
  //   val pageIndex = pageIndexes(getPageForComponent(c))
  //   pageIndex.getChildrenWithLabel(c.id, l)
  //     .map(tree => tree.map{ cid =>
  //       pageIndex.componentIndex.get(cid.unwrap).getOrElse {
  //         sys.error(s"getChildren(${c}, ${l}) contained an invalid component id: ${cid}")
  //       }
  //     })
  // }

  def getPageForComponent(c: Component): Int@@PageNum = {
    c.pageRegion.page.pageNum
  }



  def addLabel(c: Component, l: Label): Component = {
    val pageId = getPageForComponent(c)
    val pinfo = getPageIndex(pageId)
    pinfo.addLabel(c, l)
  }

  def removeLabel(c: Component, l: Label): Component = {
    val pageId = getPageForComponent(c)
    val pinfo = getPageIndex(pageId)
    pinfo.removeLabel(c, l)
  }


  def getLabels(c: Component): Set[Label] = {
    val pageId = getPageForComponent(c)
    val pinfo = getPageIndex(pageId)
    pinfo.getLabels(c)
  }

  def getPageIndex(pageNum: Int@@PageNum) = pageIndexes(pageNum)

  def removeComponent(c: Component): Unit = {
    val pinfo = getPageIndex(getPageForComponent(c))
    pinfo.componentToLabels.get(c.id)
      .foreach{ labels =>
        labels.foreach { label =>
          pinfo.labelToComponents.get(label)
            .map{_.filterNot(id => id == c.id)}
            .foreach{ filtered =>
              pinfo.labelToComponents.update(label, filtered)
            }
        }

      }
    pinfo.componentToLabels -= c.id
    pinfo.componentIndex.remove(c)

  }

  def labelRegion(components: Seq[Component], role: Label): Option[(RegionComponent, PageRegion)] = {
    if (components.isEmpty) None else {
      val totalBounds = components.map(_.bounds).reduce(_ union _)
      val targetPages = components.map(_.pageNum.unwrap)
      val numOfTargetPages =  targetPages.toSet.size

      if (numOfTargetPages != 1) {
        sys.error(s"""cannot label connected components from different pages (got pages=${targetPages.mkString(", ")})""")
      }

      val pageNum =  PageNum(targetPages.head)

      val pageId = docStore.getPage(docId, pageNum).get
      val regionId = docStore.addTargetRegion(pageId, totalBounds)
      val targetRegion = docStore.getTargetRegion(regionId)
      val pageRegion = PageRegion(targetRegion.page, targetRegion.bbox)

      val region = createRegionComponent(pageRegion, role, None)
      // componentIdToRegionId.put(region.id, targetRegion.id)

      Some((region, targetRegion))
    }
  }


  def createRegionComponent(targetRegion: PageRegion, role: Label, text:Option[String]): RegionComponent = {
    val region = RegionComponent(componentIdGen.nextId, role, targetRegion, text)
    addComponent(region)

    region
  }

  def addCharAtom(pageAtom: CharAtom): AtomicComponent = {
    val c = AtomicComponent(componentIdGen.nextId, pageAtom)
    addComponent(c)
    c
  }

  def addPathItem(path: PageItem.Path): Seq[RegionComponent] = {

    val slineCCs = path.slantedLines
      .map{ line =>
        val region = path.pageRegion.copy(bbox = line.bounds.copy(height=0.01.toFloatExact))
        val c = createRegionComponent(region, LB.LinePath, None)
        addComponent(c)
        c
      }

    val hlineCCs = path.horizontalLines
      .map{ line =>
        val region = path.pageRegion.copy(bbox = line.bounds.copy(height=0.01.toFloatExact))
        val c = createRegionComponent(region, LB.HLinePath, None)
        addComponent(c)
        c
      }
    val vlineCCs = path.verticalLines()
      .map{ line =>
        val region = path.pageRegion.copy(bbox = line.bounds.copy(width=0.01.toFloatExact))
        val c = createRegionComponent(region, LB.VLinePath, None)
        addComponent(c)
        c
      }
    val region = path.pageRegion
    val c = createRegionComponent(region, LB.PathBounds, None)
    addComponent(c)

    Seq(c) ++ hlineCCs ++ vlineCCs ++ slineCCs
  }

  def addImageAtom(pageAtom: PageItem.ImageAtom): RegionComponent = {
    // println(s"addImageAtom ${pageAtom.pageRegion}")
    val c = createRegionComponent(pageAtom.pageRegion, LB.Image, None)
    addComponent(c)
    c
  }

  def getPageAtoms(pageNum: Int@@PageNum): Seq[AtomicComponent] = {
    getPageIndex(pageNum).getPageAtoms
  }

  def getImageAtoms(pageNum: Int@@PageNum): Seq[RegionComponent] = {
    getPageIndex(pageNum).getImageAtoms
  }

  def getComponent(id: Int@@ComponentID, pageId: Int@@PageNum): Component = {
    getPageIndex(pageId).componentIndex.getItem(id.unwrap)
  }

  def addComponent(c: Component): Component = {
    val pageNum = c.pageRegion.page.pageNum
    getPageIndex(pageNum)
      .addComponent(c)
  }

  def getPageGeometry(p: Int@@PageNum) = pageIndexes(p).pageGeometry

  def getPages(): List[Int@@PageNum] = {
    pageIndexes.keys.toList.sortBy(PageNum.unwrap(_))
  }


  def addPage(pageGeometry: PageGeometry): PageIndex = {
    val pageIndex = new PageIndex(
      pageGeometry
    )

    val existing = pageIndexes.put(pageGeometry.id, pageIndex)

    existing.foreach { e =>
      sys.error("adding new page w/existing id")
    }
    pageIndex
  }


  // def addBioLabels(label: Label, node: BioNode): Unit = {
  //   addBioLabels(label, Seq(node))
  // }

  // def addBioLabels(label: Label, nodes: Seq[BioNode]): Unit = {
  //   val labelId = labelIdGen.nextId
  //   val l = label.copy(id=labelId)

  //   if (nodes.length==1) {
  //     nodes.foreach(_.pins += l.U)
  //   } else if (nodes.length > 1) {
  //     nodes.head.pins += l.B
  //     nodes.last.pins += l.L

  //     nodes.drop(1).dropRight(1).foreach(
  //       _.pins += l.I
  //     )
  //   }
  // }

}