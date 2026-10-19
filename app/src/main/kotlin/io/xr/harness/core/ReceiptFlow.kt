package io.xr.harness.core

import io.xr.lab.shared.models.FlowStatus

/**
 * 接样单列表查询（swift ReceiptListQuery 同构）：分页 1-based，家族约定 page=1/pageSize=20。 filter 为三态粗过滤， flowStatus
 * 为精确阶段——后端语义随 swift 侧同款。
 */
data class ReceiptListQuery(
    val page: Int = ReceiptsPaging.DEFAULT_PAGE,
    val pageSize: Int = ReceiptsPaging.DEFAULT_PAGE_SIZE,
    val keyword: String? = null,
    val flowStatus: FlowStatus? = null,
    val filter: ReceiptFilter = ReceiptFilter.All,
)

/** 三态过滤（swift ReceiptFilter 同构）：all → 不传 filter。 */
enum class ReceiptFilter(val queryValue: String?) {
  All(null),
  NotYet("not_yet"),
  Submitted("submitted"),
}

/** 分页钉值（家族约定，detekt MagicNumber 收口）。 */
object ReceiptsPaging {
  const val DEFAULT_PAGE = 1
  const val DEFAULT_PAGE_SIZE = 20
}
