import { Injectable } from '@nestjs/common';
import { RoleType } from '../auth/dto/auth.dto';

export interface ReferencePriceInput {
  priceId?: string;
  category: string;
  materialName: string;
  grade?: string;
  pricePerKg: number;
  marketMin: number;
  marketMax: number;
  currency?: string;
  effectiveDate?: string | null;
  isActive?: boolean;
  baseVersion?: number;
}

export interface OfferInput {
  offerId?: string;
  recyclerId: string;
  recyclerName?: string;
  category: string;
  materialName: string;
  grade?: string;
  pricePerKg: number;
  currency?: string;
  minQuantityKg?: number | null;
  maxQuantityKg?: number | null;
  condition?: string;
  status?: string;
  notes?: string;
  baseVersion?: number;
}

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}

const ALLOWED_CURRENCIES = new Set(['INR']);
const ALLOWED_OFFER_STATUSES = new Set(['DRAFT', 'ACTIVE', 'PAUSED', 'EXPIRED']);

/**
 * Pure price-management rules mirrored from the Android
 * `PriceManagementPolicy`. The database (RLS + constraints in
 * `0008_price_management.sql`) remains the final authority; this service lets
 * an authorised client validate before writing and gives tests a single place
 * to assert the rules.
 */
@Injectable()
export class PricePolicyService {
  static readonly SIGNIFICANT_CHANGE_PERCENT = 20;

  canManageReferencePrices(role: RoleType | string | null | undefined): boolean {
    return role === RoleType.GOVERNMENT_ADMIN;
  }

  canManageOffers(role: RoleType | string | null | undefined): boolean {
    return role === RoleType.FORMAL_RECYCLER;
  }

  ownsOffer(offerRecyclerId: string, actorRecyclerId: string): boolean {
    if (!offerRecyclerId || !actorRecyclerId) return false;
    return offerRecyclerId.trim().toLowerCase() === actorRecyclerId.trim().toLowerCase();
  }

  validateReferencePrice(input: ReferencePriceInput): ValidationResult {
    const errors: string[] = [];
    if (!input.materialName || !input.materialName.trim()) {
      errors.push('Material name is required.');
    }
    if (!Number.isFinite(input.pricePerKg) || input.pricePerKg < 0) {
      errors.push('Price per kg must be a non-negative number.');
    }
    if (!Number.isFinite(input.marketMin) || input.marketMin < 0) {
      errors.push('Minimum price must be a non-negative number.');
    }
    if (!Number.isFinite(input.marketMax) || input.marketMax < 0) {
      errors.push('Maximum price must be a non-negative number.');
    }
    if (
      Number.isFinite(input.marketMin) &&
      Number.isFinite(input.marketMax) &&
      input.marketMin > input.marketMax
    ) {
      errors.push('Minimum price cannot be greater than the maximum price.');
    }
    if (!ALLOWED_CURRENCIES.has((input.currency ?? 'INR').toUpperCase())) {
      errors.push('Only INR is supported.');
    }
    if ((input.materialName ?? '').length > 120) {
      errors.push('Material name is too long.');
    }
    if ((input.grade ?? '').length > 60) {
      errors.push('Grade is too long.');
    }
    return { valid: errors.length === 0, errors };
  }

  validateOffer(input: OfferInput): ValidationResult {
    const errors: string[] = [];
    if (!input.materialName || !input.materialName.trim()) {
      errors.push('Material name is required.');
    }
    if (!Number.isFinite(input.pricePerKg) || input.pricePerKg <= 0) {
      errors.push('Offered price per kg must be greater than zero.');
    }
    const min = input.minQuantityKg ?? null;
    const max = input.maxQuantityKg ?? null;
    if (min !== null && (!Number.isFinite(min) || min < 0)) {
      errors.push('Minimum quantity must be a non-negative number.');
    }
    if (max !== null && (!Number.isFinite(max) || max < 0)) {
      errors.push('Maximum quantity must be a non-negative number.');
    }
    if (min !== null && max !== null && min > max) {
      errors.push('Minimum quantity cannot be greater than the maximum quantity.');
    }
    if (!ALLOWED_OFFER_STATUSES.has((input.status ?? 'ACTIVE').toUpperCase())) {
      errors.push('Offer status must be DRAFT, ACTIVE, PAUSED or EXPIRED.');
    }
    if (!ALLOWED_CURRENCIES.has((input.currency ?? 'INR').toUpperCase())) {
      errors.push('Only INR is supported.');
    }
    return { valid: errors.length === 0, errors };
  }

  isSignificantChange(oldPrice: number | null | undefined, newPrice: number): boolean {
    if (oldPrice === null || oldPrice === undefined) return false;
    if (!Number.isFinite(oldPrice) || !Number.isFinite(newPrice) || oldPrice <= 0) return false;
    const percent = (Math.abs(newPrice - oldPrice) / oldPrice) * 100;
    return percent >= PricePolicyService.SIGNIFICANT_CHANGE_PERCENT;
  }

  canApplyChange(baseVersion: number, serverVersion: number | null | undefined): boolean {
    if (serverVersion === null || serverVersion === undefined) return baseVersion <= 0;
    return baseVersion === serverVersion;
  }

  canChangeAgreedPrice(existingAgreedRate: number | null | undefined, proposedRate: number): boolean {
    if (existingAgreedRate === null || existingAgreedRate === undefined) return true;
    return existingAgreedRate === proposedRate;
  }
}
