package edu.umass.cs.iesl.watr
package labeling

import watrmarks._

import utils.Color
import utils.Colors

sealed trait WidgetMod

object WidgetMod {

  case class AddLw(id: Int@@WidgetID, abs: Option[AbsPosWidget]=None) extends WidgetMod
  case class RmLw(id: Int@@WidgetID, abs: Option[AbsPosWidget]=None) extends WidgetMod
  case class ClearAllLw() extends WidgetMod

}


case class UIState(
  selectionConstraint: Constraint,
  selectedLabel: Option[Label],
  selections: Seq[Int@@ZoneID], // TODO generalize Id type (has to be able to serialize through strings)
  currentLabeler: LabelerIdentifier
)

case class UIRequest(
  uiState: UIState,
  gesture: Gesture
)

case class UIResponse(
  uiState: UIState,
  changes: List[WidgetMod]
)

case class PaginationInfo(
  pageNum: Int@@PageNum,
  labelCount: Int
)

case class Pagination(
  pageCount: Int,
  currentPage: Int@@PageNum,
  pageInfo: Option[Seq[PaginationInfo]]
)

sealed trait LabelerIdentifier {
  def labelColors: Map[Label, Color]
}

case object NilLabelerIdentifier extends LabelerIdentifier {
  def labelColors: Map[Label, Color] = Map().withDefaultValue(Colors.Black)
}

case class DocumentLabelerIdentifier(
  stableId: String@@DocumentID,
  labelerType: String,
  pagination: Pagination,
  labelColors: Map[Label, Color] = Map().withDefaultValue(Colors.Black)
) extends LabelerIdentifier

