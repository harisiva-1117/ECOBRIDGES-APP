import {
  ConflictException,
  ForbiddenException,
  Injectable,
  Logger,
  ServiceUnavailableException,
} from '@nestjs/common';
import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { OfferDto, ReferencePriceDto } from './dto/price.dto';
import { PricePolicyService } from './price-policy.service';

export interface SavedOfferRecord {
  offerId: string;
  rateId: string;
  recyclerId: string;
  recyclerName?: string;
  category: string;
  materialName: string;
  grade: string;
  pricePerKg: number;
  currency: string;
  minQuantityKg?: number | null;
  maxQuantityKg?: number | null;
  condition: string;
  status: string;
  notes: string;
  updatedAt: string;
  updatedBy: string;
  version: number;
}

export interface SavedReferencePriceRecord {
  priceId: string;
  category: string;
  materialName: string;
  grade: string;
  pricePerKg: number;
  marketMin: number;
  marketMax: number;
  currency: string;
  effectiveDate: string;
  isActive: boolean;
  updatedAt: string;
  updatedBy: string;
  updatedByLabel: string;
  version: number;
}

export interface PriceHistoryRecord {
  historyId: number | string;
  priceId: string;
  category: string;
  materialName: string;
  grade?: string;
  pricePerKg: number;
  marketMin: number;
  marketMax: number;
  currency: string;
  effectiveDate: string;
  isActive: boolean;
  version: number;
  changeType: string;
  changedBy?: string;
  changedByLabel?: string;
  changedAt: string;
}

/** Identity fields a controller may pass when resolving a caller's facility. */
export interface ActorIdentity {
  userId?: string;
  email?: string;
  statutoryIdentifier?: string;
  facilityId?: string;
  isDemo?: boolean;
}

/**
 * Known registry rows used only when Supabase is not configured (local tests).
 * The live database is always the authority when it is available.
 */
const KNOWN_RECYCLERS: Array<{ id: string; cpcbRegNo: string }> = [
  { id: 'REC-CPCB-MH-001', cpcbRegNo: 'CPCB/EPR-REC/2023/MH-0042' },
  { id: 'REC-CPCB-MH-002', cpcbRegNo: 'CPCB/EPR-REC/2022/MH-0118' },
  { id: 'REC-CPCB-MH-003', cpcbRegNo: 'CPCB/EPR-REC/2024/MH-0205' },
  { id: 'REC-CPCB-MH-004', cpcbRegNo: 'CPCB/EPR-REC/2023/MH-0091' },
];

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Audit columns are UUID-typed; demo actor ids are not UUIDs, so store null. */
export function asUuidOrNull(value: string | null | undefined): string | null {
  if (!value) return null;
  return UUID_RE.test(value.trim()) ? value.trim() : null;
}

function sameNumber(a: unknown, b: unknown): boolean {
  return Number(a ?? 0) === Number(b ?? 0);
}

@Injectable()
export class PricesService {
  private readonly logger = new Logger(PricesService.name);
  private readonly supabaseUrl: string;
  private readonly clientKey: string;
  private readonly supabase: SupabaseClient | null = null;

  // In-memory fallback cache when live Supabase connection is not provisioned
  private inMemoryOffers: Map<string, SavedOfferRecord> = new Map();
  private inMemoryPrices: Map<string, SavedReferencePriceRecord> = new Map();
  private inMemoryHistory: PriceHistoryRecord[] = [];

  constructor(private readonly policy: PricePolicyService) {
    this.supabaseUrl = (process.env.SUPABASE_URL ?? '').replace(/\/+$/, '');
    // A service-role key is only used for the server-side write path. Without
    // it the service still works in local/in-memory mode (never as a privilege
    // escalation for clients).
    this.clientKey = process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_ANON_KEY || '';

    if (this.supabaseUrl && this.clientKey) {
      this.supabase = createClient(this.supabaseUrl, this.clientKey, {
        auth: { persistSession: false, autoRefreshToken: false },
      });
      this.logger.log('PricesService initialized with Supabase integration.');
    } else {
      this.logger.warn(
        'SUPABASE_URL / key not configured. PricesService using in-memory fallback for local verification.',
      );
      this.seedInMemoryDefaults();
    }
  }

