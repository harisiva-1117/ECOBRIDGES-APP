package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.model.MaterialCategory
import com.example.model.RoleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
 * In-memory Room tests for price writes: history, ownership, authorisation,
 * offline queueing, optimistic concurrency and agreed-price preservation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PriceManagementRepositoryTest {

    private lateinit var database: EwasteDatabase
    private lateinit var repository: EwasteRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, EwasteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = EwasteRepository(
            database.materialLotDao(),
            database.priceDao(),
            database.recyclerDao(),
            database.transactionLedgerDao(),
            database.safetyGuidelineDao(),
            database.lotPhotoDao(),
            database.collectorLocationDao(),
            database.connectionRequestDao(),
            database.quotationDao(),
            database.auditLogDao(),
            database.recyclerOfferDao(),
            database.priceHistoryDao(),
            database.priceChangeQueueDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun referenceInput(
        priceId: String = "",
        pricePerKg: Double = 120.0,
        baseVersion: Long = 0L,
        isActive: Boolean = true
    ) = ReferencePriceInput(
        priceId = priceId,
        category = MaterialCategory.PCB_BOARDS,
        materialName = "Motherboard",
        grade = "stripped",
        pricePerKg = pricePerKg,
        marketMin = 100.0,
        marketMax = 150.0,
        isActive = isActive,
        baseVersion = baseVersion
    )

    private fun offerInput(
        offerId: String = "",
        recyclerId: String = "REC-1",
        pricePerKg: Double = 250.0,
        baseVersion: Long = 0L
    ) = OfferInput(
        offerId = offerId,
        recyclerId = recyclerId,
        recyclerName = "Green Refineries",
        category = MaterialCategory.CABLES_WIRES,
        materialName = "Copper wire",
        pricePerKg = pricePerKg,
        minQuantityKg = 5.0,
        maxQuantityKg = 100.0,
        status = "ACTIVE",
        baseVersion = baseVersion
    )

    private fun lotEntity(lotId: String, agreedRate: Double? = null) = MaterialLotEntity(
        lotId = lotId,
        categoryName = MaterialCategory.PCB_BOARDS.name,
        subCategory = "Motherboard",
        weightKg = 10.0,
        condition = "Working",
        imageUri = null,
        estimatedValueInr = 1200.0,
        quotedRatePerKg = 120.0,
        collectionTimestamp = 1_700_000_000_000L,
        collectionLocation = "Pune",
        gpsCoordinates = "18.52,73.85",
        matchedRecyclerId = "REC-1",
        matchedRecyclerName = "Green Refineries",
        statusName = "MATCHED",
        paymentModeName = "UPI",
        handoverReceiptNumber = "RC-TEST",
        recyclerConfirmed = false,
        eprCertificateNo = null,
        manifestDetails = null,
        isSynced = true,
        agreedRatePerKg = agreedRate,
        agreedAt = agreedRate?.let { 1_700_000_100_000L }
    )

    @Test
    fun `non-admin cannot write a reference price`() = runBlocking {
        val result = repository.saveReferencePrice(
            input = referenceInput(),
            actorRole = RoleType.INFORMAL_COLLECTOR,
            actorUserId = "collector-1",
            actorLabel = "Collector",
            isDemoAccount = false
        )
        assertTrue(result is PriceWriteResult.Rejected)
        assertTrue(database.priceDao().getAllPricesNow().isEmpty())
        assertTrue(database.priceHistoryDao().getAll().first().isEmpty())
        assertTrue(database.priceChangeQueueDao().getAll().first().isEmpty())
    }

    @Test
    fun `admin edit records history and queues an offline change`() = runBlocking {
        val saved = repository.saveReferencePrice(
            input = referenceInput(priceId = "PRICE-1"),
            actorRole = RoleType.GOVERNMENT_ADMIN,
            actorUserId = "admin-1",
            actorLabel = "Admin One",
            isDemoAccount = false
        )
        assertEquals(PriceWriteResult.Saved(localOnly = false), saved)

        val row = database.priceDao().getPriceById("PRICE-1")
        assertNotNull(row)
        assertEquals(120.0, row!!.prevailingBuyRate, 0.0)
        assertEquals(1L, row.version)
        assertEquals("Admin One", row.updatedBy)

        val history = database.priceHistoryDao().getAll().first()
        assertEquals(1, history.size)
        assertEquals("CREATE", history.first().changeType)

        val queue = database.priceChangeQueueDao().getAll().first()
        assertEquals(1, queue.size)
        assertEquals("REFERENCE_PRICE", queue.first().kind)
        assertEquals("PRICE-1", queue.first().targetId)
    }

    @Test
    fun `demo admin edit is local-only and never queued`() = runBlocking {
        val saved = repository.saveReferencePrice(
            input = referenceInput(priceId = "PRICE-DEMO"),
            actorRole = RoleType.GOVERNMENT_ADMIN,
            actorUserId = "DEMO-ADMIN",
            actorLabel = "Demo admin",
            isDemoAccount = true
        )
        assertEquals(PriceWriteResult.Saved(localOnly = true), saved)
        assertNotNull(database.priceDao().getPriceById("PRICE-DEMO"))
        assertTrue(database.priceChangeQueueDao().getAll().first().isEmpty())
    }

    @Test
    fun `editing with a stale version is refused and preserves history`() = runBlocking {
        repository.saveReferencePrice(
            referenceInput(priceId = "PRICE-2"), RoleType.GOVERNMENT_ADMIN, "admin-1", "Admin", false
        )
        val updated = repository.saveReferencePrice(
            referenceInput(priceId = "PRICE-2", pricePerKg = 135.0, baseVersion = 1L),
            RoleType.GOVERNMENT_ADMIN,
            "admin-1",
            "Admin",
            false
        )
        assertTrue(updated is PriceWriteResult.Saved)

        val stale = repository.saveReferencePrice(
            referenceInput(priceId = "PRICE-2", pricePerKg = 999.0, baseVersion = 1L),
            RoleType.GOVERNMENT_ADMIN,
            "admin-1",
            "Admin",
            false
        )
        assertTrue(stale is PriceWriteResult.Conflict)
        assertEquals(135.0, database.priceDao().getPriceById("PRICE-2")!!.prevailingBuyRate, 0.0)

        val history = database.priceHistoryDao().getAll().first()
        assertEquals(2, history.size)
        assertTrue(history.any { it.changeType == "CREATE" })
        assertTrue(history.any { it.changeType == "UPDATE" && it.pricePerKg == 135.0 })
    }

    @Test
    fun `deactivation requires the current version`() = runBlocking {
        repository.saveReferencePrice(
            referenceInput(priceId = "PRICE-3"), RoleType.GOVERNMENT_ADMIN, "admin-1", "Admin", false
        )
        val stale = repository.deactivateReferencePrice(
            "PRICE-3", 99L, RoleType.GOVERNMENT_ADMIN, "admin-1", "Admin", false
        )
        assertTrue(stale is PriceWriteResult.Conflict)
        assertTrue(database.priceDao().getPriceById("PRICE-3")!!.isActive)

        val done = repository.deactivateReferencePrice(
            "PRICE-3", 1L, RoleType.GOVERNMENT_ADMIN, "admin-1", "Admin", false
        )
        assertEquals(PriceWriteResult.Saved(localOnly = false), done)
        assertFalse(database.priceDao().getPriceById("PRICE-3")!!.isActive)
        assertTrue(
            database.priceHistoryDao().getAll().first().any { it.changeType == "DEACTIVATE" }
        )
    }

    @Test
    fun `recycler can only write its own offers`() = runBlocking {
        val own = repository.saveRecyclerOffer(
            input = offerInput(recyclerId = "REC-1"),
            actorRole = RoleType.FORMAL_RECYCLER,
            actorUserId = "recycler-user-1",
            actorLabel = "Green Refineries",
            actorRecyclerId = "REC-1",
            isDemoAccount = false
        )
        assertTrue(own is PriceWriteResult.Saved)
        val created = database.recyclerOfferDao().getAllNow().single()

        val foreign = repository.saveRecyclerOffer(
            input = offerInput(recyclerId = "REC-2"),
            actorRole = RoleType.FORMAL_RECYCLER,
            actorUserId = "recycler-user-1",
            actorLabel = "Green Refineries",
            actorRecyclerId = "REC-1",
            isDemoAccount = false
        )
        assertTrue(foreign is PriceWriteResult.Rejected)

        val hijack = repository.saveRecyclerOffer(
            input = offerInput(offerId = created.offerId, recyclerId = "REC-1", baseVersion = 1L),
            actorRole = RoleType.FORMAL_RECYCLER,
            actorUserId = "recycler-user-2",
            actorLabel = "Other facility",
            actorRecyclerId = "REC-2",
            isDemoAccount = false
        )
        assertTrue(hijack is PriceWriteResult.Rejected)
        assertEquals(1, database.recyclerOfferDao().getAllNow().size)
    }

    @Test
    fun `collector and admin cannot publish offers`() = runBlocking {
        listOf(RoleType.INFORMAL_COLLECTOR, RoleType.GOVERNMENT_ADMIN).forEach { role ->
            val result = repository.saveRecyclerOffer(
                input = offerInput(),
                actorRole = role,
                actorUserId = "user-1",
                actorLabel = "Someone",
                actorRecyclerId = "REC-1",
                isDemoAccount = false
            )
            assertTrue(result is PriceWriteResult.Rejected)
        }
        assertTrue(database.recyclerOfferDao().getAllNow().isEmpty())
    }

    @Test
    fun `a recycler cannot create two offers for the same category`() = runBlocking {
        repository.saveRecyclerOffer(
            offerInput(recyclerId = "REC-1"), RoleType.FORMAL_RECYCLER, "u1", "Green", "REC-1", false
        )
        val duplicate = repository.saveRecyclerOffer(
            offerInput(recyclerId = "REC-1", pricePerKg = 260.0),
            RoleType.FORMAL_RECYCLER,
            "u1",
            "Green",
            "REC-1",
            false
        )
        assertTrue(duplicate is PriceWriteResult.Rejected)
        assertEquals(1, database.recyclerOfferDao().getAllNow().size)
    }

    @Test
    fun `an agreed handover price is preserved and used in the ledger`() = runBlocking {
        database.materialLotDao().insertLot(lotEntity("LOT-1", agreedRate = 55.0))

        repository.confirmRecyclerHandover("LOT-1", verifiedWeight = 12.0, agreedRatePerKg = 99.0)

        val lot = database.materialLotDao().getLotById("LOT-1")!!
        assertEquals(55.0, lot.agreedRatePerKg!!, 0.0)
        assertEquals(1_700_000_100_000L, lot.agreedAt)
        assertEquals(660.0, lot.estimatedValueInr, 0.0)

        val ledger = database.transactionLedgerDao().getTransactionByLot("LOT-1")!!
        assertEquals(55.0, ledger.ratePerKg, 0.0)
        assertEquals(660.0, ledger.totalAmountInr, 0.0)
    }

    @Test
    fun `the first handover snapshots the agreed rate for later reference`() = runBlocking {
        database.materialLotDao().insertLot(lotEntity("LOT-2"))
        repository.confirmRecyclerHandover("LOT-2", verifiedWeight = 8.0, agreedRatePerKg = 140.0)

        val lot = database.materialLotDao().getLotById("LOT-2")!!
        assertEquals(140.0, lot.agreedRatePerKg!!, 0.0)
        assertNotNull(lot.agreedAt)
        assertEquals(1120.0, lot.estimatedValueInr, 0.0)

        // A later reference-price change must not touch this snapshot.
        repository.saveReferencePrice(
            referenceInput(priceId = "PRICE-LATER", pricePerKg = 500.0),
            RoleType.GOVERNMENT_ADMIN,
            "admin-1",
            "Admin",
            false
        )
        val after = database.materialLotDao().getLotById("LOT-2")!!
        assertEquals(140.0, after.agreedRatePerKg!!, 0.0)
    }

    @Test
    fun `recording an agreed price on a missing lot is rejected`() = runBlocking {
        val result = repository.recordAgreedHandoverPrice(
            lotId = "MISSING",
            ratePerKg = 100.0,
            actorRole = RoleType.FORMAL_RECYCLER,
            actorUserId = "recycler-user-1"
        )
        assertTrue(result is PriceWriteResult.Rejected)
        assertNull(database.materialLotDao().getLotById("MISSING"))
    }
}
