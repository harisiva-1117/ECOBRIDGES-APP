'use strict';
/**
 * Server-side price-management tests.
 *
 * Run with `npm test` (builds first). No extra dependencies.
 */
const test = require('node:test');
const assert = require('node:assert/strict');

const { PricePolicyService } = require('../dist/prices/price-policy.service');
const { PricesController } = require('../dist/prices/prices.controller');
const { RolesGuard, ROLES_KEY } = require('../dist/auth/guards/roles.guard');
const { RoleType } = require('../dist/auth/dto/auth.dto');

const policy = new PricePolicyService();

function reference(overrides = {}) {
  return {
    category: 'PCB_BOARDS',
    materialName: 'Motherboard',
    pricePerKg: 120,
    marketMin: 100,
    marketMax: 150,
    currency: 'INR',
    ...overrides,
  };
}

function offer(overrides = {}) {
  return {
    recyclerId: 'REC-1',
    category: 'CABLES_WIRES',
    materialName: 'Copper wire',
    pricePerKg: 250,
    minQuantityKg: 5,
    maxQuantityKg: 100,
    status: 'ACTIVE',
    currency: 'INR',
    ...overrides,
  };
}

function rolesContext(role) {
  return {
    switchToHttp: () => ({ getRequest: () => ({ user: { role } }) }),
    getHandler: () => ({}),
    getClass: () => ({}),
  };
}

test('only a government admin may manage reference prices', () => {
  assert.equal(policy.canManageReferencePrices(RoleType.GOVERNMENT_ADMIN), true);
  assert.equal(policy.canManageReferencePrices(RoleType.FORMAL_RECYCLER), false);
  assert.equal(policy.canManageReferencePrices(RoleType.INFORMAL_COLLECTOR), false);
  assert.equal(policy.canManageReferencePrices(null), false);
});

test('only a formal recycler may manage offers', () => {
  assert.equal(policy.canManageOffers(RoleType.FORMAL_RECYCLER), true);
  assert.equal(policy.canManageOffers(RoleType.GOVERNMENT_ADMIN), false);
  assert.equal(policy.canManageOffers(undefined), false);
});

test('offer ownership is exact and case-insensitive', () => {
  assert.equal(policy.ownsOffer('REC-1', 'rec-1'), true);
  assert.equal(policy.ownsOffer(' REC-1 ', 'REC-1'), true);
  assert.equal(policy.ownsOffer('REC-1', 'REC-2'), false);
  assert.equal(policy.ownsOffer('', 'REC-1'), false);
});

test('reference price validation rejects invalid ranges and currencies', () => {
  assert.equal(policy.validateReferencePrice(reference()).valid, true);
  assert.equal(policy.validateReferencePrice(reference({ pricePerKg: -1 })).valid, false);
  assert.equal(policy.validateReferencePrice(reference({ marketMin: 200, marketMax: 100 })).valid, false);
  assert.equal(policy.validateReferencePrice(reference({ materialName: '  ' })).valid, false);
  assert.equal(policy.validateReferencePrice(reference({ currency: 'USD' })).valid, false);
});

test('offer validation rejects zero price and inverted quantities', () => {
  assert.equal(policy.validateOffer(offer()).valid, true);
  assert.equal(policy.validateOffer(offer({ pricePerKg: 0 })).valid, false);
  assert.equal(policy.validateOffer(offer({ minQuantityKg: 200, maxQuantityKg: 10 })).valid, false);
  assert.equal(policy.validateOffer(offer({ status: 'LIVE' })).valid, false);
});

test('a twenty percent move requires confirmation', () => {
  assert.equal(policy.isSignificantChange(100, 119), false);
  assert.equal(policy.isSignificantChange(100, 120), true);
  assert.equal(policy.isSignificantChange(100, 80), true);
  assert.equal(policy.isSignificantChange(null, 500), false);
  assert.equal(policy.isSignificantChange(0, 500), false);
});

test('stale versions and agreed prices are never silently overwritten', () => {
  assert.equal(policy.canApplyChange(3, 3), true);
  assert.equal(policy.canApplyChange(3, 4), false);
  assert.equal(policy.canApplyChange(0, null), true);
  assert.equal(policy.canApplyChange(2, null), false);

  assert.equal(policy.canChangeAgreedPrice(null, 55), true);
  assert.equal(policy.canChangeAgreedPrice(55, 55), true);
  assert.equal(policy.canChangeAgreedPrice(55, 60), false);
});

test('price endpoints declare the correct role metadata', () => {
  assert.deepEqual(
    Reflect.getMetadata(ROLES_KEY, PricesController.prototype.validateReference),
    [RoleType.GOVERNMENT_ADMIN],
  );
  assert.deepEqual(
    Reflect.getMetadata(ROLES_KEY, PricesController.prototype.validateOffer),
    [RoleType.FORMAL_RECYCLER],
  );
});

test('roles guard denies a collector on the admin price endpoint', () => {
  const reflector = { getAllAndOverride: () => [RoleType.GOVERNMENT_ADMIN] };
  const guard = new RolesGuard(reflector);
  assert.equal(guard.canActivate(rolesContext(RoleType.GOVERNMENT_ADMIN)), true);
  assert.throws(
    () => guard.canActivate(rolesContext(RoleType.INFORMAL_COLLECTOR)),
    /lacks clearance/i,
  );
});

test('controller rejects an offer for a facility the caller does not own', () => {
  const controller = new PricesController(policy);
  assert.throws(
    () =>
      controller.validateOffer(offer({ recyclerId: 'REC-2' }), {
        user: { role: RoleType.FORMAL_RECYCLER, statutoryIdentifier: 'REC-1' },
      }),
    /own facility/i,
  );
  assert.throws(
    () =>
      controller.validateOffer(offer(), {
        user: { role: RoleType.FORMAL_RECYCLER },
      }),
    /identity/i,
  );
  const ok = controller.validateOffer(offer(), {
    user: { role: RoleType.FORMAL_RECYCLER, statutoryIdentifier: 'REC-1' },
  });
  assert.equal(ok.valid, true);
});

test('controller reports significant reference-price moves', () => {
  const controller = new PricesController(policy);
  const small = controller.validateReference(reference({ previousPricePerKg: 100, pricePerKg: 110 }));
  assert.equal(small.valid, true);
  assert.equal(small.significantChange, false);
  const large = controller.validateReference(reference({ previousPricePerKg: 100, pricePerKg: 150 }));
  assert.equal(large.significantChange, true);
});