  private seedInMemoryDefaults() {
    // Seed default baseline reference prices
    const seed = [
      { id: 'PR-PCB-01', cat: 'PCB_BOARDS', name: 'High-Grade Telecom & PC Motherboards', rate: 380, min: 340, max: 420 },
      { id: 'PR-CAB-02', cat: 'CABLES_WIRES', name: 'Stripped & Insulated Copper Harness', rate: 460, min: 430, max: 490 },
      { id: 'PR-BAT-03', cat: 'BATTERIES', name: 'Lithium-Ion Phone & Laptop Packs', rate: 160, min: 135, max: 185 },
      { id: 'PR-MOT-04', cat: 'MOTORS_MAGNETS', name: 'Hard Disk & Speaker Neodymium Assemblies', rate: 210, min: 190, max: 230 },
      { id: 'PR-CRT-05', cat: 'CRTS_MONITORS', name: 'Whole Sealed CRT Monitors (Intact)', rate: 45, min: 35, max: 55 },
      { id: 'PR-LCD-06', cat: 'LCD_PANELS', name: 'Flat Screen Displays & Laptops', rate: 120, min: 100, max: 140 },
      { id: 'PR-PLS-07', cat: 'MIXED_PLASTICS', name: 'Computer Housings ABS / Polycarbonate', rate: 32, min: 28, max: 36 },
    ];
    for (const item of seed) {
      this.inMemoryPrices.set(item.id, {
        priceId: item.id,
        category: item.cat,
        materialName: item.name,
        grade: 'Standard Grade',
        pricePerKg: item.rate,
        marketMin: item.min,
        marketMax: item.max,
        currency: 'INR',
        effectiveDate: new Date().toISOString().split('T')[0],
        isActive: true,
        updatedAt: new Date().toISOString(),
        updatedBy: 'system-seed',
        updatedByLabel: 'CPCB Baseline Seed',
        version: 1,
      });
    }

    // Seed default recycler offers for the demo facility.
    const defaultOffers = [
      { id: 'RATE-001-PCB', cat: 'PCB_BOARDS', name: 'High-Grade Telecom & PC Motherboards', rate: 380 },
      { id: 'RATE-001-CAB', cat: 'CABLES_WIRES', name: 'Stripped & Insulated Copper Harness', rate: 460 },
      { id: 'RATE-001-BAT', cat: 'BATTERIES', name: 'Lithium-Ion Phone & Laptop Packs', rate: 160 },
      { id: 'RATE-001-MOT', cat: 'MOTORS_MAGNETS', name: 'Hard Disk & Speaker Neodymium Assemblies', rate: 210 },
      { id: 'RATE-001-LCD', cat: 'LCD_PANELS', name: 'Flat Screen Displays & Laptops', rate: 120 },
    ];
    for (const off of defaultOffers) {
      this.inMemoryOffers.set(off.id, {
        offerId: off.id,
        rateId: off.id,
        recyclerId: 'REC-CPCB-MH-001',
        recyclerName: 'Demo Recycling Facility (not CPCB verified)',
        category: off.cat,
        materialName: off.name,
        grade: 'High Grade',
        pricePerKg: off.rate,
        currency: 'INR',
        minQuantityKg: 10,
        maxQuantityKg: 500,
        condition: 'SEGREGATED',
        status: 'ACTIVE',
        notes: 'Doorstep pickup available for lots above 25kg',
        updatedAt: new Date().toISOString(),
        updatedBy: 'Demo Recycler Manager',
        version: 1,
      });
    }
  }

