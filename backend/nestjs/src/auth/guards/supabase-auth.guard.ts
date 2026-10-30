import {
  CanActivate,
  ExecutionContext,
  Injectable,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import * as crypto from 'crypto';
import { AuthService, AuthenticatedUser, PERMISSIONS_BY_ROLE, getDemoJwtSecret, slugToRole } from '../auth.service';
import { AccountStatus } from '../dto/auth.dto';

interface JwtHeader {
  alg?: string;
  kid?: string;
  typ?: string;
}

interface JwtPayload {
  sub?: string;
  email?: string;
  exp?: number;
  nbf?: number;
  iat?: number;
  iss?: string;
  aud?: string | string[];
  role?: string;
  roleSlug?: string;
  isDemo?: boolean;
  statutoryIdentifier?: string;
  facilityId?: string;
  entityName?: string;
  displayName?: string;
  accountStatus?: string;
}

interface Jwk {
  kty: string;
  kid?: string;
  use?: string;
  alg?: string;
  n?: string;
  e?: string;
  crv?: string;
  x?: string;
  y?: string;
}

const JWKS_TTL_MS = 5 * 60 * 1000;
const CLOCK_SKEW_SECONDS = 30;

function base64UrlDecode(value: string): Buffer {
  return Buffer.from(value.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
}

/**
 * Verifies the Supabase access token (JWT) or backend-issued demo session JWT
 * before any controller runs.
 *
 * - Asymmetric tokens (RS256 / ES256) are verified against the project JWKS.
 * - Legacy HS256 tokens are verified only when SUPABASE_JWT_SECRET is set.
 * - Backend demo tokens (HS256) are verified using DEMO_JWT_SECRET when demo login is enabled.
 * - Issuer, audience, expiry and not-before are checked.
 * - The role is resolved server-side, so a token cannot be forged with an unauthorized role.
 */
@Injectable()
export class SupabaseAuthGuard implements CanActivate {
  private readonly logger = new Logger(SupabaseAuthGuard.name);
  private jwksCache: Jwk[] | null = null;
  private jwksFetchedAt = 0;

  private readonly supabaseUrl = (process.env.SUPABASE_URL ?? '').replace(/\/+$/, '');
  private readonly jwksUrl =
    process.env.SUPABASE_JWKS_URL ||
    (this.supabaseUrl ? `${this.supabaseUrl}/auth/v1/.well-known/jwks.json` : '');
  private readonly issuer =
    process.env.SUPABASE_JWT_ISSUER || (this.supabaseUrl ? `${this.supabaseUrl}/auth/v1` : '');
  private readonly audience = process.env.SUPABASE_JWT_AUDIENCE || 'authenticated';

  constructor(private readonly authService: AuthService) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest();
    const header = request.headers?.['authorization'];
    if (typeof header !== 'string' || !header.startsWith('Bearer ')) {
      throw new UnauthorizedException('Missing bearer token.');
    }

    const token = header.slice('Bearer '.length).trim();
    if (!token) {
      throw new UnauthorizedException('Missing bearer token.');
    }

    const payload = await this.verifyToken(token);
    if (!payload.sub) {
      throw new UnauthorizedException('Token has no subject.');
    }

    // Backend-verified demo session
    if (payload.isDemo === true && payload.iss === 'ecobridges-backend') {
      const isDemoEnabled = (process.env.DEMO_LOGIN_ENABLED ?? 'false').toLowerCase() === 'true';
      if (!isDemoEnabled) {
        throw new UnauthorizedException('Demo session is disabled in this environment.');
      }
      const role = slugToRole(payload.roleSlug) || (payload.role as any);
      if (!role) {
        throw new UnauthorizedException('Demo token has no recognized role.');
      }
      request.user = {
        id: payload.sub,
        email: payload.email,
        role: role,
        roleSlug: payload.roleSlug,
        accountStatus: AccountStatus.ACTIVE,
        displayName: payload.displayName || 'Demo User',
        statutoryIdentifier: payload.statutoryIdentifier || '',
        entityName: payload.entityName || '',
        permissions: PERMISSIONS_BY_ROLE[role] || [],
        isDemo: true,
        facilityId: payload.facilityId,
        accessToken: token,
      };
      return true;
    }

    const user: AuthenticatedUser = await this.authService.loadAuthenticatedUser(
      payload.sub,
      token,
      payload.email,
    );

    request.user = {
      id: user.id,
      email: user.email,
      role: user.role,
      roleSlug: user.roleSlug,
      accountStatus: user.accountStatus,
      displayName: user.displayName,
      statutoryIdentifier: user.statutoryIdentifier,
      entityName: user.entityName,
      permissions: user.permissions,
      isDemo: false,
      accessToken: token,
    };
    return true;
  }

  private async verifyToken(token: string): Promise<JwtPayload> {
    const parts = token.split('.');
    if (parts.length !== 3) {
      throw new UnauthorizedException('Malformed token.');
    }

    let header: JwtHeader;
    let payload: JwtPayload;
    try {
      header = JSON.parse(base64UrlDecode(parts[0]).toString('utf8'));
      payload = JSON.parse(base64UrlDecode(parts[1]).toString('utf8'));
    } catch {
      throw new UnauthorizedException('Malformed token.');
    }

    const signingInput = Buffer.from(`${parts[0]}.${parts[1]}`, 'utf8');
    const signature = base64UrlDecode(parts[2]);

    const algorithm = header.alg ?? '';
    if (!['RS256', 'ES256', 'HS256'].includes(algorithm)) {
      throw new UnauthorizedException('Unsupported token algorithm.');
    }

    if (algorithm === 'HS256') {
      const isDemoToken = payload.iss === 'ecobridges-backend';
      // A token claiming to be a demo session must actually be issued by the
      // backend. Anything else falls through to Supabase-secret verification.
      if (payload.isDemo === true && !isDemoToken) {
        throw new UnauthorizedException('Invalid demo token issuer.');
      }

      const demoSecret = getDemoJwtSecret();
      const supabaseSecret = process.env.SUPABASE_JWT_SECRET;

      let valid = false;
      if (isDemoToken) {
        if (demoSecret) {
          const expected = crypto.createHmac('sha256', demoSecret).update(signingInput).digest();
          if (expected.length === signature.length && crypto.timingSafeEqual(expected, signature)) {
            valid = true;
          }
        }
      } else if (supabaseSecret) {
        const expected = crypto.createHmac('sha256', supabaseSecret).update(signingInput).digest();
        if (expected.length === signature.length && crypto.timingSafeEqual(expected, signature)) {
          valid = true;
        }
      }

      if (!valid) {
        if (isDemoToken && !demoSecret) {
          throw new UnauthorizedException('Demo token verification is not configured.');
        }
        if (!isDemoToken && !supabaseSecret) {
          throw new UnauthorizedException('Token algorithm is not accepted.');
        }
        throw new UnauthorizedException('Invalid token signature.');
      }
    } else {
      const jwk = await this.findKey(header.kid, algorithm);
      let verified = false;
      try {
        const publicKey = crypto.createPublicKey({ key: jwk as crypto.JsonWebKey, format: 'jwk' });
        verified = crypto.verify(
          algorithm === 'RS256' ? 'RSA-SHA256' : 'SHA256',
          signingInput,
          algorithm === 'ES256'
            ? { key: publicKey, dsaEncoding: 'ieee-p1363' }
            : publicKey,
          signature,
        );
      } catch (error) {
        this.logger.warn(`Token verification error: ${(error as Error).message}`);
        verified = false;
      }
      if (!verified) {
        throw new UnauthorizedException('Invalid token signature.');
      }
    }

    this.validateClaims(payload);
    return payload;
  }

  private validateClaims(payload: JwtPayload): void {
    const now = Math.floor(Date.now() / 1000);
    if (typeof payload.exp !== 'number' || payload.exp + CLOCK_SKEW_SECONDS < now) {
      throw new UnauthorizedException('Token has expired.');
    }
    if (typeof payload.nbf === 'number' && payload.nbf - CLOCK_SKEW_SECONDS > now) {
      throw new UnauthorizedException('Token is not valid yet.');
    }
    if (this.issuer && payload.iss !== this.issuer && payload.iss !== 'ecobridges-backend') {
      throw new UnauthorizedException('Invalid token issuer.');
    }
    const audiences = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
    if (this.audience && !audiences.includes(this.audience)) {
      throw new UnauthorizedException('Invalid token audience.');
    }
  }

  private async findKey(kid: string | undefined, algorithm: string): Promise<Jwk> {
    let keys = await this.getJwks(false);
    let match = this.selectKey(keys, kid, algorithm);
    if (!match) {
      // Key rotation: refresh once before giving up.
      keys = await this.getJwks(true);
      match = this.selectKey(keys, kid, algorithm);
    }
    if (!match) {
      throw new UnauthorizedException('No matching signing key.');
    }
    return match;
  }

  private selectKey(keys: Jwk[], kid: string | undefined, algorithm: string): Jwk | null {
    const usable = keys.filter((key) => {
      const ktyMatches = algorithm === 'RS256' ? key.kty === 'RSA' : key.kty === 'EC';
      const algMatches = !key.alg || key.alg === algorithm;
      return ktyMatches && algMatches;
    });
    if (kid) {
      return usable.find((key) => key.kid === kid) ?? null;
    }
    return usable.length === 1 ? usable[0] : null;
  }

  private async getJwks(forceRefresh: boolean): Promise<Jwk[]> {
    const fresh = Date.now() - this.jwksFetchedAt < JWKS_TTL_MS;
    if (!forceRefresh && fresh && this.jwksCache) {
      return this.jwksCache;
    }
    if (!this.jwksUrl) {
      throw new UnauthorizedException('JWKS endpoint is not configured.');
    }
    try {
      const response = await fetch(this.jwksUrl, {
        headers: { Accept: 'application/json' },
        signal: AbortSignal.timeout(5000),
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const body = (await response.json()) as { keys?: Jwk[] };
      if (!Array.isArray(body.keys) || body.keys.length === 0) {
        throw new Error('empty JWKS');
      }
      this.jwksCache = body.keys;
      this.jwksFetchedAt = Date.now();
      return body.keys;
    } catch (error) {
      this.logger.error(`Could not load JWKS: ${(error as Error).message}`);
      // Fail closed. A stale cache is still better than no verification, but
      // only if it was fetched before.
      if (this.jwksCache) return this.jwksCache;
      throw new UnauthorizedException('Unable to verify token signature.');
    }
  }
}
