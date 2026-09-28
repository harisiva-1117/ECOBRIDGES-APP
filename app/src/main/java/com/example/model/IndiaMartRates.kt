package com.example.model

/**
 * Curated ₹/kg price benchmarks for the app's material categories, sourced from
 * the IndiaMART "Electronic Scrap" category page
 * (https://dir.indiamart.com/impcat/electronic-scrap.html) as of 27-Sep-2026.
 * These are estimated market benchmarks taken from live supplier listings — not
 * price guarantees. [sourceLabel] cites the underlying listing(s); categories with
 * no matching listing on the page keep the app's previous benchmark and say so.
 */
data class IndiaMartRateEntry(
    val category: MaterialCategory,
    val ratePerKg: Double,
    val marketMinPerKg: Double,
    val marketMaxPerKg: Double,
    val sourceLabel: String
)

object IndiaMartRates {
    /** Last time the source page was inspected. */
    const val LAST_UPDATED = "27 Sep 2026"
    const val SOURCE_URL = "https://dir.indiamart.com/impcat/electronic-scrap.html"

    /** Page-level note shown wherever these rates are displayed. */
    const val DISCLAIMER =
        "Estimated benchmarks from IndiaMART Electronic Scrap supplier listings ($LAST_UPDATED); sell prices vary by condition, lot size and location."

    private val table = listOf(
        IndiaMartRateEntry(
            MaterialCategory.PCB_BOARDS,
            ratePerKg = 350.0,
            marketMinPerKg = 100.0,
            marketMaxPerKg = 4500.0,
            sourceLabel = "Printed Circuit Board Scrap ₹350/kg (Optima Metal Recyclers, Faridabad); telecom PCB up to ₹4,500/kg; processor scrap ₹14,000/kg"
        ),
        IndiaMartRateEntry(
            MaterialCategory.CABLES_WIRES,
            ratePerKg = 460.0,
            marketMinPerKg = 300.0,
            marketMaxPerKg = 600.0,
            sourceLabel = "Corporate IT scrap ₹500/kg incl. wires & machines (LC E-Waste Recyclers, Hoskote); app benchmark for dedicated cable loads"
        ),
        IndiaMartRateEntry(
            MaterialCategory.BATTERIES,
            ratePerKg = 160.0,
            marketMinPerKg = 120.0,
            marketMaxPerKg = 200.0,
            sourceLabel = "No battery listing on the page — app benchmark kept"
        ),
        IndiaMartRateEntry(
            MaterialCategory.CRTS_MONITORS,
            ratePerKg = 45.0,
            marketMinPerKg = 30.0,
            marketMaxPerKg = 60.0,
            sourceLabel = "Computer Monitor Scrap (no listed price on page) — app benchmark kept"
        ),
        IndiaMartRateEntry(
            MaterialCategory.LCD_PANELS,
            ratePerKg = 120.0,
            marketMinPerKg = 28.0,
            marketMaxPerKg = 350.0,
            sourceLabel = "Laptop Body Scrap ₹240–350/kg (A. K. Enterprises, New Delhi); LED Light Scrap ₹28/kg (Galaxy Steels, Bengaluru)"
        ),
        IndiaMartRateEntry(
            MaterialCategory.MOTORS_MAGNETS,
            ratePerKg = 50.0,
            marketMinPerKg = 40.0,
            marketMaxPerKg = 60.0,
            sourceLabel = "Electronic Motor Control Panel Scrap ₹50/kg (Royal Scrap Traders, Indore)"
        ),
        IndiaMartRateEntry(
            MaterialCategory.MIXED_PLASTICS,
            ratePerKg = 90.0,
            marketMinPerKg = 28.0,
            marketMaxPerKg = 90.0,
            sourceLabel = "Computer Keyboard Scrap ₹90/kg (Green Roots Waste Management, Amroha); CD Scrap ₹90/kg (Shri Krishna Plastic, New Delhi); LED Scrap ₹28/kg"
        )
    )

    fun get(category: MaterialCategory): IndiaMartRateEntry =
        table.firstOrNull { it.category == category }
            ?: IndiaMartRateEntry(category, category.defaultRatePerKg, category.defaultRatePerKg * 0.85, category.defaultRatePerKg * 1.1, "App benchmark")

    fun ratePerKg(category: MaterialCategory): Double = get(category).ratePerKg

    fun sourceFor(category: MaterialCategory): String = get(category).sourceLabel
}