  /**
   * Resolve the caller's own `authorized_recyclers.recycler_id` on the server.
   *
   * - Real sessions: matched by CPCB registration number (the profile's
   *   statutory identifier) or contact email — never a client-supplied id.
   * - Demo sessions: the facility is a server-issued signed claim. When a
   *   database is configured it is re-validated against the registry, so a
   *   deleted/suspended facility cannot keep writing.
   */
  async resolveRecyclerFacilityId(actor: ActorIdentity): Promise<string | null> {
    const statutory = (actor.statutoryIdentifier ?? '').trim();
    const email = (actor.email ?? '').trim();
    const facility = (actor.facilityId ?? '').trim();

    if (actor.isDemo) {
      const claimed = facility || statutory;
      if (!claimed) return null;
      if (!this.supabase) {
        return KNOWN_RECYCLERS.some((r) => r.id === claimed || r.cpcbRegNo.toLowerCase() === claimed.toLowerCase())
          ? claimed
          : (claimed.startsWith('REC-') ? claimed : null);
      }
      const { data, error } = await this.supabase
        .from('authorized_recyclers')
        .select('recycler_id')
        .eq('recycler_id', claimed)
        .eq('authorization_status', 'active')
        .maybeSingle();
      if (error) {
        this.logger.warn(`Demo facility lookup failed: ${error.message}`);
        return null;
      }
      return data?.recycler_id ?? null;
    }

    if (!this.supabase) {
      const target = statutory || facility;
      const match = KNOWN_RECYCLERS.find(
        (r) =>
          (target !== '' && (r.cpcbRegNo.toLowerCase() === target.toLowerCase() || r.id.toLowerCase() === target.toLowerCase())) ||
          (facility !== '' && r.id.toLowerCase() === facility.toLowerCase()),
      );
      return match?.id ?? (target.startsWith('REC-') ? target : null);
    }

    try {
      if (statutory) {
        const { data, error } = await this.supabase
          .from('authorized_recyclers')
          .select('recycler_id')
          .eq('cpcb_reg_no', statutory)
          .eq('authorization_status', 'active')
          .maybeSingle();
        if (error) throw error;
        if (data?.recycler_id) return data.recycler_id;
      }
      if (email) {
        const { data, error } = await this.supabase
          .from('authorized_recyclers')
          .select('recycler_id')
          .ilike('contact_email', email)
          .eq('authorization_status', 'active')
          .maybeSingle();
        if (error) throw error;
        if (data?.recycler_id) return data.recycler_id;
      }
    } catch (err) {
      this.logger.warn(`Facility resolution failed: ${(err as Error).message}`);
      return null;
    }
    return null;
  }

  private mapOfferRow(row: any, fallback?: { recyclerName?: string }): SavedOfferRecord {
    return {
      offerId: row.offer_id || row.rate_id,
      rateId: row.rate_id,
      recyclerId: row.recycler_id,
      recyclerName: fallback?.recyclerName,
      category: row.category_name,
      materialName: row.material_name || row.category_name,
      grade: row.grade || '',
      pricePerKg: Number(row.rate_per_kg),
      currency: row.currency || 'INR',
      minQuantityKg: row.min_quantity_kg ?? null,
      maxQuantityKg: row.max_quantity_kg ?? null,
      condition: row.condition || 'ACCEPTED',
      status: row.status || 'ACTIVE',
      notes: row.notes || '',
      updatedAt: row.updated_at || new Date().toISOString(),
      updatedBy: row.updated_by_label || row.updated_by || '',
      version: Number(row.version || 1),
    };
  }

  private mapPriceRow(row: any): SavedReferencePriceRecord {
    return {
      priceId: row.price_id,
      category: row.category_name,
      materialName: row.sub_category,
      grade: row.grade || '',
      pricePerKg: Number(row.prevailing_buy_rate),
      marketMin: Number(row.market_min),
      marketMax: Number(row.market_max),
      currency: row.currency || 'INR',
      effectiveDate: row.effective_date || '',
      isActive: row.is_active !== false,
      updatedAt: row.updated_at || '',
      updatedBy: row.updated_by || '',
      updatedByLabel: row.updated_by_label || '',
      version: Number(row.version || 1),
    };
  }

