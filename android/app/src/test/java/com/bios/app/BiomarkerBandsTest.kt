package com.bios.app

import com.bios.app.config.BiomarkerBand
import com.bios.app.config.BiomarkerBands
import com.bios.app.config.RegionConfigProvider
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Guards the clinical-band classification for biomarker readings and the
 * region-config population the longevity screen depends on.
 */
class BiomarkerBandsTest {

    // -- classify() boundary behaviour --

    @Test
    fun classify_returns_NORMAL_below_the_normal_ceiling() {
        val bands = BiomarkerBands(normalCeiling = 1.0, borderlineCeiling = 3.0)
        assertEquals(BiomarkerBand.NORMAL, bands.classify(0.5))
        assertEquals(BiomarkerBand.NORMAL, bands.classify(0.999))
    }

    @Test
    fun classify_returns_BORDERLINE_at_or_above_normal_ceiling_and_below_borderline_ceiling() {
        // Inclusive at the lower edge so values on the cut-off slot into the
        // higher-risk band by clinical convention.
        val bands = BiomarkerBands(normalCeiling = 1.0, borderlineCeiling = 3.0)
        assertEquals(BiomarkerBand.BORDERLINE, bands.classify(1.0))
        assertEquals(BiomarkerBand.BORDERLINE, bands.classify(2.5))
        assertEquals(BiomarkerBand.BORDERLINE, bands.classify(2.999))
    }

    @Test
    fun classify_returns_HIGH_at_or_above_borderline_ceiling() {
        val bands = BiomarkerBands(normalCeiling = 1.0, borderlineCeiling = 3.0)
        assertEquals(BiomarkerBand.HIGH, bands.classify(3.0))
        assertEquals(BiomarkerBand.HIGH, bands.classify(10.0))
    }

    @Test
    fun ceilings_must_be_monotonic() {
        assertThrows(IllegalArgumentException::class.java) {
            BiomarkerBands(normalCeiling = 3.0, borderlineCeiling = 1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BiomarkerBands(normalCeiling = 1.0, borderlineCeiling = 1.0)
        }
    }

    // -- hsCRP literature thresholds --

    @Test
    fun hscrp_bands_match_Ridker_2003_thresholds() {
        val bands = RegionConfigProvider.forRegion("US")
            .clinicalThresholds.biomarkerBands[MetricType.HSCRP]
        assertNotNull("hsCRP bands should be populated for US", bands)
        // Ridker AHA/CDC 2003: <1.0 low, 1.0-3.0 moderate, ≥3.0 high.
        assertEquals(BiomarkerBand.NORMAL, bands!!.classify(0.5))
        assertEquals(BiomarkerBand.BORDERLINE, bands.classify(1.0))
        assertEquals(BiomarkerBand.BORDERLINE, bands.classify(2.5))
        assertEquals(BiomarkerBand.HIGH, bands.classify(3.0))
        assertEquals(BiomarkerBand.HIGH, bands.classify(10.0))
    }

    // -- HbA1c literature thresholds --

    @Test
    fun hba1c_bands_match_ADA_2024_thresholds() {
        val bands = RegionConfigProvider.forRegion("US")
            .clinicalThresholds.biomarkerBands[MetricType.HBA1C]
        assertNotNull("HbA1c bands should be populated for US", bands)
        // ADA 2024: <5.7 normal, 5.7-6.5 prediabetic, ≥6.5 diabetic.
        assertEquals(BiomarkerBand.NORMAL, bands!!.classify(5.0))
        assertEquals(BiomarkerBand.BORDERLINE, bands.classify(5.7))
        assertEquals(BiomarkerBand.BORDERLINE, bands.classify(6.4))
        assertEquals(BiomarkerBand.HIGH, bands.classify(6.5))
        assertEquals(BiomarkerBand.HIGH, bands.classify(9.0))
    }

    // -- Region coverage --

    @Test
    fun every_supported_region_carries_the_universal_biomarker_bands() {
        for (regionCode in RegionConfigProvider.supportedRegions()) {
            val bands = RegionConfigProvider.forRegion(regionCode).clinicalThresholds.biomarkerBands
            assertNotNull(
                "$regionCode should ship hsCRP bands",
                bands[MetricType.HSCRP]
            )
            assertNotNull(
                "$regionCode should ship HbA1c bands",
                bands[MetricType.HBA1C]
            )
        }
    }
}
