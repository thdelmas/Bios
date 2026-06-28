package com.bios.app.labocr

import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import java.text.Normalizer

/**
 * Multilingual analyte-name → [MetricType] resolution for lab-report OCR.
 *
 * **Anti-drift design (critical).** [MetricType] carries no synonyms, and a
 * biomarker's English `key` (`tsh`, `total_cholesterol`) will never match a
 * Catalan/Spanish report (`Tirotropina`, `Colesterol total`). So aliases are
 * the one genuinely new data artifact this feature needs — but the resolver
 * [aliasToMetricType] is *derived by scanning [MetricType.entries]* through
 * [biomarkerAliases], exactly as `FhirImporter.loincToMetricType` is built by
 * scanning entries through `loincCode`. A hand-maintained `Map<String,
 * MetricType>` kept *parallel* to the enum silently drifts the day a new
 * biomarker key lands without an alias — the same dual-source drift the LOINC
 * map was deliberately built to avoid.
 *
 * `LabAnalyteAliasesTest` asserts every `BIOMARKER` + `allowsManualEntry`
 * entry has ≥1 alias, so adding a biomarker key without an alias fails CI.
 *
 * Aliases ship for the owner's locales (ES / CA / EN). Broader language
 * coverage is the eventual home of the `RegionConfigProvider` layer (v0.4).
 *
 * See docs/LAB_OCR_INGESTION.md §4.3.
 */
object LabAnalyteAliases {

    /** Resolver derived FROM the per-metric alias lists. Built once, lazily. */
    val aliasToMetricType: Map<String, MetricType> by lazy {
        buildMap {
            for (type in MetricType.entries) {
                for (alias in biomarkerAliases(type).orEmpty()) {
                    // First writer wins: alias lists are authored collision-free
                    // (LabAnalyteAliasesTest guards this), so a clash is a bug.
                    putIfAbsent(normalise(alias), type)
                }
            }
        }
    }

    /** Resolution of a report line's leading text to a biomarker. */
    data class AnalyteMatch(val metric: MetricType, val exact: Boolean)

    /**
     * Resolve [leadingText] (the words before the first numeric token) to a
     * biomarker. An exact normalised hit is [AnalyteMatch.exact] = true; a
     * prefix/partial overlap resolves with exact = false so the caller can
     * drop the row to LOW confidence.
     */
    fun resolve(leadingText: String): AnalyteMatch? {
        val norm = normalise(leadingText)
        if (norm.length < 2) return null
        aliasToMetricType[norm]?.let { return AnalyteMatch(it, exact = true) }
        // Fuzzy: the printed label carries a trailing qualifier OCR kept
        // ("colesterol total seric"), or a leading code was dropped. Accept
        // the longest alias the line starts with, min 4 chars to avoid noise.
        val fuzzy = aliasToMetricType.entries
            .filter { it.key.length >= 4 && norm.startsWith(it.key) }
            .maxByOrNull { it.key.length }
        return fuzzy?.let { AnalyteMatch(it.value, exact = false) }
    }

