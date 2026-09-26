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
    fun `each row carries its own steps, the partial km included`() {
        val splits =
            GeoUtils.KmSplits(
                completeSeconds = listOf(300L, 250L),
                partial = GeoUtils.PartialSplit(470.0, 150L, steps = 560L),
                completeSteps = listOf(1_210L, 1_150L),
            )

        assertThat(KmSplitRows.rows(splits).map { it.steps }).containsExactly(1_210L, 1_150L, 560L)
    }

    @Test
    fun `no steps per split leaves every row's steps empty`() {
        val splits = GeoUtils.KmSplits(listOf(300L, 250L), GeoUtils.PartialSplit(470.0, 150L))

        assertThat(KmSplitRows.rows(splits).map { it.steps }).containsOnlyNulls()
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

    @Test
    fun `fastest seconds is the lowest complete km duration`() {
        assertThat(KmSplitRows.fastestSeconds(listOf(310L, 290L, 330L))).isEqualTo(290L)
    }

    @Test
    fun `fastest seconds is null with fewer than two complete km`() {
        assertThat(KmSplitRows.fastestSeconds(listOf(300L))).isNull()
        assertThat(KmSplitRows.fastestSeconds(emptyList())).isNull()
    }

    @Test
    fun `average seconds rounds the mean of every complete km`() {
        assertThat(KmSplitRows.averageSeconds(listOf(300L, 250L, 260L))).isEqualTo(270L)
    }

    @Test
    fun `average seconds is null with no complete km at all`() {
        assertThat(KmSplitRows.averageSeconds(emptyList())).isNull()
    }
}
