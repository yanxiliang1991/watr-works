package edu.umass.cs.iesl.watr
package watrcolors
package client
package pages

import parts._
import wiring._

import scala.async.Async
import scala.concurrent.Future

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom
import org.scalajs.dom.ext._


import labeling._
import watrmarks._
import native.mousetrap._
import native.fabric

// import watrmarks.{StandardLabels => LB}

import scaladget.stylesheet.{all => sty}
import sty.{ctx => _, _}
import scalatags.JsDom.all._

import rx._
import scaladget.api._
import scaladget.tools.JsRxTags.{ctx => _, _}

import TypeTags._
import dom.raw.MouseEvent

import scala.collection.mutable
// import utils.Color
import BootstrapBits._

class ClientStateRx(
  initUIState: UIState,
  uiRequestCycle: (UIRequest) => Future[Unit]
)(implicit co: Ctx.Owner) extends BaseClientDefs {

  val currentUIState: Var[UIState] = Var(initUIState)

  val DocumentLabelerIdentifier(_, _, initPagination, _) = initUIState.currentLabeler

  val paginator = new Paginator(initPagination)
  val paginationRx = paginator.getRx

  val triggerRepaginate = currentUIState.trigger {
    val currLabelerId = currentUIState.now.currentLabeler
    val DocumentLabelerIdentifier(_, _, p, _) = currLabelerId
    val Pagination(pgCount, currPage, pgInfo) = p

    uiRequestCycle(
      UIRequest(
        currentUIState.now,
        MenuAction(LabelAction.NavigateTo(currPage.unwrap))
      )
    )
  }

  val selectedLabel: Var[Option[Label]] = Var(None)

  val setupLabelChooser: RxModifier = Rx {
    // val UIState(_, _, sels, labelerId) = currentUIState()
    // val DocumentLabelerIdentifier(_, _, _, labelTypes) = initUIState.currentLabeler

    // val selectActiveLabel: SelectableButtons = radios()(
    //   (labelTypes.toList.zipWithIndex.map{ case ((lbl, clr), i) =>

    //     if(i==0) { selectedLabel() = Some(lbl) }

    //     selectableButton(
    //       lbl.fqn,
    //       (i==0),
    //       modifierSeq = (sty.btn_small +++ (backgroundColor:=clr.cssHash())),
    //       onclick = () => {selectedLabel() = Some(lbl)})
    //   }):_*
    // )
    // selectActiveLabel.render
    div()
  }


  // val labelerIdentifier: Var[LabelerIdentifier] = Var(labelerId)
  // val selectionConstraint: Var[Constraint] = Var(ByLine)
  // val selections: Var[Seq[Int@@ZoneID]] = Var(Seq())

  val doMergeZones: Var[Boolean] = Var(false)
  val doDeleteZone: Var[Boolean] = Var(false)

  // def toUIState = currentUIState.now


  val selectionInProgress: Var[Boolean] = Var(false)

  def startSelection(): Unit = {
    selectionInProgress() = true
    selectionInProgress() = false
  }

  val rx2 = doDeleteZone.triggerLater{
    if (doDeleteZone.now) {
      println(s"Delete: uiRequestCycle()")
      uiRequestCycle(
        UIRequest(
          currentUIState.now,
          MenuAction(LabelAction.DeleteZone(ZoneID(0)))
        )
      )
    }
    doDeleteZone() = false
  }

  val rx3 = doMergeZones.triggerLater{
    if (doMergeZones.now) {
      println("Merge: uiRequestCycle()")
      uiRequestCycle(
        UIRequest(
          currentUIState.now,
          MenuAction(LabelAction.MergeZones(List()))
        )
      )
    }
    doMergeZones() = false
  }


}

@JSExportTopLevel("WatrColors")
object WatrColors extends  BaseClientDefs {
  import BootstrapBits._



  object states {
    // def modState(f: UIState => UIState): Unit = {
    //   uiState = f(uiState)
    // }

    // def selectByChar(): Unit = modState(_.copy(selectionConstraint = ByChar))
    // def selectByLine(): Unit = modState(_.copy(selectionConstraint = ByLine))
    // def selectByRegion(): Unit = modState(_.copy(selectionConstraint = ByRegion))
    // def setLabel(l: Label): Unit = modState(_.copy(selectedLabel=Option(l)))

  }

  val keybindings: List[(String, (MousetrapEvent) => Unit)] = List(
    // "l t" -> ((e: MousetrapEvent) => states.setLabel(LB.Title)),

    // "s l" -> ((e: MousetrapEvent) => states.selectByLine()),
    // "s c" -> ((e: MousetrapEvent) => states.selectByChar()),
    // "s b" -> ((e: MousetrapEvent) => states.selectByRegion()),

    "s s" -> ((e: MousetrapEvent) => clientState.foreach {_.startSelection()})
  )

  def initKeybindings() = {
    Mousetrap.reset()
    keybindings.foreach { case (str, fn) =>
      val bindFunc: MousetrapEvent => Boolean = e => {
        fn(e); true
      }

      Mousetrap.bind(str, bindFunc, "keypress")
    }
  }


  val activeFabricObjects = mutable.HashMap[Int@@WidgetID, fabric.FabricObject]()