    /**
     * Lowercase, strip diacritics, fold `%` to a `percent` token (the only
     * differentiator between e.g. `Monòcits %` and absolute `Monòcits`), and
     * collapse every other non-alphanumeric run to a single space. Used to
     * key both the alias table and the lookup so they compare apples-to-apples.
     */
    fun normalise(s: String): String {
        val deAccented = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return deAccented.lowercase()
            .replace("%", " percent ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Aliases keyed BY [MetricType]; the resolver is derived FROM this. Only
     * `BIOMARKER`-domain entries the owner can transcribe off a report need
     * coverage — everything else returns null. Percentage differential keys
     * carry a `%` so they don't collide with their absolute-count siblings.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun biomarkerAliases(metric: MetricType): List<String>? {
        if (metric.domain != MetricDomain.BIOMARKER) return null
        return when (metric) {
            // Glycemic
            MetricType.HBA1C -> listOf("hba1c", "hemoglobina glicada", "hemoglobina glucosilada", "glycated hemoglobin", "a1c")
            MetricType.FASTING_GLUCOSE -> listOf(
                "glucosa en dejuni", "glucosa en ayunas", "glucosa basal", "fasting glucose", "glucemia basal",
            )
            MetricType.FASTING_INSULIN -> listOf("insulina en dejuni", "insulina basal", "insulina en ayunas", "fasting insulin")
            MetricType.HOMA_IR -> listOf("homa ir", "homa", "index homa", "indice homa")
            // Inflammation
            MetricType.HSCRP -> listOf("pcr ultrasensible", "pcr us", "proteina c reactiva ultrasensible", "high sensitivity crp", "hs crp")
            MetricType.CRP -> listOf("pcr", "proteina c reactiva", "proteina c reactiva serica", "c reactive protein")
            // Lipids
            MetricType.TOTAL_CHOLESTEROL -> listOf("colesterol total", "colesterol", "cholesterol total", "colesterol serico")
            MetricType.LDL_CHOLESTEROL -> listOf("colesterol ldl", "ldl", "colesterol de ldl", "ldl cholesterol", "colesterol ldl calculat")
            MetricType.HDL_CHOLESTEROL -> listOf("colesterol hdl", "hdl", "colesterol de hdl", "hdl cholesterol", "colesterol d hdl")
            MetricType.TRIGLYCERIDES -> listOf("triglicerids", "trigliceridos", "triglycerides", "tg")
            MetricType.APO_B -> listOf("apolipoproteina b", "apo b", "apob", "apolipoprotein b")
            MetricType.LIPOPROTEIN_A -> listOf("lipoproteina a", "lp a", "lipoprotein a", "lpa")
            // Vitamins / micronutrients
            MetricType.VITAMIN_D_25OH -> listOf("vitamina d", "25 hidroxivitamina d", "25 oh vitamina d", "vitamin d 25 oh", "calcidiol")
            MetricType.VITAMIN_B12 -> listOf("vitamina b12", "cobalamina", "vitamin b12", "b12")
            MetricType.FOLATE -> listOf("acid folic", "acido folico", "folat", "folato", "folate")
            MetricType.VITAMIN_A_RETINOL -> listOf("vitamina a", "retinol", "vitamin a retinol")
            MetricType.VITAMIN_E_ALPHA_TOCOPHEROL -> listOf("vitamina e", "alfa tocoferol", "tocoferol alfa", "vitamin e")
            MetricType.VITAMIN_K2 -> listOf("vitamina k2", "menaquinona", "vitamin k2", "mk 7")
            MetricType.OMEGA_3_INDEX -> listOf("index omega 3", "indice omega 3", "omega 3 index")
            // Thyroid
            MetricType.TSH -> listOf("tsh", "tirotropina", "tirotropina tsh", "thyrotropin", "hormona estimulant del tiroide")
            MetricType.FREE_T4 -> listOf("t4 lliure", "t4 libre", "tiroxina lliure", "tiroxina libre", "free t4", "ft4")
            MetricType.FREE_T3 -> listOf("t3 lliure", "t3 libre", "triiodotironina lliure", "free t3", "ft3")
            MetricType.REVERSE_T3 -> listOf("t3 reversa", "t3 invertida", "reverse t3", "rt3")
            MetricType.THYROID_PEROXIDASE_AB -> listOf(
                "anticossos antiperoxidasa", "anti tpo", "anticuerpos antiperoxidasa",
                "tpo ab", "thyroid peroxidase antibody",
            )
            MetricType.THYROGLOBULIN_AB -> listOf("anticossos antitiroglobulina", "anti tiroglobulina", "tg ab", "thyroglobulin antibody")
            // CBC
            MetricType.HEMOGLOBIN -> listOf("hemoglobina", "hb", "hemoglobin")
            MetricType.HEMATOCRIT -> listOf("hematocrit", "hematocrito", "hto", "hct")
            MetricType.WBC -> listOf("leucocits", "leucocitos", "white blood cells", "wbc", "recompte de leucocits")
            MetricType.RBC -> listOf("hematies", "eritrocits", "eritrocitos", "red blood cells", "rbc", "hematies recompte")
            MetricType.PLATELETS -> listOf("plaquetes", "plaquetas", "platelets", "plt", "recompte de plaquetes")
            MetricType.MCV -> listOf("vcm", "volum corpuscular mitja", "volumen corpuscular medio", "mcv")
            MetricType.MCH -> listOf("hcm", "hemoglobina corpuscular mitjana", "hemoglobina corpuscular media", "mch")
            MetricType.MCHC -> listOf("chcm", "concentracio hemoglobina corpuscular", "mchc")
            MetricType.RDW -> listOf("rdw", "amplada de distribucio eritrocitaria", "ancho de distribucion eritrocitaria")
            MetricType.MPV -> listOf("vpm", "volum plaquetari mitja", "volumen plaquetario medio", "mpv")
            MetricType.NEUTROPHILS_PCT -> listOf("neutrofils %", "neutrofilos %", "neutrophils %", "segmentats %")
            MetricType.LYMPHOCYTES_PCT -> listOf("limfocits %", "linfocitos %", "lymphocytes %")
            MetricType.MONOCYTES_PCT -> listOf("monocits %", "monocitos %", "monocytes %")
            MetricType.EOSINOPHILS_PCT -> listOf("eosinofils %", "eosinofilos %", "eosinophils %")
            MetricType.BASOPHILS_PCT -> listOf("basofils %", "basofilos %", "basophils %")
            MetricType.ABSOLUTE_NEUTROPHIL_COUNT -> listOf(
                "neutrofils absoluts", "neutrofilos absolutos", "absolute neutrophil count", "anc",
            )
            MetricType.ABSOLUTE_LYMPHOCYTE_COUNT -> listOf("limfocits absoluts", "linfocitos absolutos", "absolute lymphocyte count")
            MetricType.ABSOLUTE_MONOCYTE_COUNT -> listOf("monocits absoluts", "monocitos absolutos", "absolute monocyte count")
            MetricType.ABSOLUTE_EOSINOPHIL_COUNT -> listOf("eosinofils absoluts", "eosinofilos absolutos", "absolute eosinophil count")
            MetricType.ABSOLUTE_BASOPHIL_COUNT -> listOf("basofils absoluts", "basofilos absolutos", "absolute basophil count")
            // Iron
            MetricType.FERRITIN -> listOf("ferritina", "ferritin")
            MetricType.IRON_SERUM -> listOf("ferro", "hierro", "ferro seric", "hierro serico", "serum iron", "sideremia")
            MetricType.IRON_SATURATION_PCT -> listOf(
                "index de saturacio de transferrina", "indice de saturacion de transferrina",
                "saturacio de transferrina", "transferrin saturation", "ist",
            )
            MetricType.TIBC -> listOf(
                "capacitat total de fixacio del ferro", "capacidad total de fijacion del hierro",
                "total iron binding capacity", "tibc",
            )
            // Renal / metabolic panel
            MetricType.EGFR -> listOf("filtrat glomerular", "filtrado glomerular", "fg", "egfr", "tasa de filtracion glomerular", "ckd epi")
            MetricType.CREATININE -> listOf("creatinina", "creatinine")
            MetricType.BUN -> listOf("nitrogen ureic", "nitrogeno ureico", "bun", "urea nitrogen")
            MetricType.URIC_ACID -> listOf("acid uric", "acido urico", "uric acid", "uricemia")
            MetricType.SODIUM -> listOf("sodi", "sodio", "sodium", "na")
            MetricType.POTASSIUM -> listOf("potassi", "potasio", "potassium", "k")
            MetricType.CHLORIDE -> listOf("clor", "cloro", "cloruro", "chloride", "cl")
            MetricType.CARBON_DIOXIDE -> listOf("bicarbonat", "bicarbonato", "co2 total", "carbon dioxide")
            MetricType.CALCIUM_SERUM -> listOf("calci", "calcio", "calci seric", "calcium", "calcemia")
            MetricType.PHOSPHATE -> listOf("fosfat", "fosforo", "fosfor", "phosphate", "fosforo serico")
            MetricType.MAGNESIUM -> listOf("magnesi", "magnesio", "magnesium", "mg serico")
            // Liver / pancreas
            MetricType.ALT -> listOf("alt", "gpt", "alanina aminotransferasa", "alat", "transaminasa gpt")
            MetricType.AST -> listOf("ast", "got", "aspartat aminotransferasa", "asat", "transaminasa got")
            MetricType.GGT -> listOf("ggt", "gamma gt", "gamma glutamil transferasa", "gamma glutamyl transferase")
            MetricType.ALKALINE_PHOSPHATASE -> listOf("fosfatasa alcalina", "fa", "alkaline phosphatase", "alp")
            MetricType.BILIRUBIN_TOTAL -> listOf("bilirubina total", "bilirrubina total", "total bilirubin")
            MetricType.ALBUMIN -> listOf("albumina", "albumin", "albumina serica")
            MetricType.TOTAL_PROTEIN -> listOf("proteines totals", "proteinas totales", "total protein", "proteina total")
            MetricType.AMYLASE -> listOf("amilasa", "amylase", "amilasemia")
            MetricType.LIPASE -> listOf("lipasa", "lipase")
            // Endocrine / reproductive
            MetricType.TESTOSTERONE_TOTAL -> listOf("testosterona total", "testosterona", "total testosterone")
            MetricType.TESTOSTERONE_FREE -> listOf("testosterona lliure", "testosterona libre", "free testosterone")
            MetricType.ESTRADIOL -> listOf("estradiol", "estradiol e2", "17 beta estradiol")
            MetricType.CORTISOL -> listOf("cortisol", "cortisol seric", "cortisol basal")
            MetricType.IGF_1 -> listOf("igf 1", "factor de creixement insulinic", "somatomedina c", "insulin like growth factor 1")
            MetricType.FSH -> listOf("fsh", "hormona fol·liculoestimulant", "foliculoestimulante", "follicle stimulating hormone")
            MetricType.LH -> listOf("lh", "hormona luteinitzant", "hormona luteinizante", "luteinizing hormone")
            MetricType.SHBG -> listOf(
                "shbg", "globulina fixadora hormones sexuals",
                "globulina transportadora de hormonas sexuales", "sex hormone binding globulin",
            )
            MetricType.AMH -> listOf("hormona antimulleriana", "amh", "anti mullerian hormone")
            MetricType.PROLACTIN -> listOf("prolactina", "prl", "prolactin")
            MetricType.DHEA_SULFATE -> listOf("dhea sulfat", "sulfat de dhea", "sulfato de dhea", "dhea s", "dhea sulfate")
            // Cardiac / coag
            MetricType.TROPONIN_NG_PER_L -> listOf("troponina", "troponina t", "troponina i", "troponin", "hs troponina")
            MetricType.NT_PRO_BNP_PG_PER_ML -> listOf("nt probnp", "probnp", "nt pro bnp", "pro bnp")
            MetricType.D_DIMER -> listOf("dimer d", "dimero d", "d dimer", "d dimero")
            MetricType.HOMOCYSTEINE -> listOf("homocisteina", "homocysteine")
            MetricType.PROTHROMBIN_TIME -> listOf("temps de protrombina", "tiempo de protrombina", "prothrombin time", "tp")
            MetricType.INR -> listOf("inr", "ratio internacional normalitzat", "indice internacional normalizado")
            MetricType.QUICK_INDEX -> listOf("index de quick", "indice de quick", "activitat de protrombina", "quick")
            MetricType.APTT -> listOf(
                "ttpa", "temps de tromboplastina parcial activada",
                "tiempo de tromboplastina parcial activada", "aptt",
            )
            MetricType.APTT_RATIO -> listOf("ratio ttpa", "aptt ratio", "ratio de aptt")
            // Tumor / screening / imaging markers
            MetricType.PSA_TOTAL -> listOf(
                "psa total", "psa", "antigen prostatic especific",
                "antigeno prostatico especifico", "prostate specific antigen",
            )
            MetricType.PSA_FREE -> listOf("psa lliure", "psa libre", "free psa", "fpsa")
            MetricType.CORONARY_CALCIUM_SCORE -> listOf(
                "calci coronari", "calcio coronario", "coronary calcium score", "agatston", "cac score",
            )
            MetricType.BONE_DENSITY_T_SCORE -> listOf(
                "t score", "puntuacio t", "densitat ossia t score", "bone density t score", "dexa t score",
            )
            MetricType.PTAU_217 -> listOf("ptau 217", "tau fosforilada 217", "p tau 217")
            // Adipokines
            MetricType.LEPTIN -> listOf("leptina", "leptin")
            MetricType.ADIPONECTIN -> listOf("adiponectina", "adiponectin")
            // Epigenetic / proprietary clocks (EN only — vendor-specific reports)
            MetricType.TELOMERE_LENGTH -> listOf("longitud telomerica", "telomere length", "longitud de telomeros")
            MetricType.EPIGENETIC_AGE_DUNEDIN_PACE -> listOf("dunedinpace", "dunedin pace", "pace of aging")
            MetricType.EPIGENETIC_AGE_GRIM -> listOf("grimage", "grim age", "edat epigenetica grim")
            MetricType.EPIGENETIC_AGE_PHENO -> listOf("phenoage", "pheno age", "edat epigenetica pheno")
            MetricType.EPIGENETIC_AGE_HORVATH -> listOf("horvath", "horvath age", "rellotge de horvath")
            else -> null
        }
    }
}