  // =========================================================================
  // RECYCLER OFFERS PERSISTENCE
  // =========================================================================

  async saveOffer(
    dto: OfferDto,
    actorRecyclerId: string,
    actorLabel: string,
    actorUserId?: string,
  ): Promise<SavedOfferRecord> {
    if (!actorRecyclerId) {
      throw new ForbiddenException('Your recycler identity could not be resolved.');
    }
    if (!this.policy.ownsOffer(dto.recyclerId, actorRecyclerId)) {
      throw new ForbiddenException('You can only publish offers for your own facility.');
    }

    const validation = this.policy.validateOffer(dto);
    if (!validation.valid) {
      throw new ForbiddenException(`Validation failed: ${validation.errors.join(', ')}`);
    }

    const offerId = dto.offerId || `OFFER-${actorRecyclerId}-${dto.category}`;

    if (this.supabase) {
      try {
        // The uniqueness key is (recycler_id, category_name). Query it first,
        // then honour an explicit offer/rate id if one was supplied.
        const { data: byCategory, error: categoryErr } = await this.supabase
          .from('recycler_offered_rates')
          .select('*')
          .eq('recycler_id', actorRecyclerId)
          .eq('category_name', dto.category)
          .maybeSingle();
        if (categoryErr) {
          this.logger.warn(`Offer lookup error from Supabase: ${categoryErr.message}`);
        }

        let existing: any = byCategory ?? null;
        if (!existing && dto.offerId) {
          const { data: byId } = await this.supabase
            .from('recycler_offered_rates')
            .select('*')
            .eq('rate_id', dto.offerId)
            .maybeSingle();
          existing = byId ?? null;
        }

        if (existing) {
          if (String(existing.recycler_id).toLowerCase() !== actorRecyclerId.toLowerCase()) {
            throw new ForbiddenException('You can only edit offers for your own facility.');
          }

          const requestedBase = Number(dto.baseVersion ?? 0);
          if (requestedBase > 0) {
            if (Number(existing.version) !== requestedBase) {
              throw new ConflictException(
                `Conflict: This offer was updated on the server (server version: ${existing.version}, your base: ${requestedBase}). Please reload.`,
              );
            }
          } else if (this.offerMatches(existing, dto, actorRecyclerId)) {
            // Idempotent retry of a create whose response was lost.
            return this.mapOfferRow(existing, { recyclerName: dto.recyclerName });
          } else {
            throw new ConflictException(
              `An offer for ${dto.category} already exists for this facility. Reload and edit it.`,
            );
          }

          const nowIso = new Date().toISOString();
          const updateRow = {
            material_name: dto.materialName,
            grade: dto.grade ?? '',
            rate_per_kg: dto.pricePerKg,
            unit: '₹/kg',
            currency: dto.currency ?? 'INR',
            min_quantity_kg: dto.minQuantityKg ?? null,
            max_quantity_kg: dto.maxQuantityKg ?? null,
            condition: dto.condition ?? 'ACCEPTED',
            status: (dto.status ?? 'ACTIVE').toUpperCase(),
            notes: dto.notes ?? '',
            updated_at: nowIso,
            updated_by: asUuidOrNull(actorUserId),
            updated_by_label: actorLabel,
            version: Number(existing.version) + 1,
          };

          const { data: updated, error: updateErr } = await this.supabase
            .from('recycler_offered_rates')
            .update(updateRow)
            .eq('rate_id', existing.rate_id)
            .eq('version', existing.version)
            .select();

          if (updateErr) {
            this.logger.error(`Supabase offer update failed: ${updateErr.message}`);
            throw new ServiceUnavailableException('Failed to persist offer to cloud database.');
          }
          if (!updated || updated.length === 0) {
            const { data: current } = await this.supabase
              .from('recycler_offered_rates')
              .select('version')
              .eq('rate_id', existing.rate_id)
              .maybeSingle();
            throw new ConflictException(
              `Conflict: This offer was updated on the server (server version: ${current?.version ?? 'unknown'}). Please reload.`,
            );
          }

          const record = this.mapOfferRow(updated[0], { recyclerName: dto.recyclerName });
          this.inMemoryOffers.set(record.offerId, record);
          return record;
        }

        // Insert. Version starts at 1; unique (recycler, category) protects
        // against two concurrent creates.
        const nowIso = new Date().toISOString();
        const insertRow = {
          rate_id: offerId,
          offer_id: offerId,
          recycler_id: actorRecyclerId,
          category_name: dto.category,
          material_name: dto.materialName,
          grade: dto.grade ?? '',
          rate_per_kg: dto.pricePerKg,
          unit: '₹/kg',
          currency: dto.currency ?? 'INR',
          min_quantity_kg: dto.minQuantityKg ?? null,
          max_quantity_kg: dto.maxQuantityKg ?? null,
          condition: dto.condition ?? 'ACCEPTED',
          status: (dto.status ?? 'ACTIVE').toUpperCase(),
          notes: dto.notes ?? '',
          effective_from: nowIso,
          updated_at: nowIso,
          updated_by: asUuidOrNull(actorUserId),
          updated_by_label: actorLabel,
          version: 1,
        };

        const { data: inserted, error: insertErr } = await this.supabase
          .from('recycler_offered_rates')
          .insert(insertRow)
          .select()
          .single();

        if (insertErr || !inserted) {
          if (insertErr?.code === '23505') {
            throw new ConflictException(
              `An offer for ${dto.category} already exists for this facility. Reload and edit it.`,
            );
          }
          this.logger.error(`Supabase offer insert failed: ${insertErr?.message}`);
          throw new ServiceUnavailableException('Failed to persist offer to cloud database.');
        }

        const record = this.mapOfferRow(inserted, { recyclerName: dto.recyclerName });
        this.inMemoryOffers.set(record.offerId, record);
        return record;
      } catch (err) {
        if (
          err instanceof ConflictException ||
          err instanceof ForbiddenException ||
          err instanceof ServiceUnavailableException
        ) {
          throw err;
        }
        this.logger.error(`Live Supabase error: ${(err as Error).message}`);
        throw new ServiceUnavailableException(`Cloud persistence error: ${(err as Error).message}`);
      }
    }

    // Fallback in-memory (local verification only)
    const existing = this.inMemoryOffers.get(offerId);
    if (existing) {
      if (dto.baseVersion !== undefined && dto.baseVersion > 0) {
        if (existing.version !== dto.baseVersion) {
          throw new ConflictException(
            `Conflict: This offer was updated on the server (server version: ${existing.version}, your base: ${dto.baseVersion}). Please reload.`,
          );
        }
      } else if (this.inMemoryOfferMatches(existing, dto, actorRecyclerId)) {
        return existing;
      } else {
        throw new ConflictException(
          `An offer for ${dto.category} already exists for this facility. Reload and edit it.`,
        );
      }
    }

    const version = (existing?.version || 0) + 1;
    const record: SavedOfferRecord = {
      offerId,
      rateId: existing?.rateId ?? offerId,
      recyclerId: actorRecyclerId,
      recyclerName: dto.recyclerName || 'Demo Recycling Facility (not CPCB verified)',
      category: dto.category,
      materialName: dto.materialName,
      grade: dto.grade || 'Standard',
      pricePerKg: dto.pricePerKg,
      currency: dto.currency || 'INR',
      minQuantityKg: dto.minQuantityKg ?? null,
      maxQuantityKg: dto.maxQuantityKg ?? null,
      condition: dto.condition || 'ACCEPTED',
      status: (dto.status || 'ACTIVE').toUpperCase(),
      notes: dto.notes || '',
      updatedAt: new Date().toISOString(),
      updatedBy: actorLabel,
      version,
    };
    this.inMemoryOffers.set(offerId, record);
    return record;
  }

