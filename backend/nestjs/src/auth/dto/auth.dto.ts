import { IsEmail, IsEnum, IsNotEmpty, IsOptional, IsString, Length, Matches } from 'class-validator';

export enum RoleType {
  INFORMAL_COLLECTOR = 'INFORMAL_COLLECTOR',
  FORMAL_RECYCLER = 'FORMAL_RECYCLER',
  GOVERNMENT_ADMIN = 'GOVERNMENT_ADMIN',
}

export enum AccountStatus {
  ACTIVE = 'ACTIVE',
  PENDING_VERIFICATION = 'PENDING_VERIFICATION',
  SUSPENDED = 'SUSPENDED',
}

export class RequestOtpDto {
  @IsNotEmpty()
  @IsString()
  @Matches(/^[0-9]{10}$/, { message: 'Phone number must be exactly 10 digits' })
  phoneNumber: string;

  @IsEnum(RoleType)
  role: RoleType;
}

export class VerifyOtpDto {
  @IsNotEmpty()
  @IsString()
  @Matches(/^[0-9]{10}$/, { message: 'Phone number must be exactly 10 digits' })
  phoneNumber: string;

  @IsNotEmpty()
  @IsString()
  @Length(6, 6, { message: 'OTP must be 6 digits' })
  otp: string;

  @IsEnum(RoleType)
  role: RoleType;
}

export class DemoLoginDto {
  @IsNotEmpty()
  @IsString()
  @Matches(/^[0-9]{10}$/, { message: 'Phone number must be exactly 10 digits' })
  phoneNumber: string;

  @IsNotEmpty()
  @IsString()
  @Length(6, 6, { message: 'OTP must be 6 digits' })
  otp: string;

  @IsOptional()
  @IsEnum(RoleType)
  role?: RoleType;
}

export class EmailLoginDto {
  @IsNotEmpty()
  @IsEmail({}, { message: 'Please provide a valid email address' })
  email: string;

  @IsNotEmpty()
  @IsString()
  @Length(6, 50, { message: 'Password must be between 6 and 50 characters' })
  password: string;

  @IsEnum(RoleType)
  role: RoleType;
}

export class GoogleSignInDto {
  @IsNotEmpty()
  @IsString()
  idToken: string;

  @IsNotEmpty()
  @IsEmail()
  email: string;

  @IsOptional()
  @IsString()
  displayName?: string;

  @IsEnum(RoleType)
  role: RoleType;
}

export interface AuthResponse {
  success: boolean;
  message: string;
  accessToken: string;
  /** Supabase refresh token (returned so clients can rotate sessions). */
  refreshToken?: string;
  /** Access-token lifetime in seconds. */
  expiresIn?: number;
  user: {
    id: string;
    email?: string;
    phoneNumber?: string;
    displayName: string;
    role: RoleType;
    status: AccountStatus;
    permissions: string[];
    statutoryIdentifier: string;
    entityName: string;
    /**
     * True only for backend-issued hackathon demo sessions. These sessions are
     * NOT Supabase sessions: they cannot write through PostgREST/RLS and every
     * privileged write is re-checked by this backend.
     */
    isDemoAccount: boolean;
    /**
     * Server-resolved recycler facility (registry `recycler_id`) for recycler
     * accounts. Clients must never derive this from a user-editable field.
     */
    facilityId?: string;
    dashboardRoute: string;
  };
}
