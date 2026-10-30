import { Type } from 'class-transformer';
import {
  IsBoolean,
  IsIn,
  IsNotEmpty,
  IsNumber,
  IsOptional,
  IsString,
  Length,
  Min,
} from 'class-validator';

const CATEGORIES = [
  'PCB_BOARDS',
  'CABLES_WIRES',
  'BATTERIES',
  'CRTS_MONITORS',
  'LCD_PANELS',
  'MOTORS_MAGNETS',
  'MIXED_PLASTICS',
] as const;

export class ReferencePriceDto {
  @IsOptional()
  @IsString()
  priceId?: string;

  @IsIn(CATEGORIES)
  category!: (typeof CATEGORIES)[number];

  @IsString()
  @IsNotEmpty()
  @Length(1, 120)
  materialName!: string;

  @IsOptional()
  @IsString()
  @Length(0, 60)
  grade?: string;

  @IsNumber()
  @Min(0)
  @Type(() => Number)
  pricePerKg!: number;

  @IsNumber()
  @Min(0)
  @Type(() => Number)
  marketMin!: number;

  @IsNumber()
  @Min(0)
  @Type(() => Number)
  marketMax!: number;

  @IsOptional()
  @IsIn(['INR'])
  currency?: string;

  @IsOptional()
  @IsString()
  effectiveDate?: string | null;

  @IsOptional()
  @IsBoolean()
  isActive?: boolean;

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Type(() => Number)
  baseVersion?: number;

  /** Previous stored rate, used only to report whether a large move needs confirmation. */
  @IsOptional()
  @IsNumber()
  @Min(0)
  @Type(() => Number)
  previousPricePerKg?: number;
}

export class OfferDto {
  @IsOptional()
  @IsString()
  offerId?: string;

  /** Must match the caller's registered CPCB identity. */
  @IsString()
  @IsNotEmpty()
  recyclerId!: string;

  @IsOptional()
  @IsString()
  recyclerName?: string;

  @IsIn(CATEGORIES)
  category!: (typeof CATEGORIES)[number];

  @IsString()
  @IsNotEmpty()
  @Length(1, 120)
  materialName!: string;

  @IsOptional()
  @IsString()
  @Length(0, 60)
  grade?: string;

  @IsNumber()
  @Min(0.01)
  @Type(() => Number)
  pricePerKg!: number;

  @IsOptional()
  @IsIn(['INR'])
  currency?: string;

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Type(() => Number)
  minQuantityKg?: number | null;

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Type(() => Number)
  maxQuantityKg?: number | null;

  @IsOptional()
  @IsString()
  @Length(0, 500)
  condition?: string;

  @IsOptional()
  @IsIn(['DRAFT', 'ACTIVE', 'PAUSED', 'EXPIRED'])
  status?: string;

  @IsOptional()
  @IsString()
  @Length(0, 1000)
  notes?: string;

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Type(() => Number)
  baseVersion?: number;
}
