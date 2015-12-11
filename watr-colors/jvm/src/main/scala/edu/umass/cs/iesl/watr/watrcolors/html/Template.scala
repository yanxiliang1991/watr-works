package edu.umass.cs.iesl.watr
package watrcolors
package html

object Template{
  import scalatags.Text.all._
  import scalatags.Text.tags2.title
  val txt =
    "<!DOCTYPE html>" +
      html(
        head(
          title("Example Scala.js application"),
          meta(httpEquiv:="Content-Type", content:="text/html; charset=UTF-8"),
          script(`type`:="text/javascript", src:="/watrcolors-client-fastopt.js"),
          script(`type`:="text/javascript", src:="//localhost:12345/workbench.js"),
          script(`type`:="text/javascript", src:="/webjars/mousetrap/1.5.3/mousetrap.min.js"),
          link(rel:="stylesheet", `type`:="text/css", href:="/webjars/bootstrap/3.3.6/css/bootstrap.min.css")
        ),


        body(margin:=0)(
          p(" what the hell .. the starting page"),

          script("edu.umass.cs.iesl.watr.watrcolors.WatrColorClient().main()")
        )
      )
}
