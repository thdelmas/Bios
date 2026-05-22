package com.bios.app.data

import com.bios.app.model.MedicationAnnotation

/**
 * Pull-side flagging extensions for [MedicationAnnotation]. Issue #194 —
 * the cardiology audit (CARDIOLOGY_POV §2.7 — CredibleMeds) and the
 * geriatrics audit (GERIATRICS_PALLIATIVE_POV §2.4 — AGS Beers 2023) both
 * ask the medication-annotation surface to *recognise* substance classes,
 * not to nudge the owner.
 *
 * Matching uses the owner's [MedicationAnnotation.name] field (the
 * canonical display string) and falls back to nothing if the name is
 * blank. This deliberately covers both vocabulary-coded entries
 * ([SubstanceSource.RXNORM] etc.) and the legacy free-text path —
 * "metoprolol", "Sotalol 80mg BID", and "amiodarone (Cordarone)" all
 * match without requiring the owner to have selected a vocabulary.
 *
 * Traditional / functional-medicine substances ([SubstanceSource.AYUSH_ICD11],
 * [SubstanceSource.TCM_FORMULA_CODE], [SubstanceSource.TSUMURA_NHI_148],
 * [SubstanceSource.WHO_BOTANICAL_MONOGRAPH], etc.) are **not** classified
 * against CredibleMeds or Beers by default. Bios's stance: the empirical
 * QT-prolongation and geriatric-avoid evidence base for the substances
 * on those lists is allopathic-pharma-centric; flagging an Ayurvedic
 * dravya against the AGS Beers list would be inappropriate without
 * matching evidence. Owners can still record them; Bios just doesn't
 * pretend it knows.
 */

/**
 * True when this medication's name matches any token in the CredibleMeds
 * "Known Risk of Torsades de Pointes" subset.
 *
 * Used by the cardiology audit's pull-side QT-context surface to render
 * "Annotated medications include N QT-prolonging agent(s)" when the
 * owner annotates two or more flagged drugs simultaneously.
 */
fun MedicationAnnotation.isCredibleMedsQtProlonging(): Boolean =
    CredibleMedsList.matches(name)

/**
 * True when this medication is on the AGS Beers 2023 "Potentially
 * Inappropriate Medications" list for an adult of [age] years with the
 * given [comorbidities].
 *
 * @param age owner's age in whole years. Beers keys on ≥65 by default;
 *   the threshold is configurable via [BeersCriteriaList.matches].
 * @param comorbidities owner-annotated comorbidities the Beers entry can
 *   key on (CKD, heart failure, dementia, falls history, peptic ulcer
 *   disease). Default empty — unconditional Beers entries still match.
 *
 * Returns false when [age] is below the Beers threshold, so a single
 * call works for the whole adult population.
 */
fun MedicationAnnotation.isBeersAvoidIn(
    age: Int,
    comorbidities: Set<Comorbidity> = emptySet(),
): Boolean = BeersCriteriaList.matches(name, age, comorbidities)