  private offerMatches(row: any, dto: OfferDto, actorRecyclerId: string): boolean {
    return (
      String(row.recycler_id).toLowerCase() === actorRecyclerId.toLowerCase() &&
      String(row.category_name) === dto.category &&
      sameNumber(row.rate_per_kg, dto.pricePerKg) &&
      String(row.material_name ?? '') === dto.materialName &&
      String(row.status ?? 'ACTIVE').toUpperCase() === (dto.status ?? 'ACTIVE').toUpperCase()
    );
  }

  private inMemoryOfferMatches(
    existing: SavedOfferRecord,
    dto: OfferDto,
    actorRecyclerId: string,
  ): boolean {
    return (
      existing.recyclerId.toLowerCase() === actorRecyclerId.toLowerCase() &&
      existing.category === dto.category &&
      sameNumber(existing.pricePerKg, dto.pricePerKg) &&
      existing.materialName === dto.materialName &&
      existing.status.toUpperCase() === (dto.status ?? 'ACTIVE').toUpperCase()
    );
  }

  async getOffers(
    category?: string,
    recyclerId?: string,
    activeOnly: boolean = false,
  ): Promise<SavedOfferRecord[]> {
    if (this.supabase) {
      try {
        let query = this.supabase.from('recycler_offered_rates').select('*');
        if (category) query = query.eq('category_name', category);
        if (recyclerId) query = query.eq('recycler_id', recyclerId);
        if (activeOnly) query = query.eq('status', 'ACTIVE');

        const { data, error } = await query;
        if (!error && Array.isArray(data)) {
          return data.map((row) => this.mapOfferRow(row));
        }
      } catch (err) {
        this.logger.warn(`Failed reading offers from Supabase: ${(err as Error).message}`);
      }
    }

    let results = Array.from(this.inMemoryOffers.values());
    if (category) results = results.filter((o) => o.category === category);
    if (recyclerId) results = results.filter((o) => o.recyclerId === recyclerId);
    if (activeOnly) results = results.filter((o) => o.status === 'ACTIVE');
    return results;
  }

