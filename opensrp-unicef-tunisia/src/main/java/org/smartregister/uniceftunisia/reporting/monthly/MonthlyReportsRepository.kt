package org.smartregister.uniceftunisia.reporting.monthly

import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.sqlite.db.transaction
import org.smartregister.reporting.ReportingLibrary
import org.smartregister.reporting.domain.IndicatorTally
import org.smartregister.repository.BaseRepository
import org.smartregister.uniceftunisia.application.UnicefTunisiaApplication
import org.smartregister.uniceftunisia.reporting.ReportsDao
import org.smartregister.uniceftunisia.reporting.ReportsDao.dateFormatter
import org.smartregister.uniceftunisia.reporting.convertToNamedMonth
import org.smartregister.uniceftunisia.reporting.monthly.MonthlyReportsRepository.ColumnNames.CREATED_AT
import org.smartregister.uniceftunisia.reporting.monthly.MonthlyReportsRepository.ColumnNames.DATE_SENT
import org.smartregister.uniceftunisia.reporting.monthly.MonthlyReportsRepository.ColumnNames.EDITED
import org.smartregister.uniceftunisia.reporting.monthly.MonthlyReportsRepository.ColumnNames.INDICATOR_CODE
import org.smartregister.uniceftunisia.reporting.monthly.MonthlyReportsRepository.ColumnNames.INDICATOR_GROUPING
import org.smartregister.uniceftunisia.reporting.monthly.MonthlyReportsRepository.ColumnNames.MONTH
import org.smartregister.uniceftunisia.reporting.monthly.MonthlyReportsRepository.ColumnNames.PROVIDER_ID
import org.smartregister.uniceftunisia.reporting.monthly.MonthlyReportsRepository.ColumnNames.UPDATED_AT
import org.smartregister.uniceftunisia.reporting.monthly.MonthlyReportsRepository.ColumnNames.VALUE
import org.smartregister.uniceftunisia.reporting.monthly.domain.MonthlyTally
import java.util.*

/**
 * This the repository class that extends [BaseRepository] and has all the implementation code that
 * allows you to interact with monthly_tallies table used to store information about monthly reports.
 * [supportedReportGroups] is a set of the report section headers and must be the same as what is
 * declared in the report queries (the group headers are also mapped to strings for translation)
 */
class MonthlyReportsRepository private constructor() : BaseRepository() {

    private val username = UnicefTunisiaApplication.getInstance().context().allSharedPreferences().fetchRegisteredANM()

    private val supportedReportGroups = setOf(
            "report_group_header_vaccination_activity",
            "report_group_header_vaccine_utilization",
            "report_group_header_tetanus_protected",
            "report_group_header_rubella_vaccine",
            "report_group_header_adequate_growth_measurement",
            "report_group_header_previous_under_nutrition",
            "report_group_header_diagnosed_malnourished"
    )

    private object Constants {
        const val TABLE_NAME = "monthly_tallies"
    }

    private object ColumnNames {
        const val PROVIDER_ID = "provider_id"
        const val INDICATOR_CODE = "indicator_code"
        const val VALUE = "value"
        const val MONTH = "month"
        const val EDITED = "edited"
        const val DATE_SENT = "date_sent"
        const val INDICATOR_GROUPING = "indicator_grouping"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
    }

    private object TableQueries {
        const val CREATE_TABLE_SQL = """
            CREATE TABLE monthly_tallies
            (
                _id                INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
                indicator_code     VARCHAR   NOT NULL,
                provider_id        VARCHAR   NOT NULL,
                value              VARCHAR   NOT NULL,
                month              VARCHAR   NOT NULL,
                edited             INTEGER   NOT NULL DEFAULT 0,
                indicator_grouping TEXT,
                date_sent          DATETIME  NULL,
                created_at         DATETIME  NULL,
                updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
        """
        const val CREATE_PROVIDER_ID_INDEX_SQL =
                "CREATE INDEX monthly_tallies_provider_id_index ON monthly_tallies (provider_id COLLATE NOCASE);"
        const val CREATE_DATE_SENT_INDEX_SQL =
                "CREATE INDEX monthly_tallies_date_sent_index ON monthly_tallies(date_sent);"
        const val CREATE_INDICATOR_CODE_INDEX_SQL =
                "CREATE INDEX monthly_tallies_indicator_code_index ON monthly_tallies (indicator_code COLLATE NOCASE);"
        const val CREATE_MONTH_INDEX_SQL =
                "CREATE INDEX monthly_tallies_month_index ON monthly_tallies(month);"
        const val CREATE_INDICATOR_GROUPING_INDEX_SQL =
                "CREATE INDEX monthly_tallies_indicator_grouping_index ON monthly_tallies(indicator_grouping);"
        const val CREATE_INDICATOR_CODE_MONTH_INDEX_SQL =
                "CREATE UNIQUE INDEX monthly_tallies_indicator_code_month_index ON monthly_tallies (indicator_code, month);"
    }

