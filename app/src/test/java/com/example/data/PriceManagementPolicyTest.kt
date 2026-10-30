package com.example.data

import com.example.model.MaterialCategory
import com.example.model.PriceChangeKind
import com.example.model.RecyclerOffer
import com.example.model.RoleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM rules for price management. These cover role permission, ownership,
 * validation, change confirmation, optimistic concurrency and the immutability
 * of an agreed handover price.
 */
class PriceManagementPolicyTest {

    private fun referenceInput(
        materialName: String = "Motherboard",
        pricePerKg: Double = 120.0,
        marketMin: Double = 100.0,
        marketMax: Double = 150.0,
        currency: String = "INR",
        grade: String = "stripped"
    ) = ReferencePriceInput(
        priceId = "",
        category = MaterialCategory.PCB_BOARDS,
        materialName = materialName,
        grade = grade,
        pricePerKg = pricePerKg,
        marketMin = marketMin,
        marketMax = marketMax,
        currency = currency
    )

    private fun offerInput(
        recyclerId: String = "REC-1",
        materialName: String = "Copper wire",
        pricePerKg: Double = 250.0,
        min: Double? = 5.0,
        max: Double? = 100.0,
        status: String = "ACTIVE",
        currency: String = "INR"
    ) = OfferInput(
        offerId = "",
        recyclerId = recyclerId,
        recyclerName = "Green Refineries",
        category = MaterialCategory.CABLES_WIRES,
        materialName = materialName,
        pricePerKg = pricePerKg,
        minQuantityKg = min,
        maxQuantityKg = max,
        status = status,
        currency = currency
    )

    @Test
    fun `only a government admin can manage reference prices`() {
        assertTrue(PriceManagementPolicy.canManageReferencePrices(RoleType.GOVERNMENT_ADMIN))
        assertFalse(PriceManagementPolicy.canManageReferencePrices(RoleType.FORMAL_RECYCLER))
        assertFalse(PriceManagementPolicy.canManageReferencePrices(RoleType.INFORMAL_COLLECTOR))
        assertFalse(PriceManagementPolicy.canManageReferencePrices(null))
    }

    @Test
    fun `only a formal recycler can manage offers`() {
        assertTrue(PriceManagementPolicy.canManageOffers(RoleType.FORMAL_RECYCLER))
        assertFalse(PriceManagementPolicy.canManageOffers(RoleType.GOVERNMENT_ADMIN))
        assertFalse(PriceManagementPolicy.canManageOffers(RoleType.INFORMAL_COLLECTOR))
        assertFalse(PriceManagementPolicy.canManageOffers(null))
    }

    @Test
    fun `an offer belongs to exactly one recycler`() {
        assertTrue(PriceManagementPolicy.ownsOffer("REC-1", "rec-1"))
        assertTrue(PriceManagementPolicy.ownsOffer(" REC-1 ", "REC-1"))
        assertFalse(PriceManagementPolicy.ownsOffer("REC-1", "REC-2"))
        assertFalse(PriceManagementPolicy.ownsOffer("", "REC-1"))
        assertFalse(PriceManagementPolicy.ownsOffer("REC-1", ""))
    }

    @Test
    fun `valid reference price passes`() {
        assertTrue(PriceManagementPolicy.validateReferencePrice(referenceInput()).isValid)
    }

    @Test
    fun `reference price rejects negatives and inverted ranges`() {
        assertFalse(
            PriceManagementPolicy.validateReferencePrice(referenceInput(pricePerKg = -1.0)).isValid
        )
        assertFalse(
            PriceManagementPolicy.validateReferencePrice(referenceInput(marketMin = -0.5)).isValid
        )
        assertFalse(
            PriceManagementPolicy.validateReferencePrice(
                referenceInput(marketMin = 200.0, marketMax = 100.0)
            ).isValid
        )
    }

    @Test
    fun `reference price requires a material name and INR`() {
        assertFalse(
            PriceManagementPolicy.validateReferencePrice(referenceInput(materialName = "  ")).isValid
        )
        assertFalse(
            PriceManagementPolicy.validateReferencePrice(referenceInput(currency = "USD")).isValid
        )
    }

    @Test
    fun `valid offer passes`() {
        assertTrue(PriceManagementPolicy.validateOffer(offerInput()).isValid)
    }

    @Test
    fun `offer must have a positive price and ordered quantities`() {
        assertFalse(
            PriceManagementPolicy.validateOffer(offerInput(pricePerKg = 0.0)).isValid
        )
        assertFalse(
            PriceManagementPolicy.validateOffer(offerInput(min = 150.0, max = 10.0)).isValid
        )
        assertFalse(
            PriceManagementPolicy.validateOffer(offerInput(status = "LIVE")).isValid
        )
        assertFalse(
            PriceManagementPolicy.validateOffer(offerInput(currency = "USD")).isValid
        )
    }

    @Test
    fun `a twenty percent move needs explicit confirmation`() {
        assertFalse(PriceManagementPolicy.isSignificantChange(100.0, 119.0))
        assertTrue(PriceManagementPolicy.isSignificantChange(100.0, 120.0))
        assertTrue(PriceManagementPolicy.isSignificantChange(100.0, 80.0))
        assertTrue(PriceManagementPolicy.isSignificantChange(50.0, 10.0))
        assertFalse(PriceManagementPolicy.isSignificantChange(null, 500.0))
        assertFalse(PriceManagementPolicy.isSignificantChange(0.0, 500.0))
    }

    @Test
    fun `stale versions are rejected instead of overwriting`() {
        assertTrue(PriceManagementPolicy.canApplyChange(baseVersion = 3L, serverVersion = 3L))
        assertFalse(PriceManagementPolicy.canApplyChange(baseVersion = 3L, serverVersion = 4L))
        assertTrue(PriceManagementPolicy.canApplyChange(baseVersion = 0L, serverVersion = null))
        assertFalse(PriceManagementPolicy.canApplyChange(baseVersion = 2L, serverVersion = null))
    }

    @Test
    fun `an agreed price is immutable`() {
        assertTrue(PriceManagementPolicy.canChangeAgreedPrice(null, 55.0))
        assertTrue(PriceManagementPolicy.canChangeAgreedPrice(55.0, 55.0))
        assertFalse(PriceManagementPolicy.canChangeAgreedPrice(55.0, 60.0))
    }

    @Test
    fun `change kind is derived from the role`() {
        assertEquals(
            PriceChangeKind.REFERENCE_PRICE,
            PriceManagementPolicy.changeKindFor(RoleType.GOVERNMENT_ADMIN)
        )
        assertEquals(
            PriceChangeKind.RECYCLER_OFFER,
            PriceManagementPolicy.changeKindFor(RoleType.FORMAL_RECYCLER)
        )
        assertNull(PriceManagementPolicy.changeKindFor(RoleType.INFORMAL_COLLECTOR))
    }

    @Test
    fun `offer owner label always resolves to something readable`() {
        val named = RecyclerOffer(
            offerId = "OFF-1",
            recyclerId = "REC-1",
            recyclerName = "Green Refineries",
            category = MaterialCategory.CABLES_WIRES,
            materialName = "Copper wire",
            grade = "",
            pricePerKg = 250.0
        )
        assertEquals("Green Refineries", PriceManagementPolicy.offerOwnerLabel(named))
        assertEquals(
            "REC-2",
            PriceManagementPolicy.offerOwnerLabel(named.copy(recyclerName = "", recyclerId = "REC-2"))
        )
        assertEquals(
            "Unknown recycler",
            PriceManagementPolicy.offerOwnerLabel(named.copy(recyclerName = "", recyclerId = ""))
        )
    }
}
