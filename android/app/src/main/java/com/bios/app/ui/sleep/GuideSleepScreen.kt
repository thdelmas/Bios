package com.bios.app.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Static evidence-anchored sleep-medicine reference (#303 / #135
 * deliverable 3). Read-only, owner-pulled, no behavioural mechanics
 * — same precedent as
 * [com.bios.app.ui.reference.LongevityReferenceScreen] and
 * [com.bios.app.ui.biomarkers.BiomarkerReferenceRanges].
 *
 * Reachable from [SleepDashboardScreen] via the "Learn more" link.
 * The owner reads the published-research basics, grounds the
 * numbers they see in the dashboard, and decides what (if anything)
 * to act on. Bios renders; the owner reads.
 *
 * ## Manifesto guards baked into the content
 *
 *  - No score (component metrics + this reference is enough).
 *  - No products, supplements, protocol-of-the-month framing.
 *  - No streaks / achievements / gamification.
 *  - No bedtime nudges or wake reminders — Android's alarm app
 *    owns that surface.
 *  - Honest scope: what Bios measures, what it does NOT measure,
 *    and where a sleep clinic remains the right next step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideSleepScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { IntroParagraph() }
            item { WhyRegularityMattersCard() }
            item { SleepEnvironmentCard() }
            item { BehaviouralBasicsCard() }
            item { WhatBiosMeasuresCard() }
            item { WhatBiosDoesNotDoCard() }
            item { ReferencesCard() }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun IntroParagraph() {
    Text(
        "What the sleep-medicine literature says — and what Bios deliberately leaves out. " +
            "Pull-side reference only. No targets, no protocols, no nudges.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WhyRegularityMattersCard() {
    GuideCard(
        title = "Why regularity matters",
        body = "Researchers have converged in recent years on regularity — the " +
            "day-to-day consistency of when you sleep — as a stronger predictor of " +
            "long-term health outcomes than absolute duration. Phillips et al. (2017) " +
            "introduced the Sleep Regularity Index and showed measurable links between " +
            "low regularity and cardiometabolic markers. Windred et al. (2024) extended " +
            "the finding to all-cause mortality in a large UK Biobank actigraphy " +
            "cohort — irregular sleep timing was a stronger mortality predictor than " +
            "short sleep duration.\n\n" +
            "This is the load-bearing argument for the SLEEP_REGULARITY metric Bios " +
            "computes nightly: midpoint variance across your last week is the single " +
            "number the literature points to most often.",
    )
}

@Composable
private fun SleepEnvironmentCard() {
    GuideCard(
        title = "Sleep environment",
        body = "The published recommendations converge on three controllables: dark, " +
            "cool, quiet.\n\n" +
            "• Dark: even dim ambient light during sleep is associated with elevated " +
            "nocturnal cortisol and reduced melatonin (Cho et al. 2015). AASM " +
            "environmental guidance targets near-zero lux during sleep.\n\n" +
            "• Cool: core body temperature drops 1–2°C during sleep; bedrooms above " +
            "~24°C interfere with that thermal decline (Okamoto-Mizuno & Mizuno 2012). " +
            "The literature consensus for the bedroom is 16–19°C (60–67°F).\n\n" +
            "• Quiet: WHO Environmental Noise Guidelines for the European Region " +
            "(2018) recommend night-noise levels below 40 dB averaged across the " +
            "sleep period for cardiovascular protection.",
    )
}

@Composable
private fun BehaviouralBasicsCard() {
    GuideCard(
        title = "Behavioural basics",
        body = "• Consistent bedtime. Directly addresses the regularity signal — the " +
            "larger your bedtime variance, the lower your Sleep Regularity Index.\n\n" +
            "• Screens before sleep. Blue-light suppression of melatonin is real but " +
            "modest; the larger effect Chang et al. (2015) measured was cognitive " +
            "arousal from content interacting with sleep onset.\n\n" +
            "• Caffeine timing. Half-life is 4–6 hours typical but the inter-" +
            "individual range is wide (2–10 h via CYP1A2 variants). Drake et al. " +
            "(2013) showed 400 mg taken 6 hours before bed measurably reduced " +
            "total sleep time vs placebo.\n\n" +
            "• Alcohol. Facilitates sleep onset but suppresses REM and fragments the " +
            "second half of the night. Even one drink within ~3 hours of bedtime " +
            "is detectable in PSG-measured sleep architecture (Roehrs & Roth 2001).",
    )
}

@Composable
private fun WhatBiosMeasuresCard() {
    GuideCard(
        title = "What Bios measures — and what it doesn't",
        body = "Bios estimates sleep duration, stage when available, and regularity " +
            "from one or more of: a paired wearable (Oura, Whoop, Garmin, Polar, or " +
            "any Health-Connect-published sleep session); your manual diary entry; " +
            "or — when no wearable is paired — your phone's accelerometer + display " +
            "state, processed through a Cole-Kripke 7-tap classifier with Webster " +
            "rescoring rules (the published 1992 actigraphy baseline), individually " +
            "calibrated to your own quiet-hour movement distribution.\n\n" +
            "Honest accuracy framing:\n\n" +
            "• Wearables: typically ~5–10 min RMSE on total sleep time vs " +
            "polysomnography, depending on device class.\n\n" +
            "• Phone-only: typically wider RMSE — surfaced with LOW confidence so the " +
            "baseline engine weights it appropriately.\n\n" +
            "• Polysomnography (PSG): multi-channel EEG + EOG + EMG + respiratory + " +
            "SpO2 — the clinical gold standard. Bios cannot match this. No consumer " +
            "device can.\n\n" +
            "If your questions are clinical (suspected apnea, narcolepsy, REM " +
            "behaviour disorder, parasomnia), the right next step is a sleep clinic, " +
            "not a more expensive wearable.",
    )
}

@Composable
private fun WhatBiosDoesNotDoCard() {
    GuideCard(
        title = "What Bios deliberately does not do",
        body = "• No sleep score. A composite metric summarising \"how well you're " +
            "doing\" at sleep invites judgement; Bios surfaces the component metrics " +
            "and lets you read them yourself.\n\n" +
            "• No bedtime nudges or wake reminders. Android's alarm app already " +
            "handles that surface well.\n\n" +
            "• No streaks, achievements, or gamification. Sleep quality fluctuates " +
            "with life — gamifying it adds anxiety, not insight.\n\n" +
            "• No protocol-of-the-month or supplement framing. Bios is not a " +
            "marketplace.\n\n" +
            "This restraint is the design intent, not an oversight. The consumer-sleep " +
            "app contrast — Don't Die, Whoop, Oura — all ship some combination of the " +
            "above. Bios is the instrument; the owner reads it.",
    )
}

@Composable
private fun ReferencesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "References",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            REFERENCES.forEach { ref ->
                Text(
                    ref,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GuideCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private val REFERENCES = listOf(
    "Phillips AJK et al. (2017) — Irregular sleep/wake patterns are " +
        "associated with poorer academic performance and delayed circadian and " +
        "sleep/wake timing. Scientific Reports 7:3216.",
    "Windred DP et al. (2024) — Sleep regularity is a stronger predictor of " +
        "mortality risk than sleep duration. Sleep 47(1).",
    "Cho CH et al. (2015) — Effects of artificial light at night on melatonin " +
        "secretion and cardiovascular regulation. Chronobiology International " +
        "32(9):1294–1310.",
    "Okamoto-Mizuno K, Mizuno K (2012) — Effects of thermal environment on " +
        "sleep and circadian rhythm. Journal of Physiological Anthropology 31:14.",
    "WHO Regional Office for Europe (2018) — Environmental Noise Guidelines " +
        "for the European Region.",
    "Chang AM et al. (2015) — Evening use of light-emitting eReaders " +
        "negatively affects sleep, circadian timing, and next-morning alertness. " +
        "PNAS 112(4):1232–1237.",
    "Drake C et al. (2013) — Caffeine effects on sleep taken 0, 3, or 6 hours " +
        "before going to bed. Journal of Clinical Sleep Medicine 9(11):1195–1200.",
    "Roehrs T, Roth T (2001) — Sleep, sleepiness, sleep disorders and alcohol " +
        "use and abuse. Sleep Medicine Reviews 5(4):287–297.",
    "Cole RJ et al. (1992) — Automatic sleep/wake identification from wrist " +
        "activity. Sleep 15(5):461–469.",
    "Walker M (2017) — Why We Sleep: Unlocking the Power of Sleep and Dreams. " +
        "Scribner.",
)