  // =========================================================================
  // ADMIN REFERENCE PRICES PERSISTENCE
  // =========================================================================

  async saveReferencePrice(
    dto: ReferencePriceDto,
    actorUserId: string,
    actorLabel: string,
  ): Promise<SavedReferencePriceRecord> {
    const validation = this.policy.validateReferencePrice(dto);
    if (!validation.valid) {
      throw new ForbiddenException(`Validation failed: ${validation.errors.join(', ')}`);
    }

    const priceId = dto.priceId || `PR-${dto.category.slice(0, 3)}-01`;

    if (this.supabase) {
      try {
        const { data: existing, error: lookupErr } = await this.supabase
          .from('material_prices')
          .select('*')
          .eq('price_id', priceId)
          .maybeSingle();
        if (lookupErr) {
          this.logger.warn(`Reference-price lookup error: ${lookupErr.message}`);
        }

        const nowIso = new Date().toISOString();

        if (existing) {
          const requestedBase = Number(dto.baseVersion ?? 0);
          if (requestedBase > 0) {
            if (Number(existing.version) !== requestedBase) {
              throw new ConflictException(
                `Conflict: This reference price was updated on the server (server version: ${existing.version}, your base: ${requestedBase}). Please reload.`,
              );
            }
          }

          const updateRow = {
            category_name: dto.category,
            sub_category: dto.materialName,
            grade: dto.grade ?? '',
            prevailing_buy_rate: dto.pricePerKg,
            market_min: dto.marketMin,
            market_max: dto.marketMax,
            currency: dto.currency ?? 'INR',
            effective_date: dto.effectiveDate ?? nowIso.split('T')[0],
            is_active: dto.isActive !== false,
            updated_by: asUuidOrNull(actorUserId),
            updated_by_label: actorLabel,
            updated_at: nowIso,
            version: Number(existing.version) + 1,
          };

          const { data: updated, error: updateErr } = await this.supabase
            .from('material_prices')
            .update(updateRow)
            .eq('price_id', priceId)
            .eq('version', existing.version)
            .select();

          if (updateErr) {
            this.logger.error(`Supabase reference price update failed: ${updateErr.message}`);
            throw new ServiceUnavailableException(
              'Failed to persist reference price to cloud database.',
            );
          }
          if (!updated || updated.length === 0) {
            const { data: current } = await this.supabase
              .from('material_prices')
              .select('version')
              .eq('price_id', priceId)
              .maybeSingle();
            throw new ConflictException(
              `Conflict: This reference price was updated on the server (server version: ${current?.version ?? 'unknown'}). Please reload.`,
            );
          }

          const record = this.mapPriceRow(updated[0]);
          this.inMemoryPrices.set(priceId, record);
          return record;
        }

        const insertRow = {
          price_id: priceId,
          category_name: dto.category,
          sub_category: dto.materialName,
          location: 'National Reference (CPCB)',
          grade: dto.grade ?? '',
          prevailing_buy_rate: dto.pricePerKg,
          market_min: dto.marketMin,
          market_max: dto.marketMax,
          currency: dto.currency ?? 'INR',
          effective_date: dto.effectiveDate ?? nowIso.split('T')[0],
          is_active: dto.isActive !== false,
          updated_by: asUuidOrNull(actorUserId),
          updated_by_label: actorLabel,
          updated_at: nowIso,
          version: 1,
        };

        const { data: inserted, error: insertErr } = await this.supabase
          .from('material_prices')
          .insert(insertRow)
          .select()
          .single();

        if (insertErr || !inserted) {
          if (insertErr?.code === '23505') {
            throw new ConflictException(
              'A reference price with this id already exists. Reload and edit it.',
            );
          }
          this.logger.error(`Supabase reference price insert failed: ${insertErr?.message}`);
          throw new ServiceUnavailableException(
            'Failed to persist reference price to cloud database.',
          );
        }

        const record = this.mapPriceRow(inserted);
        this.inMemoryPrices.set(priceId, record);
        return record;
      } catch (err) {
        if (
          err instanceof ConflictException ||
          err instanceof ForbiddenException ||
          err instanceof ServiceUnavailableException
        ) {
          throw err;
        }
        this.logger.error(`Live Supabase error: ${(err as Error).message}`);
        throw new ServiceUnavailableException(`Cloud persistence error: ${(err as Error).message}`);
      }
    }

    // In-memory fallback
    const existing = this.inMemoryPrices.get(priceId);
    if (existing) {
      if (dto.baseVersion !== undefined && dto.baseVersion > 0) {
        if (existing.version !== dto.baseVersion) {
          throw new ConflictException(
            `Conflict: This reference price was updated on the server (server version: ${existing.version}, your base: ${dto.baseVersion}). Please reload.`,
          );
        }
      }
    }

    const version = (existing?.version || 0) + 1;
    const nowIso = new Date().toISOString();
    const record: SavedReferencePriceRecord = {
      priceId,
      category: dto.category,
      materialName: dto.materialName,
      grade: dto.grade || 'Standard Grade',
      pricePerKg: dto.pricePerKg,
      marketMin: dto.marketMin,
      marketMax: dto.marketMax,
      currency: dto.currency || 'INR',
      effectiveDate: dto.effectiveDate || nowIso.split('T')[0],
      isActive: dto.isActive !== false,
      updatedAt: nowIso,
      updatedBy: actorUserId,
      updatedByLabel: actorLabel,
      version,
    };

    this.inMemoryPrices.set(priceId, record);

    // Record in history
    this.inMemoryHistory.unshift({
      historyId: Date.now(),
      priceId,
      category: dto.category,
      materialName: dto.materialName,
      grade: dto.grade,
      pricePerKg: dto.pricePerKg,
      marketMin: dto.marketMin,
      marketMax: dto.marketMax,
      currency: dto.currency || 'INR',
      effectiveDate: record.effectiveDate,
      isActive: record.isActive,
      version,
      changeType: existing ? 'UPDATE' : 'INSERT',
      changedBy: actorUserId,
      changedByLabel: actorLabel,
      changedAt: nowIso,
    });

    return record;
  }

