package edu.umass.cs.iesl.watr
package watrcolors
package client
package pages

import parts._

import org.scalajs.dom.raw._

import scaladget.stylesheet.{
  all => sty
}

import scaladget.api.{
  SelectableButtons
}
import scalatags.JsDom.all._
import scalatags.JsDom.{
  TypedTag
}

import labeling._
import rx._

import scaladget.tools.JsRxTags.{ctx => _, _}

object SharedLayout extends BaseClientDefs {
  import BootstrapBits._

  import pageStyles._

  def initNavbar(
    navItems: Seq[NavEntry[_ <: HTMLElement]] = Seq()
  )(implicit rxOwnr: Ctx.Owner): HtmlTag = {
    val logo = navItem(
      span(pageStyles.logo, "WatrColors").render
    )
    val navs = logo :: navItems.toList

    navBar(
      navbarStyle,
      navs:_*
    )
  }

  def pageSetup(
    nav: TypedTag[HTMLElement],
    bodyContent: TypedTag[HTMLElement],
    sidebarContent: TypedTag[HTMLElement]
  )(implicit co: Ctx.Owner) = {
    div(
      nav,
      sidebarLayout(bodyContent, sidebarContent)
    )
  }

  def sidebarLayout(
    bodyContent: TypedTag[HTMLElement],
    sidebarContent: TypedTag[HTMLElement]
  )(implicit co: Ctx.Owner): TypedTag[HTMLElement] = {

    div("wrapper".id, "toggled".clazz)(
      div("sidebar-wrapper".id, pageStyles.sidebarStyle)(
        sidebarContent
      ),

      div("page-content-wrapper".id)(
        div("container-fluid".clazz)(
          div("row".clazz)(
            div("col-lg-12".clazz)(
              bodyContent
            )
          )
        )
      )
    )

  }


  def zoneSelectorControls(
    zsRx: ClientStateRx,
    labelSelector: RxModifier
  )(implicit ctx: Ctx.Owner): RxHtmlTag = Rx {

    val delClrMrgButtons = buttonGroup()(
      button("Merge", sty.btn_small,  () => {zsRx.doMergeZones() = true}),
      button("Delete", sty.btn_danger, () => {zsRx.doDeleteZone() = true})
    )

    val initSelectConstraint =
      List[(String, Constraint, Boolean)](
        ("Line", ByLine, true),
        ("Char", ByChar, false),
        ("Region", ByRegion, false)
      ).map{ case (s, c, b) =>
          selectableButton(s,
            defaultActive = b,
            modifierSeq = sty.btn_small,
            onclick = () => {
              ???
              // zsRx.selectionConstraint() = c
            }
          )
      }

    val selectConstraint: SelectableButtons = radios()(
      initSelectConstraint:_*
    )


    div("selection-controls".id,
      selectConstraint.render,
      labelSelector,
      delClrMrgButtons.render
    )

  }


}