    fun createTable(database: net.sqlcipher.database.SQLiteDatabase) {
        database.run {
            execSQL(TableQueries.CREATE_TABLE_SQL)
            execSQL(TableQueries.CREATE_PROVIDER_ID_INDEX_SQL)
            execSQL(TableQueries.CREATE_DATE_SENT_INDEX_SQL)
            execSQL(TableQueries.CREATE_INDICATOR_CODE_INDEX_SQL)
            execSQL(TableQueries.CREATE_MONTH_INDEX_SQL)
            execSQL(TableQueries.CREATE_INDICATOR_GROUPING_INDEX_SQL)
            execSQL(TableQueries.CREATE_INDICATOR_CODE_MONTH_INDEX_SQL)
        }
    }

    fun fetchUnDraftedMonths() = ReportsDao.getDistinctReportMonths()
            .subtract(fetchDraftedMonths().map { it.first.convertToNamedMonth(true) })
            .subtract(ReportsDao.getSentReportMonths().map { it.first.convertToNamedMonth(true) })
            .toList()

    fun fetchDraftedMonths() = ReportsDao.getDraftedMonths()

    fun fetchDraftedReportTalliesByMonth(yearMonth: String): List<MonthlyTally> {
        val draftReports = ReportsDao.getReportsByMonth(yearMonth = yearMonth)
        if (draftReports.isNotEmpty()) return draftReports

        val monthlyTallies = arrayListOf<MonthlyTally>()
        supportedReportGroups.forEach { reportHeader ->
            ReportingLibrary.getInstance().dailyIndicatorCountRepository()
                    .findTalliesInMonth(dateFormatter().parse(yearMonth)!!, reportHeader).run {
                        values.forEach { currentList ->
                            if (currentList.isNotEmpty())
                                computeDailyTallies(currentList)?.let { monthlyTallies.add(it) }
                        }
                    }
        }
        return monthlyTallies
    }

    private fun computeDailyTallies(dailyTallies: List<IndicatorTally>) =
            if (dailyTallies.isNotEmpty()) {
                MonthlyTally(
                        indicator = dailyTallies[0].indicatorCode,
                        grouping = dailyTallies[0].grouping,
                        updatedAt = Calendar.getInstance().time,
                        providerId = username,
                        value = dailyTallies.map { it.floatCount.toInt() }.reduce { sum, element -> sum + element }.toString()
                )
            } else null

    fun saveMonthlyDraft(monthlyTallies: Map<String, MonthlyTally>?, yearMonth: String?, sync: Boolean): Boolean {
        if (!monthlyTallies.isNullOrEmpty() && !yearMonth.isNullOrBlank()) {
            monthlyTallies.values.forEach { tally ->
                writableDatabase.transaction(exclusive = true) {
                    val currentTime = Calendar.getInstance().timeInMillis
                    val contentValues = contentValuesOf(
                            Pair(INDICATOR_CODE, tally.indicator),
                            Pair(INDICATOR_GROUPING, tally.grouping),
                            Pair(VALUE, tally.value),
                            Pair(CREATED_AT, if (sync && tally.createdAt != null) tally.createdAt!!.time else currentTime),
                            Pair(UPDATED_AT, if (sync && tally.updatedAt != null) tally.updatedAt!!.time else currentTime),
                            Pair(PROVIDER_ID, tally.providerId),
                            Pair(EDITED, 1),
                            Pair(MONTH, yearMonth),
                            Pair(DATE_SENT, if (sync) currentTime else null)
                    )
                    writableDatabase.insertWithOnConflict(Constants.TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            return true
        }
        return false
    }

    /**
     * Fetch all sent report months reports and group them by Year
     */
    fun fetchSentReportMonths(): Map<String, List<MonthlyTally>> =
            ReportsDao.getAllSentReportMonths().groupBy { dateFormatter("yyyy").format(it.month) }

    /**
     * Fetch all sent report tallies for the [yearMonth]
     */
    fun fetchSentReportTalliesByMonth(yearMonth: String) =
            ReportsDao.getReportsByMonth(yearMonth = yearMonth, drafted = false)

    companion object {
        @Volatile
        private var instance: MonthlyReportsRepository? = null

        @JvmStatic
        fun getInstance(): MonthlyReportsRepository = instance ?: synchronized(this) {
            MonthlyReportsRepository().also { instance = it }
        }
    }
}