import {
  ForbiddenException,
  Injectable,
  Logger,
  NotImplementedException,
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';
import * as crypto from 'crypto';
import { createClient, SupabaseClient } from '@supabase/supabase-js';
import {
  AccountStatus,
  AuthResponse,
  DemoLoginDto,
  EmailLoginDto,
  GoogleSignInDto,
  RequestOtpDto,
  RoleType,
  VerifyOtpDto,
} from './dto/auth.dto';

export function base64UrlEncode(data: string | Buffer): string {
  const buf = Buffer.isBuffer(data) ? data : Buffer.from(data, 'utf8');
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * Read the demo-signing secret from the environment.
 *
 * There is deliberately NO hard-coded fallback: if neither `DEMO_JWT_SECRET`
 * nor `SUPABASE_JWT_SECRET` is configured, demo login (and verification of an
 * existing demo token) fails closed instead of using a public default secret.
 */
export function getDemoJwtSecret(): string | null {
  const secret = (process.env.DEMO_JWT_SECRET || process.env.SUPABASE_JWT_SECRET || '').trim();
  return secret.length > 0 ? secret : null;
}

export function signDemoJwt(payload: Record<string, any>, secret: string): string {
  if (!secret) {
    throw new Error('Demo token signing secret is not configured.');
  }
  const header = { alg: 'HS256', typ: 'JWT' };
  const headerEncoded = base64UrlEncode(JSON.stringify(header));
  const payloadEncoded = base64UrlEncode(JSON.stringify(payload));
  const signature = crypto
    .createHmac('sha256', secret)
    .update(`${headerEncoded}.${payloadEncoded}`)
    .digest();
  return `${headerEncoded}.${payloadEncoded}.${base64UrlEncode(signature)}`;
}

/** Shape of a `public.profiles` row used by this service. */
export interface ProfileRecord {
  id?: string;
  auth_user_id: string;
  role: string | null;
  account_status: string | null;
  display_name: string | null;
  entity_name: string | null;
  statutory_identifier: string | null;
  phone_number: string | null;
  email: string | null;
}

export interface AuthenticatedUser {
  id: string;
  email?: string;
  role: RoleType;
  roleSlug: string;
  accountStatus: AccountStatus;
  displayName: string;
  entityName: string;
  statutoryIdentifier: string;
  phoneNumber?: string;
  permissions: string[];
  /** True only for a backend-issued demo session (never a Supabase JWT). */
  isDemo: boolean;
  /** Registry `authorized_recyclers.recycler_id` for an owning recycler. */
  facilityId?: string;
}

/** The one demo recycler facility seeded by schema.sql / 0005. */
export const DEMO_RECYCLER_FACILITY_ID = 'REC-CPCB-MH-001';

const DASHBOARD_BY_ROLE: Record<RoleType, string> = {
  [RoleType.INFORMAL_COLLECTOR]: '/collector/dashboard',
  [RoleType.FORMAL_RECYCLER]: '/recycler/portal',
  [RoleType.GOVERNMENT_ADMIN]: '/government/oversight',
};

export const PERMISSIONS_BY_ROLE: Record<RoleType, string[]> = {
  [RoleType.INFORMAL_COLLECTOR]: [
    'COLL_ISSUE_RECEIPT',
    'COLL_VIEW_RATES',
    'COLL_DIGITAL_WEIGH_IN',
    'COLL_EPR_CREDIT_ACCRUAL',
  ],
  [RoleType.FORMAL_RECYCLER]: [
    'REC_CPCB_INBOUND_ACCEPT',
    'REC_EPR_CERTIFICATE_MINT',
    'REC_HAZARDOUS_NEUTRALIZE',
    'REC_DIRECT_ESCROW_SETTLE',
  ],
  [RoleType.GOVERNMENT_ADMIN]: [
    'ADM_CPCB_PAN_INDIA_OVERSIGHT',
    'ADM_EPR_COMPLIANCE_AUDIT',
    'ADM_FACILITY_GEO_INSPECT',
    'ADM_RULE_13_PENALTY_NOTICE',
  ],
};

export function slugToRole(slug: string | null | undefined): RoleType | null {
  switch ((slug ?? '').trim().toLowerCase()) {
    case 'informal_collector':
    case 'collector':
      return RoleType.INFORMAL_COLLECTOR;
    case 'formal_recycler':
    case 'recycler':
      return RoleType.FORMAL_RECYCLER;
    case 'government_admin':
    case 'admin':
      return RoleType.GOVERNMENT_ADMIN;
    default:
      return null;
  }
}

function roleToSqlSlug(role: RoleType): string {
  switch (role) {
    case RoleType.INFORMAL_COLLECTOR:
      return 'informal_collector';
    case RoleType.FORMAL_RECYCLER:
      return 'formal_recycler';
    case RoleType.GOVERNMENT_ADMIN:
      return 'government_admin';
    default:
      return 'informal_collector';
  }
}

function statusFromDb(value: string | null | undefined): AccountStatus {
  switch ((value ?? '').trim().toLowerCase()) {
    case 'active':
      return AccountStatus.ACTIVE;
    case 'pending_verification':
    case 'pending':
      return AccountStatus.PENDING_VERIFICATION;
    case 'suspended':
    case 'rejected':
    case 'expired':
      return AccountStatus.SUSPENDED;
    default:
      return AccountStatus.PENDING_VERIFICATION;
  }
}

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);
  private readonly supabaseUrl: string;
  private readonly anonKey: string;

  constructor() {
    this.supabaseUrl = (process.env.SUPABASE_URL ?? '').replace(/\/+$/, '');
    this.anonKey = process.env.SUPABASE_ANON_KEY ?? '';

    if (!this.supabaseUrl || !this.anonKey) {
      // Do not crash the whole process: health checks still work and every
      // auth call fails closed with 503 instead of accepting anyone.
      this.logger.error(
        'SUPABASE_URL / SUPABASE_ANON_KEY are not configured. Authentication is disabled.',
      );
    }
  }

  private assertConfigured(): void {
    if (!this.supabaseUrl || !this.anonKey) {
      throw new ServiceUnavailableException('Authentication provider is not configured.');
    }
  }

  /**
   * A fresh client per request: `signIn*` mutates the client's local session,
   * so sharing one instance would leak sessions across concurrent requests.
   */
  private newUserClient(accessToken?: string): SupabaseClient {
    return createClient(this.supabaseUrl, this.anonKey, {
      auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
      global: accessToken
        ? { headers: { Authorization: `Bearer ${accessToken}` } }
        : undefined,
    });
  }

  /** Loads the caller's own profile under RLS (no service key required). */
  async resolveProfile(authUserId: string, accessToken: string): Promise<ProfileRecord | null> {
    this.assertConfigured();
    const client = this.newUserClient(accessToken);
    const { data, error } = await client
      .from('profiles')
      .select(
        'id,auth_user_id,role,account_status,display_name,entity_name,statutory_identifier,phone_number,email',
      )
      .eq('auth_user_id', authUserId)
      .maybeSingle<ProfileRecord>();

    if (error) {
      this.logger.warn(`Profile lookup failed: ${error.message}`);
      throw new ServiceUnavailableException('Could not verify the account profile.');
    }
    return data ?? null;
  }

  /** Used by SupabaseAuthGuard. Rejects missing/inactive/unknown roles. */
  async loadAuthenticatedUser(
    authUserId: string,
    accessToken: string,
    email?: string,
  ): Promise<AuthenticatedUser> {
    const profile = await this.resolveProfile(authUserId, accessToken);
    if (!profile) {
      throw new ForbiddenException('No profile is provisioned for this account.');
    }
    const role = slugToRole(profile.role);
    if (!role) {
      throw new ForbiddenException('This account has no recognized role.');
    }
    const accountStatus = statusFromDb(profile.account_status);
    if (accountStatus !== AccountStatus.ACTIVE) {
      throw new ForbiddenException('This account is not active. Contact an administrator.');
    }
    return {
      id: authUserId,
      email: profile.email ?? email,
      role,
      roleSlug: profile.role ?? roleToSqlSlug(role),
      accountStatus,
      displayName: profile.display_name || (profile.email ?? email) || 'User',
      entityName: profile.entity_name || '',
      statutoryIdentifier: profile.statutory_identifier || '',
      phoneNumber: profile.phone_number ?? undefined,
      permissions: PERMISSIONS_BY_ROLE[role],
      isDemo: false,
    };
  }

  private buildResponse(
    accessToken: string,
    refreshToken: string | undefined,
    expiresIn: number | undefined,
    user: AuthenticatedUser,
  ): AuthResponse {
    return {
      success: true,
      message: 'Authentication successful.',
      accessToken,
      refreshToken,
      expiresIn,
      user: {
        id: user.id,
        email: user.email,
        phoneNumber: user.phoneNumber,
        displayName: user.displayName,
        role: user.role,
        status: user.accountStatus,
        permissions: user.permissions,
        statutoryIdentifier: user.statutoryIdentifier,
        entityName: user.entityName,
        isDemoAccount: user.isDemo,
        facilityId: user.facilityId,
        dashboardRoute: DASHBOARD_BY_ROLE[user.role],
      },
    };
  }

  /**
   * Email + password is verified by Supabase Auth (GoTrue). The backend never
   * compares passwords itself and never mints tokens: the returned access
   * token is the real signed Supabase JWT.
   */
  async loginWithEmail(dto: EmailLoginDto): Promise<AuthResponse> {
    this.assertConfigured();
    const email = dto.email.trim().toLowerCase();
    const client = this.newUserClient();

    const { data, error } = await client.auth.signInWithPassword({
      email,
      password: dto.password,
    });
    if (error || !data.session || !data.user) {
      this.logger.warn(`Email sign-in failed for ${email}: ${error?.message ?? 'no session'}`);
      throw new UnauthorizedException('Invalid email or password.');
    }

    const user = await this.verifyPortalAccess(
      data.user.id,
      data.session.access_token,
      dto.role,
      data.user.email,
    );
    return this.buildResponse(
      data.session.access_token,
      data.session.refresh_token,
      data.session.expires_in,
      user,
    );
  }

  /**
   * Google ID token is verified by Supabase (which checks Google's signature,
   * audience and expiry). The client-supplied email is ignored.
   */
  async loginWithGoogle(dto: GoogleSignInDto): Promise<AuthResponse> {
    this.assertConfigured();
    const client = this.newUserClient();

    const { data, error } = await client.auth.signInWithIdToken({
      provider: 'google',
      token: dto.idToken,
    });
    if (error || !data.session || !data.user) {
      this.logger.warn(`Google sign-in failed: ${error?.message ?? 'no session'}`);
      throw new UnauthorizedException('Google sign-in could not be verified.');
    }

    const user = await this.verifyPortalAccess(
      data.user.id,
      data.session.access_token,
      dto.role,
      data.user.email,
    );
    return this.buildResponse(
      data.session.access_token,
      data.session.refresh_token,
      data.session.expires_in,
      user,
    );
  }

  private async verifyPortalAccess(
    authUserId: string,
    accessToken: string,
    requestedRole: RoleType,
    email?: string,
  ): Promise<AuthenticatedUser> {
    const user = await this.loadAuthenticatedUser(authUserId, accessToken, email);
    if (user.role !== requestedRole) {
      this.logger.warn(
        `Cross-role login blocked for ${email ?? authUserId}: registered ${user.roleSlug}, requested ${requestedRole}`,
      );
      throw new ForbiddenException(
        `This account is registered for a different portal (${user.roleSlug}).`,
      );
    }
    this.logger.log(`User ${authUserId} authenticated as ${user.roleSlug}`);
    return user;
  }

  /**
   * Backend-mediated demo authentication for hackathon evaluation.
   * Gated by DEMO_LOGIN_ENABLED. Accepts fixed code 123456.
   * Validates role against server-side account mappings; users cannot promote themselves.
   * Issues a signed, expiring JWT with facility binding.
   */
  async demoLogin(dto: DemoLoginDto): Promise<AuthResponse> {
    // Demo login is OFF unless explicitly enabled for this environment.
    const isDemoEnabled = (process.env.DEMO_LOGIN_ENABLED ?? 'false').toLowerCase() === 'true';
    if (!isDemoEnabled) {
      throw new ForbiddenException('Demo sign-in is disabled in this environment.');
    }

    // Fail closed: no demo token is ever signed with a published fallback.
    const tokenSecret = getDemoJwtSecret();
    if (!tokenSecret) {
      throw new ServiceUnavailableException(
        'Demo sign-in is not configured: DEMO_JWT_SECRET is missing.',
      );
    }

    const expectedOtp = process.env.DEMO_OTP_CODE || '123456';
    if (!dto.otp || dto.otp.trim() !== expectedOtp) {
      throw new UnauthorizedException('Invalid demo verification code.');
    }

    const rawPhone = (dto.phoneNumber ?? '').replace(/\D/g, '');
    const cleanPhone = rawPhone.length >= 10 ? rawPhone.slice(-10) : rawPhone;
    if (cleanPhone.length !== 10) {
      throw new UnauthorizedException('A valid 10-digit mobile number is required.');
    }

    // Server-side account mapping: phone:role pairs
    const rawAccounts =
      process.env.DEMO_ACCOUNTS || '9876543210:collector,9876543211:recycler,9876543212:admin';
    const accountMap = new Map<string, RoleType>();
    for (const entry of rawAccounts.split(/[,;\n]/)) {
      const trimmed = entry.trim();
      if (!trimmed.includes(':')) continue;
      const [p, r] = trimmed.split(':');
      const pClean = p.replace(/\D/g, '').slice(-10);
      const roleEnum = slugToRole(r);
      if (pClean && roleEnum) {
        accountMap.set(pClean, roleEnum);
      }
    }

    const configuredRole = accountMap.get(cleanPhone);
    const allowAdmin = (process.env.DEMO_ENABLE_ADMIN ?? 'false').toLowerCase() === 'true';

    let assignedRole: RoleType;
    if (configuredRole) {
      if (configuredRole === RoleType.GOVERNMENT_ADMIN && !allowAdmin) {
        throw new ForbiddenException('Admin demo portal is not enabled.');
      }
      assignedRole = configuredRole;
    } else {
      // Unlisted number cannot claim admin
      if (dto.role === RoleType.GOVERNMENT_ADMIN) {
        throw new ForbiddenException('Access denied: Demo admin requires an authorized admin number.');
      }
      assignedRole = dto.role || RoleType.INFORMAL_COLLECTOR;
    }

    // Role check if client requested a specific portal
    if (dto.role && dto.role !== assignedRole) {
      throw new ForbiddenException(
        `Access denied: This phone number is configured as ${roleToSqlSlug(assignedRole)}. You cannot access the ${roleToSqlSlug(dto.role)} portal.`,
      );
    }

    const roleSlug = roleToSqlSlug(assignedRole);

    // Demo identities are deliberately labelled as non-production so nothing in
    // the UI can be mistaken for a real CPCB registration or government office.
    // The recycler facility is carried separately (server-issued claim) because
    // a facility id is NOT a statutory identifier.
    const statutoryIdentifier =
      assignedRole === RoleType.FORMAL_RECYCLER
        ? DEMO_RECYCLER_FACILITY_ID
        : assignedRole === RoleType.GOVERNMENT_ADMIN
        ? 'DEMO-ADMIN-NOT-AUTHORISED'
        : `DEMO-AGG-${cleanPhone.slice(-6)}`;

    const facilityId =
      assignedRole === RoleType.FORMAL_RECYCLER ? DEMO_RECYCLER_FACILITY_ID : undefined;

    const entityName =
      assignedRole === RoleType.FORMAL_RECYCLER
        ? 'Demo Recycling Facility (not CPCB verified)'
        : assignedRole === RoleType.GOVERNMENT_ADMIN
        ? 'Demo Administrator (not a government account)'
        : 'Demo E-Waste Aggregator (local demo)';

    const displayName =
      assignedRole === RoleType.FORMAL_RECYCLER
        ? 'Demo Recycler Manager'
        : assignedRole === RoleType.GOVERNMENT_ADMIN
        ? 'Demo CPCB Admin'
        : 'Demo Informal Collector';

    const userId = `demo-${cleanPhone}-${roleSlug}`;
    const email = `${cleanPhone}@ecobridges.demo`;

    const nowSeconds = Math.floor(Date.now() / 1000);
    const expiresInSeconds = 12 * 3600; // 12 hours

    const payload = {
      sub: userId,
      email,
      phone: `+91 ${cleanPhone}`,
      role: assignedRole,
      roleSlug,
      accountStatus: AccountStatus.ACTIVE,
      statutoryIdentifier,
      facilityId,
      entityName,
      displayName,
      isDemo: true,
      iss: 'ecobridges-backend',
      aud: 'authenticated',
      iat: nowSeconds,
      exp: nowSeconds + expiresInSeconds,
    };

    const token = signDemoJwt(payload, tokenSecret);

    const user: AuthenticatedUser = {
      id: userId,
      email,
      role: assignedRole,
      roleSlug,
      accountStatus: AccountStatus.ACTIVE,
      displayName,
      entityName,
      statutoryIdentifier,
      phoneNumber: `+91 ${cleanPhone}`,
      permissions: PERMISSIONS_BY_ROLE[assignedRole] || [],
      isDemo: true,
      facilityId,
    };

    this.logger.log(`Demo login granted for +91 ${cleanPhone} as ${roleSlug} (demo identity)`);

    return this.buildResponse(token, undefined, expiresInSeconds, user);
  }

  /**
   * There is no SMS gateway configured. These endpoints must never fabricate
   * delivery or tokens; they return 501 until a real provider is wired in.
   * (The Android app labels its on-device code as Demo OTP instead.)
   */
  async requestMobileOtp(_dto: RequestOtpDto): Promise<never> {
    throw new NotImplementedException(
      'Real SMS OTP is not configured. Use email/password or Google sign-in, or configure a phone auth provider.',
    );
  }

  async verifyMobileOtp(_dto: VerifyOtpDto): Promise<never> {
    throw new NotImplementedException(
      'Real SMS OTP is not configured. This endpoint cannot issue sessions.',
    );
  }
}
