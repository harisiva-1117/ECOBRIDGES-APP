import {
  Body,
  Controller,
  ForbiddenException,
  Get,
  HttpCode,
  HttpStatus,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { RoleType } from '../auth/dto/auth.dto';
import { Roles, RolesGuard } from '../auth/guards/roles.guard';
import { SupabaseAuthGuard } from '../auth/guards/supabase-auth.guard';
import { OfferDto, ReferencePriceDto } from './dto/price.dto';
import { PricePolicyService } from './price-policy.service';
import { PricesService } from './prices.service';

interface AuthenticatedRequest {
  user?: {
    id?: string;
    email?: string;
    displayName?: string;
    role?: RoleType;
    statutoryIdentifier?: string;
    facilityId?: string;
    isDemo?: boolean;
  };
}

/**
 * Server-side price management and validation controller.
 * Enforces role access, facility ownership, and persistence to Supabase.
 */
@Controller('api/prices')
export class PricesController {
  constructor(
    private readonly policy: PricePolicyService,
    private readonly pricesService?: PricesService,
  ) {}

  @Get('health')
  health() {
    return { status: 'ok', policy: 'reference-and-offer-v2' };
  }

  // --- VALIDATION ENDPOINTS ---

  @Post('reference/validate')
  @HttpCode(HttpStatus.OK)
  @UseGuards(SupabaseAuthGuard, RolesGuard)
  @Roles(RoleType.GOVERNMENT_ADMIN)
  validateReference(@Body() dto: ReferencePriceDto) {
    return {
      ...this.policy.validateReferencePrice(dto),
      significantChange: this.policy.isSignificantChange(
        dto.previousPricePerKg ?? null,
        dto.pricePerKg,
      ),
    };
  }

  @Post('offers/validate')
  @HttpCode(HttpStatus.OK)
  @UseGuards(SupabaseAuthGuard, RolesGuard)
  @Roles(RoleType.FORMAL_RECYCLER)
  validateOffer(@Body() dto: OfferDto, @Req() request: AuthenticatedRequest) {
    const actorRecyclerId = request.user?.facilityId || request.user?.statutoryIdentifier;
    if (!actorRecyclerId) {
      throw new ForbiddenException('Your recycler identity could not be resolved.');
    }
    if (!this.policy.ownsOffer(dto.recyclerId, actorRecyclerId)) {
      throw new ForbiddenException('You can only publish offers for your own facility.');
    }
    return this.policy.validateOffer(dto);
  }

  // --- RECYCLER OFFERS PERSISTENCE ---

  @Post('offers')
  @HttpCode(HttpStatus.OK)
  @UseGuards(SupabaseAuthGuard, RolesGuard)
  @Roles(RoleType.FORMAL_RECYCLER)
  async saveOffer(@Body() dto: OfferDto, @Req() request: AuthenticatedRequest) {
    const actorRecyclerId = await this.resolveFacility(request);
    const actorLabel = request.user?.displayName || 'Recycler Manager';
    if (!this.pricesService) {
      throw new ForbiddenException('Prices service is unavailable.');
    }
    return this.pricesService.saveOffer(dto, actorRecyclerId, actorLabel, request.user?.id);
  }

  /**
   * Resolve the caller's registry facility server-side. The client-supplied
   * `recyclerId` is only ever compared against this value, never trusted.
   */
  private async resolveFacility(request: AuthenticatedRequest): Promise<string> {
    if (!this.pricesService) {
      const fallback = request.user?.facilityId || request.user?.statutoryIdentifier;
      if (fallback) return fallback;
      throw new ForbiddenException('Your recycler identity could not be resolved.');
    }
    const actorRecyclerId = await this.pricesService.resolveRecyclerFacilityId({
      userId: request.user?.id,
      email: request.user?.email,
      statutoryIdentifier: request.user?.statutoryIdentifier,
      facilityId: request.user?.facilityId,
      isDemo: request.user?.isDemo,
    });
    if (!actorRecyclerId) {
      throw new ForbiddenException('Your recycler identity could not be resolved.');
    }
    return actorRecyclerId;
  }

  @Get('offers')
  @HttpCode(HttpStatus.OK)
  @UseGuards(SupabaseAuthGuard)
  async getOffers(
    @Query('category') category?: string,
    @Query('recyclerId') recyclerId?: string,
    @Query('activeOnly') activeOnly?: string,
    @Req() request?: AuthenticatedRequest,
  ) {
    const userRole = request?.user?.role;
    const filterActive = userRole === RoleType.INFORMAL_COLLECTOR || activeOnly === 'true';
    return this.pricesService.getOffers(category, recyclerId, filterActive);
  }

  // --- ADMIN REFERENCE PRICES PERSISTENCE ---

  @Post('reference')
  @HttpCode(HttpStatus.OK)
  @UseGuards(SupabaseAuthGuard, RolesGuard)
  @Roles(RoleType.GOVERNMENT_ADMIN)
  async saveReference(@Body() dto: ReferencePriceDto, @Req() request: AuthenticatedRequest) {
    const actorUserId = request.user?.id || 'admin-system';
    const actorLabel = request.user?.displayName || 'Government Admin';
    return this.pricesService.saveReferencePrice(dto, actorUserId, actorLabel);
  }

  @Get('reference')
  @HttpCode(HttpStatus.OK)
  @UseGuards(SupabaseAuthGuard)
  async getReferencePrices(
    @Query('category') category?: string,
    @Query('activeOnly') activeOnly?: string,
  ) {
    const filterActive = activeOnly !== 'false';
    return this.pricesService.getReferencePrices(category, filterActive);
  }

  @Get('history')
  @HttpCode(HttpStatus.OK)
  @UseGuards(SupabaseAuthGuard, RolesGuard)
  @Roles(RoleType.GOVERNMENT_ADMIN)
  async getPriceHistory(
    @Query('priceId') priceId?: string,
    @Query('limit') limit?: string,
  ) {
    const maxLimit = limit ? Math.min(100, Math.max(1, parseInt(limit, 10))) : 50;
    return this.pricesService.getPriceHistory(priceId, maxLimit);
  }
}
