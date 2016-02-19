package edu.umass.cs.iesl.watr
package watrcolors

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.annotation.JSExport


import autowire._
import boopickle.DefaultBasic._
import Picklers._

@JSExport
class CorpusExplorerView() extends ClientView {

  val server = ServerWire("explorer")[CorpusExplorerApi]

  def createView(): Unit = {
    server.createView().call().foreach{ update =>
      applyHtmlUpdates(update)
    }
  }

  def navNext(e: MousetrapEvent): Boolean = {
    server.navNext().call().foreach(applyHtmlUpdates(_))
    true
  }

  def navNext(): Boolean = {
    server.navNext().call().foreach(applyHtmlUpdates(_))
    true
  }

  def navPrev(): Boolean = {
    server.navPrev().call() foreach (applyHtmlUpdates(_))
    true
  }

  def openFocus(): Boolean = {
    server.getFileInFocus().call().foreach { currfile =>
      WatrColorClient.switchViews(new SvgOverview(currfile))
    }
    server.openFocus().call() foreach (applyHtmlUpdates(_))

    true
  }

  override val initKeys = Keybindings(List(
    "j" -> ((e: MousetrapEvent) => navNext),
    "k" -> ((e: MousetrapEvent) => navPrev),
    "x" -> ((e: MousetrapEvent) => openFocus)
  ))

}

