package com.github.tomasbjerre.wisp.ui.splits

import com.github.tomasbjerre.wisp.util.GeoUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/** Verifies specs/ui-flows.md#4-km-splits. */
class KmSplitRowsTest {
    @Test
    fun `one row per complete km, numbered from 1, then the partial km`() {
        val splits = GeoUtils.KmSplits(listOf(300L, 250L), GeoUtils.PartialSplit(470.0, 150L))

        val rows = KmSplitRows.rows(splits)

        // Decimal separator follows the device locale, like every other distance in the app.
        assertThat(rows.map { it.label }).containsExactly("1", "2", "+" + "%.2f".format(0.47))
        assertThat(rows.map { it.durationSeconds }).containsExactly(300L, 250L, 150L)
        assertThat(rows.map { it.isPartial }).containsExactly(false, false, true)
    }

    @Test
    fun `a row's speed is its own distance over its own time`() {
        val splits = GeoUtils.KmSplits(listOf(250L), GeoUtils.PartialSplit(500.0, 200L))

        val rows = KmSplitRows.rows(splits)

        assertThat(rows[0].speedMps).isCloseTo(4.0, within(0.001))
        assertThat(rows[1].speedMps).isCloseTo(2.5, within(0.001))
    }

    @Test
    fun `bars are relative to the fastest row, which gets a full bar`() {
        val splits = GeoUtils.KmSplits(listOf(400L, 200L), null)

        val rows = KmSplitRows.rows(splits)

        assertThat(rows[1].relativeSpeed).isEqualTo(1f)
        assertThat(rows[0].relativeSpeed).isCloseTo(0.5f, within(0.001f))
    }

    @Test
    fun `no splits at all is no rows`() {
        assertThat(KmSplitRows.rows(GeoUtils.KmSplits(emptyList(), null))).isEmpty()
    }

    @Test
    fun `fastest and slowest are picked among complete km only`() {
        // The partial km's pace (100s for 500m = 200s/km) would beat every full km — it
        // must not be reported as the fastest.
        val splits = GeoUtils.KmSplits(listOf(310L, 290L, 330L), GeoUtils.PartialSplit(500.0, 100L))

        val extremes = KmSplitRows.extremes(splits)

        assertThat(extremes).isEqualTo(
            KmSplitExtremes(fastestKm = 2, fastestSeconds = 290L, slowestKm = 3, slowestSeconds = 330L),
        )
    }

    @Test
    fun `fewer than two complete km has nothing to compare`() {
        assertThat(KmSplitRows.extremes(GeoUtils.KmSplits(listOf(300L), GeoUtils.PartialSplit(500.0, 100L)))).isNull()
    }
}