  private referenceMatches(row: any, dto: ReferencePriceDto): boolean {
    return (
      String(row.category_name) === dto.category &&
      String(row.sub_category ?? '') === dto.materialName &&
      sameNumber(row.prevailing_buy_rate, dto.pricePerKg) &&
      sameNumber(row.market_min, dto.marketMin) &&
      sameNumber(row.market_max, dto.marketMax) &&
      (row.is_active !== false) === (dto.isActive !== false)
    );
  }

  private inMemoryReferenceMatches(
    existing: SavedReferencePriceRecord,
    dto: ReferencePriceDto,
  ): boolean {
    return (
      existing.category === dto.category &&
      existing.materialName === dto.materialName &&
      sameNumber(existing.pricePerKg, dto.pricePerKg) &&
      sameNumber(existing.marketMin, dto.marketMin) &&
      sameNumber(existing.marketMax, dto.marketMax) &&
      existing.isActive === (dto.isActive !== false)
    );
  }

  async getReferencePrices(category?: string, activeOnly: boolean = true): Promise<SavedReferencePriceRecord[]> {
    if (this.supabase) {
      try {
        let query = this.supabase.from('material_prices').select('*');
        if (category) query = query.eq('category_name', category);
        if (activeOnly) query = query.eq('is_active', true);

        const { data, error } = await query;
        if (!error && Array.isArray(data)) {
          return data.map((row) => this.mapPriceRow(row));
        }
      } catch (err) {
        this.logger.warn(`Failed reading reference prices from Supabase: ${(err as Error).message}`);
      }
    }

    let results = Array.from(this.inMemoryPrices.values());
    if (category) results = results.filter((p) => p.category === category);
    if (activeOnly) results = results.filter((p) => p.isActive);
    return results;
  }

