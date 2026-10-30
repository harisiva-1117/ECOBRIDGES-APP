'use strict';
/**
 * Backend auth security tests.
 *
 * Run with: npm test  (builds first, then `node --test test`)
 * No extra dependencies: uses the built output + Node's test runner.
 */
const test = require('node:test');
const assert = require('node:assert/strict');

const { AuthService, slugToRole } = require('../dist/auth/auth.service');
const { SupabaseAuthGuard } = require('../dist/auth/guards/supabase-auth.guard');
const { RolesGuard, ROLES_KEY } = require('../dist/auth/guards/roles.guard');
const { RoleType } = require('../dist/auth/dto/auth.dto');

function contextWithAuthorization(value) {
  return {
    switchToHttp: () => ({
      getRequest: () => ({ headers: value === undefined ? {} : { authorization: value } }),
    }),
    getHandler: () => ({}),
    getClass: () => ({}),
  };
}

function b64url(value) {
  return Buffer.from(JSON.stringify(value)).toString('base64url');
}

test('slugToRole maps only the three known roles', () => {
  assert.equal(slugToRole('informal_collector'), RoleType.INFORMAL_COLLECTOR);
  assert.equal(slugToRole('formal_recycler'), RoleType.FORMAL_RECYCLER);
  assert.equal(slugToRole('government_admin'), RoleType.GOVERNMENT_ADMIN);
  assert.equal(slugToRole('admin'), RoleType.GOVERNMENT_ADMIN);
  assert.equal(slugToRole('root'), null);
  assert.equal(slugToRole(''), null);
  assert.equal(slugToRole(null), null);
});

test('guard rejects a request without a bearer token', async () => {
  const guard = new SupabaseAuthGuard(new AuthService());
  await assert.rejects(() => guard.canActivate(contextWithAuthorization(undefined)), /bearer token/i);
  await assert.rejects(() => guard.canActivate(contextWithAuthorization('Basic abc')), /bearer token/i);
});

test('guard rejects a malformed token', async () => {
  const guard = new SupabaseAuthGuard(new AuthService());
  await assert.rejects(() => guard.canActivate(contextWithAuthorization('Bearer not-a-jwt')), /malformed/i);
});

test('guard rejects the old unsigned-base64 impersonation token', async () => {
  // This is exactly what the previous guard accepted: base64(JSON) with no
  // signature. It must now be rejected.
  delete process.env.SUPABASE_JWT_SECRET;
  const forged = `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url({
    sub: 'attacker',
    role: 'GOVERNMENT_ADMIN',
    exp: Math.floor(Date.now() / 1000) + 3600,
  })}.${Buffer.from('signature').toString('base64url')}`;

  const guard = new SupabaseAuthGuard(new AuthService());
  await assert.rejects(
    () => guard.canActivate(contextWithAuthorization(`Bearer ${forged}`)),
    /signature|algorithm/i,
  );
});

test('guard rejects the "none" algorithm', async () => {
  const token = `${b64url({ alg: 'none' })}.${b64url({ sub: 'x' })}.`;
  const guard = new SupabaseAuthGuard(new AuthService());
  await assert.rejects(
    () => guard.canActivate(contextWithAuthorization(`Bearer ${token}`)),
    /algorithm/i,
  );
});

test('roles guard denies a role that was not granted', () => {
  const reflector = {
    getAllAndOverride: () => [RoleType.GOVERNMENT_ADMIN],
  };
  const guard = new RolesGuard(reflector);
  const context = {
    switchToHttp: () => ({
      getRequest: () => ({ user: { role: RoleType.INFORMAL_COLLECTOR } }),
    }),
    getHandler: () => ({}),
    getClass: () => ({}),
  };
  assert.throws(() => guard.canActivate(context), /lacks clearance/i);
  assert.equal(ROLES_KEY, 'roles');
});

test('roles guard allows a matching role', () => {
  const reflector = { getAllAndOverride: () => [RoleType.GOVERNMENT_ADMIN] };
  const guard = new RolesGuard(reflector);
  const context = {
    switchToHttp: () => ({ getRequest: () => ({ user: { role: RoleType.GOVERNMENT_ADMIN } }) }),
    getHandler: () => ({}),
    getClass: () => ({}),
  };
  assert.equal(guard.canActivate(context), true);
});

test('OTP endpoints do not mint sessions', async () => {
  const service = new AuthService();
  await assert.rejects(
    () => service.requestMobileOtp({ phoneNumber: '9876543210', role: RoleType.INFORMAL_COLLECTOR }),
    /not configured/i,
  );
  await assert.rejects(
    () =>
      service.verifyMobileOtp({
        phoneNumber: '9876543210',
        otp: '123456',
        role: RoleType.INFORMAL_COLLECTOR,
      }),
    /not configured/i,
  );
});

test('email login fails closed when Supabase is not configured', async () => {
  const previousUrl = process.env.SUPABASE_URL;
  const previousKey = process.env.SUPABASE_ANON_KEY;
  delete process.env.SUPABASE_URL;
  delete process.env.SUPABASE_ANON_KEY;
  try {
    const service = new AuthService();
    await assert.rejects(
      () =>
        service.loginWithEmail({
          email: 'user@example.com',
          password: 'secret123',
          role: RoleType.INFORMAL_COLLECTOR,
        }),
      /not configured/i,
    );
  } finally {
    if (previousUrl !== undefined) process.env.SUPABASE_URL = previousUrl;
    if (previousKey !== undefined) process.env.SUPABASE_ANON_KEY = previousKey;
  }
});
