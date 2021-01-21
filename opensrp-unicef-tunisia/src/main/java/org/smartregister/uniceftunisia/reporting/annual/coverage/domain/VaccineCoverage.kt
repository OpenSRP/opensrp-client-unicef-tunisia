package org.smartregister.uniceftunisia.reporting.annual.coverage.domain

import org.smartregister.uniceftunisia.R
import org.smartregister.uniceftunisia.reporting.common.ReportingUtils.dateFormatter
import java.util.*

data class VaccineCoverage(
        val vaccine: String,
        val vaccinated: String,
        val coverage: String,
        val year: String = dateFormatter("yyyy").format(Date()),
        var coverageColorResource: Int = R.color.black_text_color,
        var name: String = "",
        var target: Int = 0
)