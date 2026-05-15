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
        assertEquals(BiomarkerBand.CONCERNING, bands.classify(3.0))
        assertEquals(BiomarkerBand.CONCERNING, bands.classify(10.0))
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
        assertEquals(BiomarkerBand.CONCERNING, bands.classify(3.0))
        assertEquals(BiomarkerBand.CONCERNING, bands.classify(10.0))
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
        assertEquals(BiomarkerBand.CONCERNING, bands.classify(6.5))
        assertEquals(BiomarkerBand.CONCERNING, bands.classify(9.0))
    }

    // -- Region coverage --

    @Test
    fun every_supported_region_carries_the_universal_biomarker_bands() {
        val expected = setOf(
            MetricType.HSCRP, MetricType.HBA1C,
            MetricType.TOTAL_CHOLESTEROL, MetricType.LDL_CHOLESTEROL,
            MetricType.HDL_CHOLESTEROL, MetricType.TRIGLYCERIDES,
            MetricType.APO_B,
        )
        for (regionCode in RegionConfigProvider.supportedRegions()) {
            val bands = RegionConfigProvider.forRegion(regionCode).clinicalThresholds.biomarkerBands
            for (key in expected) {
                assertNotNull("$regionCode should ship ${key.key} bands", bands[key])
            }
        }
    }

    // -- BELOW-direction classification (HDL is the inverse case) --

    @Test
    fun classify_returns_NORMAL_above_borderline_ceiling_when_direction_is_BELOW() {
        val bands = BiomarkerBands(
            normalCeiling = 40.0, borderlineCeiling = 60.0,
            concerningDirection = com.bios.app.config.BandDirection.BELOW,
        )
        // High HDL is protective.
        assertEquals(BiomarkerBand.NORMAL, bands.classify(70.0))
        assertEquals(BiomarkerBand.NORMAL, bands.classify(60.0))
    }

    @Test
    fun classify_returns_BORDERLINE_inside_the_window_when_direction_is_BELOW() {
        val bands = BiomarkerBands(
            normalCeiling = 40.0, borderlineCeiling = 60.0,
            concerningDirection = com.bios.app.config.BandDirection.BELOW,
        )
        assertEquals(BiomarkerBand.BORDERLINE, bands.classify(40.0))
        assertEquals(BiomarkerBand.BORDERLINE, bands.classify(50.0))
        assertEquals(BiomarkerBand.BORDERLINE, bands.classify(59.999))
    }

    @Test
    fun classify_returns_CONCERNING_below_normal_ceiling_when_direction_is_BELOW() {
        val bands = BiomarkerBands(
            normalCeiling = 40.0, borderlineCeiling = 60.0,
            concerningDirection = com.bios.app.config.BandDirection.BELOW,
        )
        // Low HDL is the cardiovascular concern.
        assertEquals(BiomarkerBand.CONCERNING, bands.classify(39.999))
        assertEquals(BiomarkerBand.CONCERNING, bands.classify(25.0))
    }

    // -- Lipid panel literature thresholds --

    @Test
    fun lipid_panel_bands_match_NCEP_ATP_III_thresholds() {
        val bands = RegionConfigProvider.forRegion("US").clinicalThresholds.biomarkerBands

        // Total cholesterol: <200 desirable, 200-239 borderline, ≥240 high.
        val tc = bands[MetricType.TOTAL_CHOLESTEROL]!!
        assertEquals(BiomarkerBand.NORMAL, tc.classify(180.0))
        assertEquals(BiomarkerBand.BORDERLINE, tc.classify(200.0))
        assertEquals(BiomarkerBand.CONCERNING, tc.classify(240.0))

        // LDL: <100 optimal, 100-160 borderline, ≥160 high.
        val ldl = bands[MetricType.LDL_CHOLESTEROL]!!
        assertEquals(BiomarkerBand.NORMAL, ldl.classify(95.0))
        assertEquals(BiomarkerBand.BORDERLINE, ldl.classify(100.0))
        assertEquals(BiomarkerBand.CONCERNING, ldl.classify(160.0))

        // Triglycerides: <150 normal, 150-200 borderline, ≥200 high.
        val tg = bands[MetricType.TRIGLYCERIDES]!!
        assertEquals(BiomarkerBand.NORMAL, tg.classify(140.0))
        assertEquals(BiomarkerBand.BORDERLINE, tg.classify(150.0))
        assertEquals(BiomarkerBand.CONCERNING, tg.classify(200.0))
    }

    @Test
    fun hdl_band_inverts_so_low_values_are_concerning() {
        val hdl = RegionConfigProvider.forRegion("US")
            .clinicalThresholds.biomarkerBands[MetricType.HDL_CHOLESTEROL]!!
        assertEquals(BiomarkerBand.CONCERNING, hdl.classify(35.0))
        assertEquals(BiomarkerBand.BORDERLINE, hdl.classify(40.0))
        assertEquals(BiomarkerBand.BORDERLINE, hdl.classify(55.0))
        assertEquals(BiomarkerBand.NORMAL, hdl.classify(60.0))
        assertEquals(BiomarkerBand.NORMAL, hdl.classify(75.0))
    }

    @Test
    fun apob_band_uses_Sniderman_2019_thresholds() {
        val apob = RegionConfigProvider.forRegion("US")
            .clinicalThresholds.biomarkerBands[MetricType.APO_B]!!
        assertEquals(BiomarkerBand.NORMAL, apob.classify(80.0))
        assertEquals(BiomarkerBand.BORDERLINE, apob.classify(90.0))
        assertEquals(BiomarkerBand.CONCERNING, apob.classify(120.0))
    }
}