  def uiRequestCycle(req: UIRequest)(implicit ctx: Ctx.Owner): Future[Unit] = for {
    uiResponse  <- shell.uiRequest(req)
  } yield {
    println("complete:uiRequest ")
    // uiState = uiResponse.uiState
    fabricCanvas.renderOnAddRemove = false
    val adds = uiResponse.changes
      .collect {
        case WidgetMod.AddLw(wid, widget) => widget.get
      }

    val dels = uiResponse.changes
      .collect {
        case WidgetMod.ClearAllLw() =>
          clear()

        case WidgetMod.RmLw(wid, widget) =>

          activeFabricObjects.get(wid).map {fobj =>
            fabricCanvas.remove(fobj)
          }
      }

    // println(s"""adding: ${adds.mkString("\n")}""")
    // println(s"""rmv: ${dels.mkString("\n")}""")

    println("begin:renderLabelWidget")
    renderLabelWidget(adds).foreach {
      case (bbox, fobjs) =>
        println(s"adding within $bbox")
        fobjs.foreach{os => os.foreach{ case (wid, obj)  =>
          activeFabricObjects.put(wid, obj)
          fabricCanvas.add(obj)
        }}
        fabricCanvas.renderAll()
        fabricCanvas.renderOnAddRemove = true
    }

    clientState.foreach { st =>
      st.currentUIState() = uiResponse.uiState
    }
  }


  object shell {
    import autowire._
    import UPicklers._

    val Client = new WebsideClient("shell")
    val api = Client[WatrShellApi]

    def uiRequest(r: UIRequest): Future[UIResponse] = {
      println("begin:uiRequest ")
      api.uiRequest(r).call()
    }

    def createDocumentLabeler(labelerRequest: LabelerIdentifier): Future[UIResponse] = {
      api.fetchDocumentLabeler(labelerRequest).call()
    }

  }

  def clear(): Unit = {
    activeFabricObjects.clear()
    fabricCanvas.clear()
  }


  def echoLabeler(lwidget: Seq[AbsPosWidget]): Future[Unit] = Async.async {
    println("echoLabeler()")
    try {

      renderLabelWidget(lwidget).foreach {
        case (bbox, fobjs) =>
          fabricCanvas.renderOnAddRemove = false
          clear()
          fabricCanvas.setWidth(bbox.width.toInt)
          fabricCanvas.setHeight(bbox.height.toInt)

          fobjs.foreach{os => os.foreach{ case (wid, obj)  =>
            activeFabricObjects.put(wid, obj)
            fabricCanvas.add(obj)
          }}
          fabricCanvas.renderAll()
          fabricCanvas.renderOnAddRemove = true
      }

    } catch {
      case t: Throwable =>
        println(s"error ${t}, ${t.getCause}")
        t.printStackTrace()
        throw t
    }
  }





  @JSExport
  def setupClickCatchers(clientStateRx: ClientStateRx)(implicit co: Ctx.Owner): Unit = {
    val clickcb: js.Function1[MouseEvent, Boolean] = { (event: MouseEvent) =>
      if (!clientStateRx.selectionInProgress.now) {
        println("click")

        val clickPt = getCanvasPoint(event.pageX.toInt, event.pageY.toInt)

        val req = UIRequest(clientStateRx.currentUIState.now, Click(clickPt))
        uiRequestCycle(req)
      }
      true
    }


    val elem = dom.document
      .getElementById("canvas-container")

    elem.addEventListener("click", clickcb, useCapture=false)
  }






  var clientState: Option[ClientStateRx] = None
  // var rxExprs: Option[Rx[Unit]] = None

  @JSExport
  def display(): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    val docId = param("doc").getOrElse { "" }
    val lt = param("lt").getOrElse { "" }
    val pg = param("pg").getOrElse("0").toInt
    // def createLabelerIdFromQueryParams: =
    val labelerId = DocumentLabelerIdentifier(DocumentID(docId), lt, Pagination(0, PageNum(pg), None))

    shell.createDocumentLabeler(labelerId).foreach{ uiResponse =>

      val uiState = uiResponse.uiState
      val clientStateRx =  new ClientStateRx(uiState, uiRequestCycle(_))
      clientState = Option(clientStateRx)

      // val colorMap = uiState.currentLabeler.labelColors
      // val DocumentLabelerIdentifier()
      // clientStateRx.labelTypes() = colorMap

      val selectorControls = SharedLayout.zoneSelectorControls(
        clientStateRx,
        clientStateRx.setupLabelChooser
      )

      val navContent =  SharedLayout.initNavbar(List())

      val rx0 = clientStateRx.selectionInProgress.triggerLater {
        if (clientStateRx.selectionInProgress.now) {
          for {
            bbox <- getUserSelection(fabricCanvas)
          } yield {
            uiRequestCycle(UIRequest(
              clientStateRx.currentUIState.now,
              SelectRegion(bbox)))
          }
        }
      }



      val bodyContent =
        div("container-fluid".clazz)(
          div("row".clazz, pageStyles.controlClusterStyle)(
            div("col-lg-12".clazz)(
              selectorControls,
              clientStateRx.paginator.pageControls
            )
          ),
          div("row".clazz, pageStyles.labelerBodyStyle)(
            div("col-lg-12".clazz)(
              div(^.id:="canvas-container", pageStyles.canvasContainer)(
                canvas(^.id:="canvas", pageStyles.fabricCanvasStyle)
              )
            )
          )
        )

      val sidebarContent = ul(`class`:="sidebar-nav")

      withBootstrapNative {
        SharedLayout.pageSetup(navContent, bodyContent, sidebarContent).render
      }

      setupClickCatchers(clientStateRx)

      initKeybindings()

      echoLabeler(uiResponse.changes.collect {
        case WidgetMod.AddLw(wid, Some(abs)) => abs
      })



    }

  }


}