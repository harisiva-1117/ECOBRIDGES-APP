'use strict';
/**
 * Test Suite: Demo Authentication, Role Security, and Offer/Price Persistence
 * Verifies backend-mediated authentication and multi-device persistence workflows.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');

const { AuthService, signDemoJwt } = require('../dist/auth/auth.service');
const { SupabaseAuthGuard } = require('../dist/auth/guards/supabase-auth.guard');
const { RolesGuard } = require('../dist/auth/guards/roles.guard');
const { RoleType } = require('../dist/auth/dto/auth.dto');
const { PricesController } = require('../dist/prices/prices.controller');
const { PricePolicyService } = require('../dist/prices/price-policy.service');
const { PricesService } = require('../dist/prices/prices.service');

function createMockContext(token, user = null) {
  const req = {
    headers: token ? { authorization: `Bearer ${token}` } : {},
    user,
  };
  return {
    switchToHttp: () => ({
      getRequest: () => req,
    }),
    getHandler: () => ({}),
    getClass: () => ({}),
  };
}

test('DEMO AUTH: valid demo login with code 123456 returns signed token and user profile', async () => {
  process.env.DEMO_LOGIN_ENABLED = 'true';
  process.env.DEMO_OTP_CODE = '123456';
  process.env.DEMO_ENABLE_ADMIN = 'true';
  process.env.DEMO_ACCOUNTS = '9876543210:collector,9876543211:recycler,9876543212:admin';
  process.env.DEMO_JWT_SECRET = 'test-secret-key-at-least-32-chars-long!';

  const authService = new AuthService();
  const res = await authService.demoLogin({
    phoneNumber: '9876543211',
    otp: '123456',
    role: RoleType.FORMAL_RECYCLER,
  });

  assert.equal(res.success, true);
  assert.ok(res.accessToken, 'Access token must be returned');
  assert.equal(res.user.role, RoleType.FORMAL_RECYCLER);
  assert.equal(res.user.statutoryIdentifier, 'DEMO-RECYCLER-NOT-VERIFIED');
  assert.equal(res.user.phoneNumber, '+91 9876543211');
  assert.equal(res.user.facilityId, 'REC-CPCB-MH-001');
});

test('DEMO AUTH: invalid demo code is rejected', async () => {
  process.env.DEMO_LOGIN_ENABLED = 'true';
  process.env.DEMO_OTP_CODE = '123456';
  const authService = new AuthService();

  await assert.rejects(
    () =>
      authService.demoLogin({
        phoneNumber: '9876543211',
        otp: '999999',
        role: RoleType.FORMAL_RECYCLER,
      }),
    /invalid demo verification code/i,
  );
});

test('DEMO AUTH: disabled demo login fails shut with ForbiddenException', async () => {
  process.env.DEMO_LOGIN_ENABLED = 'false';
  const authService = new AuthService();

  await assert.rejects(
    () =>
      authService.demoLogin({
        phoneNumber: '9876543211',
        otp: '123456',
        role: RoleType.FORMAL_RECYCLER,
      }),
    /disabled/i,
  );
});

test('DEMO AUTH: admin self-elevation blocked for unlisted accounts', async () => {
  process.env.DEMO_LOGIN_ENABLED = 'true';
  process.env.DEMO_OTP_CODE = '123456';
  process.env.DEMO_ENABLE_ADMIN = 'true';
  process.env.DEMO_ACCOUNTS = '9876543210:collector,9876543211:recycler,9876543212:admin';

  const authService = new AuthService();
  // An arbitrary number attempting to login as Admin
  await assert.rejects(
    () =>
      authService.demoLogin({
        phoneNumber: '9822222222',
        otp: '123456',
        role: RoleType.GOVERNMENT_ADMIN,
      }),
    /admin requires an authorized admin number/i,
  );
});

test('TOKEN VALIDATION: SupabaseAuthGuard accepts valid backend demo token', async () => {
  process.env.DEMO_LOGIN_ENABLED = 'true';
  process.env.DEMO_JWT_SECRET = 'test-secret-key-at-least-32-chars-long!';

  const authService = new AuthService();
  const guard = new SupabaseAuthGuard(authService);

  const loginRes = await authService.demoLogin({
    phoneNumber: '9876543211',
    otp: '123456',
    role: RoleType.FORMAL_RECYCLER,
  });

  const ctx = createMockContext(loginRes.accessToken);
  const allowed = await guard.canActivate(ctx);
  assert.equal(allowed, true);
  assert.equal(ctx.switchToHttp().getRequest().user.role, RoleType.FORMAL_RECYCLER);
  assert.equal(ctx.switchToHttp().getRequest().user.statutoryIdentifier, 'DEMO-RECYCLER-NOT-VERIFIED');
});

test('TOKEN VALIDATION: expired demo tokens are rejected with 401', async () => {
  process.env.DEMO_LOGIN_ENABLED = 'true';
  process.env.DEMO_JWT_SECRET = 'test-secret-key-at-least-32-chars-long!';

  const authService = new AuthService();
  const guard = new SupabaseAuthGuard(authService);

  const expiredPayload = {
    sub: 'demo-9876543211-formal_recycler',
    email: '9876543211@ecobridges.demo',
    role: RoleType.FORMAL_RECYCLER,
    roleSlug: 'formal_recycler',
    statutoryIdentifier: 'DEMO-RECYCLER-NOT-VERIFIED',
    isDemo: true,
    iss: 'ecobridges-backend',
    aud: 'authenticated',
    iat: Math.floor(Date.now() / 1000) - 7200,
    exp: Math.floor(Date.now() / 1000) - 3600, // Expired 1 hour ago
  };

  const expiredToken = signDemoJwt(expiredPayload, process.env.DEMO_JWT_SECRET);
  const ctx = createMockContext(expiredToken);

  await assert.rejects(() => guard.canActivate(ctx), /expired/i);
});

test('PERSISTENCE: Recycler creates and updates their own facility offer', async () => {
  const policy = new PricePolicyService();
  const pricesService = new PricesService(policy);
  const controller = new PricesController(policy, pricesService);

  const req = {
    user: {
      id: 'demo-9876543211',
      displayName: 'Demo Recycler Manager',
      role: RoleType.FORMAL_RECYCLER,
      statutoryIdentifier: 'DEMO-RECYCLER-NOT-VERIFIED',
    facilityId: 'REC-CPCB-MH-001',
    },
  };

  // 1. Create offer
  const created = await controller.saveOffer(
    {
      recyclerId: 'REC-CPCB-MH-001',
      category: 'BATTERIES',
      materialName: 'Lithium-Ion Phone & Laptop Packs',
      grade: 'High Grade',
      pricePerKg: 175.5,
      currency: 'INR',
      minQuantityKg: 10,
      maxQuantityKg: 500,
      condition: 'ACCEPTED',
      status: 'ACTIVE',
    },
    req,
  );

  assert.ok(created.offerId);
  assert.equal(created.pricePerKg, 175.5);
  assert.equal(created.version, 1);

  // 2. Edit offer with version check
  const updated = await controller.saveOffer(
    {
      offerId: created.offerId,
      recyclerId: 'REC-CPCB-MH-001',
      category: 'BATTERIES',
      materialName: 'Lithium-Ion Phone & Laptop Packs',
      grade: 'High Grade Premium',
      pricePerKg: 185.0,
      currency: 'INR',
      baseVersion: created.version,
    },
    req,
  );

  assert.equal(updated.pricePerKg, 185.0);
  assert.equal(updated.version, 2);
});

test('AUTHORIZATION: Recycler cannot edit another facility offer', async () => {
  const policy = new PricePolicyService();
  const pricesService = new PricesService(policy);
  const controller = new PricesController(policy, pricesService);

  const req = {
    user: {
      id: 'demo-9876543211',
      displayName: 'Demo Recycler Manager',
      role: RoleType.FORMAL_RECYCLER,
      statutoryIdentifier: 'DEMO-RECYCLER-NOT-VERIFIED',
    facilityId: 'REC-CPCB-MH-001',
    },
  };

  await assert.rejects(
    () =>
      controller.saveOffer(
        {
          recyclerId: 'REC-CPCB-MH-002', // Different facility!
          category: 'PCB_BOARDS',
          materialName: 'PC Motherboards',
          pricePerKg: 400.0,
        },
        req,
      ),
    /only publish offers for your own facility/i,
  );
});

test('AUTHORIZATION: Collector and Recycler cannot modify reference prices', async () => {
  const reflector = { getAllAndOverride: () => [RoleType.GOVERNMENT_ADMIN] };
  const guard = new RolesGuard(reflector);

  const collectorCtx = {
    switchToHttp: () => ({ getRequest: () => ({ user: { role: RoleType.INFORMAL_COLLECTOR } }) }),
    getHandler: () => ({}),
    getClass: () => ({}),
  };
  const recyclerCtx = {
    switchToHttp: () => ({ getRequest: () => ({ user: { role: RoleType.FORMAL_RECYCLER } }) }),
    getHandler: () => ({}),
    getClass: () => ({}),
  };

  assert.throws(() => guard.canActivate(collectorCtx), /lacks clearance/i);
  assert.throws(() => guard.canActivate(recyclerCtx), /lacks clearance/i);
});

test('PERSISTENCE & CROSS-DEVICE: Admin updates reference price and history is logged', async () => {
  const policy = new PricePolicyService();
  const pricesService = new PricesService(policy);
  const controller = new PricesController(policy, pricesService);

  const adminReq = {
    user: {
      id: 'demo-9876543212',
      displayName: 'Demo CPCB Admin',
      role: RoleType.GOVERNMENT_ADMIN,
    },
  };

  const saved = await controller.saveReference(
    {
      priceId: 'PR-PCB-01',
      category: 'PCB_BOARDS',
      materialName: 'High-Grade Telecom & PC Motherboards',
      grade: 'Grade A',
      pricePerKg: 395.0,
      marketMin: 350.0,
      marketMax: 430.0,
      currency: 'INR',
    },
    adminReq,
  );

  assert.equal(saved.pricePerKg, 395.0);

  // Retrieve price history
  const history = await controller.getPriceHistory('PR-PCB-01');
  assert.ok(history.length > 0);
  assert.equal(history[0].priceId, 'PR-PCB-01');
  assert.equal(history[0].pricePerKg, 395.0);
  assert.equal(history[0].changedByLabel, 'Demo CPCB Admin');
});

test('CROSS-DEVICE RETRIEVAL: Collector retrieves active recycler offers across facilities', async () => {
  const policy = new PricePolicyService();
  const pricesService = new PricesService(policy);
  const controller = new PricesController(policy, pricesService);

  const collectorReq = {
    user: {
      id: 'demo-9876543210',
      displayName: 'Demo Informal Collector',
      role: RoleType.INFORMAL_COLLECTOR,
    },
  };

  const offers = await controller.getOffers(undefined, undefined, undefined, collectorReq);
  assert.ok(Array.isArray(offers));
  assert.ok(offers.length > 0, 'Collector must see active offers');
  for (const offer of offers) {
    assert.equal(offer.status, 'ACTIVE');
  }
});
