package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Executes the real Room 6 -> 7 migration against a v6 SQLite file and proves
 * that existing rows survive and the new price-management schema appears.
 *
 * This complements the in-memory repository tests: those prove behaviour, this
 * proves the upgrade path an existing installation will actually run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration6To7Test {

    private lateinit var context: Context

    private companion object {
        const val DB_NAME = "migration_6_7_test.db"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    /** Opens the file at v6 and creates the two tables touched by the migration. */
    private fun createV6Database(): SupportSQLiteOpenHelper {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS material_lots (
                                lotId TEXT NOT NULL PRIMARY KEY,
                                categoryName TEXT NOT NULL,
                                subCategory TEXT NOT NULL,
                                weightKg REAL NOT NULL,
                                condition TEXT NOT NULL,
                                imageUri TEXT,
                                estimatedValueInr REAL NOT NULL,
                                quotedRatePerKg REAL NOT NULL,
                                collectionTimestamp INTEGER NOT NULL,
                                collectionLocation TEXT NOT NULL,
                                gpsCoordinates TEXT NOT NULL,
                                matchedRecyclerId TEXT,
                                matchedRecyclerName TEXT,
                                statusName TEXT NOT NULL,
                                paymentModeName TEXT NOT NULL,
                                handoverReceiptNumber TEXT,
                                recyclerConfirmed INTEGER NOT NULL,
                                eprCertificateNo TEXT,
                                manifestDetails TEXT,
                                isSynced INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS price_records (
                                id TEXT NOT NULL PRIMARY KEY,
                                categoryName TEXT NOT NULL,
                                subCategory TEXT NOT NULL,
                                location TEXT NOT NULL,
                                prevailingBuyRate REAL NOT NULL,
                                marketMin REAL NOT NULL,
                                marketMax REAL NOT NULL,
                                trend TEXT NOT NULL,
                                trendPercentage REAL NOT NULL,
                                unit TEXT NOT NULL,
                                dateUpdated TEXT NOT NULL,
                                keyMetalsJoined TEXT NOT NULL
                            )
                            """.trimIndent()
                        )

                        db.execSQL(
                            """
                            INSERT INTO material_lots (
                                lotId, categoryName, subCategory, weightKg, condition,
                                imageUri, estimatedValueInr, quotedRatePerKg,
                                collectionTimestamp, collectionLocation, gpsCoordinates,
                                matchedRecyclerId, matchedRecyclerName, statusName,
                                paymentModeName, handoverReceiptNumber, recyclerConfirmed,
                                eprCertificateNo, manifestDetails, isSynced
                            ) VALUES (
                                'LOT-V6', 'PCB_BOARDS', 'Motherboard', 10.0, 'Working',
                                NULL, 1200.0, 120.0, 1700000000000, 'Pune', '18.52,73.85',
                                'REC-1', 'Green Refineries', 'MATCHED', 'UPI', 'RC-V6',
                                0, NULL, NULL, 1
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO price_records (
                                id, categoryName, subCategory, location, prevailingBuyRate,
                                marketMin, marketMax, trend, trendPercentage, unit,
                                dateUpdated, keyMetalsJoined
                            ) VALUES (
                                'PRICE-V6', 'PCB_BOARDS', 'Motherboard', 'Pune', 120.0,
                                100.0, 150.0, 'STABLE', 0.0, 'INR/kg', '2026-10-01', ''
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase // force onCreate with the v6 schema
        return helper
    }

    @Test
    fun `migration from 6 to 7 preserves rows and adds price-management schema`() {
        createV6Database().close()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase
        try {
            // Precondition: v6 schema really is in place.
            assertEquals(1, count(db, "material_lots"))
            assertEquals(1, count(db, "price_records"))
            assertFalse(columnNames(db, "material_lots").contains("agreedRatePerKg"))

            EwasteDatabase.MIGRATION_6_7.migrate(db)

            // Existing rows survive.
            assertEquals(1, count(db, "material_lots"))
            assertEquals(1, count(db, "price_records"))

            // New material_lots snapshot columns are nullable and empty.
            val lot = db.query("SELECT * FROM material_lots WHERE lotId = 'LOT-V6'").use { c ->
                assertTrue(c.moveToFirst())
                mapOf(
                    "weightKg" to c.getDouble(c.getColumnIndexOrThrow("weightKg")),
                    "quotedRatePerKg" to c.getDouble(c.getColumnIndexOrThrow("quotedRatePerKg")),
                    "agreedNull" to c.isNull(c.getColumnIndexOrThrow("agreedRatePerKg")),
                    "agreedAtNull" to c.isNull(c.getColumnIndexOrThrow("agreedAt"))
                )
            }
            assertEquals(10.0, lot["weightKg"] as Double, 0.0)
            assertEquals(120.0, lot["quotedRatePerKg"] as Double, 0.0)
            assertEquals(true, lot["agreedNull"])
            assertEquals(true, lot["agreedAtNull"])

            // Existing price gets safe defaults, not nulls.
            val price = db.query("SELECT * FROM price_records WHERE id = 'PRICE-V6'").use { c ->
                assertTrue(c.moveToFirst())
                mapOf(
                    "rate" to c.getDouble(c.getColumnIndexOrThrow("prevailingBuyRate")),
                    "grade" to c.getString(c.getColumnIndexOrThrow("grade")),
                    "currency" to c.getString(c.getColumnIndexOrThrow("currency")),
                    "isActive" to c.getInt(c.getColumnIndexOrThrow("isActive")),
                    "updatedAt" to c.getLong(c.getColumnIndexOrThrow("updatedAt")),
                    "updatedBy" to c.getString(c.getColumnIndexOrThrow("updatedBy")),
                    "version" to c.getLong(c.getColumnIndexOrThrow("version")),
                    "effectiveDateNull" to c.isNull(c.getColumnIndexOrThrow("effectiveDate"))
                )
            }
            assertEquals(120.0, price["rate"] as Double, 0.0)
            assertEquals("", price["grade"])
            assertEquals("INR", price["currency"])
            assertEquals(1, price["isActive"])
            assertEquals(0L, price["updatedAt"])
            assertEquals("", price["updatedBy"])
            assertEquals(1L, price["version"])
            assertEquals(true, price["effectiveDateNull"])

            // New tables exist and accept a write with the expected columns.
            assertTrue(tableExists(db, "recycler_offers"))
            assertTrue(tableExists(db, "price_history"))
            assertTrue(tableExists(db, "price_change_queue"))

            db.execSQL(
                """
                INSERT INTO recycler_offers (
                    offerId, recyclerId, recyclerName, categoryName, materialName,
                    grade, pricePerKg, currency, minQuantityKg, maxQuantityKg,
                    condition, status, notes, effectiveFrom, updatedAt, updatedBy, version
                ) VALUES (
                    'OFF-1', 'REC-1', 'Green Refineries', 'CABLES_WIRES', 'Copper wire',
                    '', 250.0, 'INR', 5.0, 100.0, 'Working', 'ACTIVE', '',
                    1700000000000, 1700000000000, 'Green Refineries', 1
                )
                """.trimIndent()
            )
            assertEquals(1, count(db, "recycler_offers"))

            db.execSQL(
                """
                INSERT INTO price_history (
                    priceId, materialName, categoryName, grade, pricePerKg,
                    marketMin, marketMax, currency, changeType, changedBy,
                    changedAt, version
                ) VALUES (
                    'PRICE-V6', 'Motherboard', 'PCB_BOARDS', '', 120.0,
                    100.0, 150.0, 'INR', 'CREATE', 'Admin', 1700000000000, 1
                )
                """.trimIndent()
            )
            assertEquals(1, count(db, "price_history"))

            db.execSQL(
                """
                INSERT INTO price_change_queue (
                    queueId, kind, targetId, actorUserId, actorRole, payloadJson,
                    baseVersion, status, errorMessage, serverVersion, createdAt, updatedAt
                ) VALUES (
                    'Q-1', 'REFERENCE_PRICE', 'PRICE-V6', 'admin-1', 'GOVERNMENT_ADMIN', '{}',
                    1, 'PENDING', NULL, NULL, 1700000000000, 1700000000000
                )
                """.trimIndent()
            )
            assertEquals(1, count(db, "price_change_queue"))

            val queueRow = db.query(
                "SELECT errorMessage, serverVersion FROM price_change_queue WHERE queueId = 'Q-1'"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertNull(c.getString(c.getColumnIndexOrThrow("errorMessage")))
                assertTrue(c.isNull(c.getColumnIndexOrThrow("serverVersion")))
                true
            }
            assertTrue(queueRow)
            assertNotNull(columnNames(db, "price_change_queue"))
        } finally {
            db.close()
            helper.close()
        }
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { c ->
            assertTrue(c.moveToFirst())
            c.getInt(0)
        }

    private fun columnNames(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info($table)").use { c ->
            val names = mutableSetOf<String>()
            val nameIndex = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) names.add(c.getString(nameIndex))
            names
        }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { c -> c.moveToFirst() }
}
