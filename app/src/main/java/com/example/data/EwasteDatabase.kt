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
        AuditLogEntity::class
    ],
    version = 6,
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

        fun getDatabase(context: Context, @Suppress("UNUSED_PARAMETER") scope: CoroutineScope): EwasteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EwasteDatabase::class.java,
                    "ewaste_moefcc_database"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