  async getPriceHistory(priceId?: string, limit: number = 50): Promise<PriceHistoryRecord[]> {
    if (this.supabase) {
      try {
        let query = this.supabase
          .from('material_price_history')
          .select('*')
          .order('changed_at', { ascending: false })
          .limit(limit);
        if (priceId) query = query.eq('price_id', priceId);

        const { data, error } = await query;
        if (!error && Array.isArray(data)) {
          return data.map((row) => ({
            historyId: row.history_id,
            priceId: row.price_id,
            category: row.category_name,
            materialName: row.sub_category,
            grade: row.grade,
            pricePerKg: Number(row.prevailing_buy_rate),
            marketMin: Number(row.market_min),
            marketMax: Number(row.market_max),
            currency: row.currency || 'INR',
            effectiveDate: row.effective_date,
            isActive: Boolean(row.is_active),
            version: Number(row.version),
            changeType: row.change_type,
            changedBy: row.changed_by,
            changedByLabel: row.changed_by_label,
            changedAt: row.changed_at,
          }));
        }
      } catch (err) {
        this.logger.warn(`Failed reading price history from Supabase: ${(err as Error).message}`);
      }
    }

    let results = [...this.inMemoryHistory];
    if (priceId) results = results.filter((h) => h.priceId === priceId);
    return results.slice(0, limit);
  }
}
