package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope

/**
 * Local Room cache for the ECOBRIDGES app.
 *
 * IMPORTANT: this database is a *cache only*. The single source of truth is the
 * shared Supabase (PostgreSQL) project. Rows are pulled from the role-based
 * views on sign-in / refresh and pushed back on creation and handover. There is
 * deliberately no on-device seed data here — the previous mock lots, prices,
 * recyclers and transactions were removed so every role observes the same
 * authoritative cloud records.
 */
@Database(
    entities = [
        MaterialLotEntity::class,
        PriceRecordEntity::class,
        RecyclerEntity::class,
        TransactionLedgerEntity::class,
        SafetyGuidelineEntity::class,
        LotPhotoEntity::class,
        CollectorLocationEntity::class,
        ConnectionRequestEntity::class,
        QuotationEntity::class,
        AuditLogEntity::class,
        RecyclerOfferEntity::class,
        PriceHistoryEntity::class,
        PriceChangeQueueEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class EwasteDatabase : RoomDatabase() {
    abstract fun materialLotDao(): MaterialLotDao
    abstract fun priceDao(): PriceDao
    abstract fun recyclerDao(): RecyclerDao
    abstract fun transactionLedgerDao(): TransactionLedgerDao
    abstract fun safetyGuidelineDao(): SafetyGuidelineDao
    abstract fun lotPhotoDao(): LotPhotoDao
    abstract fun collectorLocationDao(): CollectorLocationDao
    abstract fun connectionRequestDao(): ConnectionRequestDao
    abstract fun quotationDao(): QuotationDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun recyclerOfferDao(): RecyclerOfferDao
    abstract fun priceHistoryDao(): PriceHistoryDao
    abstract fun priceChangeQueueDao(): PriceChangeQueueDao

    companion object {
        @Volatile
        private var INSTANCE: EwasteDatabase? = null

        /** v5 -> v6: mandatory recycler dataset columns (status, area, mail). */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE authorized_recyclers ADD COLUMN authorizationStatus TEXT NOT NULL DEFAULT 'active'")
                db.execSQL("ALTER TABLE authorized_recyclers ADD COLUMN serviceArea TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE authorized_recyclers ADD COLUMN contactEmail TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v6 -> v7: editable price management.
         *
         * The local database is a cache, but upgrading preserves offline edits
         * and price history instead of wiping them. Existing rows get safe
         * defaults (active, version 1, no grade).
         */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Agreed handover price snapshot (never silently overwritten).
                db.execSQL("ALTER TABLE material_lots ADD COLUMN agreedRatePerKg REAL")
                db.execSQL("ALTER TABLE material_lots ADD COLUMN agreedAt INTEGER")

                // Editable reference prices.
                db.execSQL("ALTER TABLE price_records ADD COLUMN grade TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE price_records ADD COLUMN currency TEXT NOT NULL DEFAULT 'INR'")
                db.execSQL("ALTER TABLE price_records ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE price_records ADD COLUMN effectiveDate TEXT")
                db.execSQL("ALTER TABLE price_records ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE price_records ADD COLUMN updatedBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE price_records ADD COLUMN version INTEGER NOT NULL DEFAULT 1")

                // Recycler purchase offers (separate from reference prices).
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recycler_offers (
                        offerId TEXT NOT NULL PRIMARY KEY,
                        recyclerId TEXT NOT NULL,
                        recyclerName TEXT NOT NULL,
                        categoryName TEXT NOT NULL,
                        materialName TEXT NOT NULL,
                        grade TEXT NOT NULL,
                        pricePerKg REAL NOT NULL,
                        currency TEXT NOT NULL,
                        minQuantityKg REAL,
                        maxQuantityKg REAL,
                        condition TEXT NOT NULL,
                        status TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        effectiveFrom INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        updatedBy TEXT NOT NULL,
                        version INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // Immutable price history (local mirror of the server trigger).
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS price_history (
                        historyId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        priceId TEXT NOT NULL,
                        materialName TEXT NOT NULL,
                        categoryName TEXT NOT NULL,
                        grade TEXT NOT NULL,
                        pricePerKg REAL NOT NULL,
                        marketMin REAL NOT NULL,
                        marketMax REAL NOT NULL,
                        currency TEXT NOT NULL,
                        changeType TEXT NOT NULL,
                        changedBy TEXT NOT NULL,
                        changedAt INTEGER NOT NULL,
                        version INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // Offline queue for authorized price edits.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS price_change_queue (
                        queueId TEXT NOT NULL PRIMARY KEY,
                        kind TEXT NOT NULL,
                        targetId TEXT NOT NULL,
                        actorUserId TEXT NOT NULL,
                        actorRole TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        baseVersion INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        errorMessage TEXT,
                        serverVersion INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context, @Suppress("UNUSED_PARAMETER") scope: CoroutineScope): EwasteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EwasteDatabase::class.java,
                    "ewaste_moefcc_database"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
