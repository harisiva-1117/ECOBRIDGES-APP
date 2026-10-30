import { Body, Controller, Get, HttpCode, HttpStatus, Post } from '@nestjs/common';
import { AuthService } from './auth.service';
import {
  AuthResponse,
  DemoLoginDto,
  EmailLoginDto,
  GoogleSignInDto,
  RequestOtpDto,
  VerifyOtpDto,
} from './dto/auth.dto';

@Controller('api/auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  /** Health probe for the auth module (no secrets, no user data). */
  @Get('health')
  health() {
    return { status: 'ok', supabaseConfigured: Boolean(process.env.SUPABASE_URL) };
  }

  /**
   * Real SMS OTP is not implemented (returns 501). The Android client labels
   * its on-device code as Demo OTP and never claims this endpoint sent an SMS.
   */
  @Post('otp/send')
  @HttpCode(HttpStatus.OK)
  async sendOtp(@Body() dto: RequestOtpDto) {
    return this.authService.requestMobileOtp(dto);
  }

  @Post('otp/verify')
  @HttpCode(HttpStatus.OK)
  async verifyOtp(@Body() dto: VerifyOtpDto): Promise<AuthResponse> {
    return this.authService.verifyMobileOtp(dto);
  }

  /**
   * Backend-mediated demo authentication for hackathon evaluation.
   * Gated by server configuration (DEMO_LOGIN_ENABLED).
   * Validates code 123456 and issues a signed, expiring JWT token.
   */
  @Post('demo-login')
  @HttpCode(HttpStatus.OK)
  async demoLogin(@Body() dto: DemoLoginDto): Promise<AuthResponse> {
    return this.authService.demoLogin(dto);
  }

  @Post('email/login')
  @HttpCode(HttpStatus.OK)
  async loginWithEmail(@Body() dto: EmailLoginDto): Promise<AuthResponse> {
    return this.authService.loginWithEmail(dto);
  }

  @Post('google')
  @HttpCode(HttpStatus.OK)
  async loginWithGoogle(@Body() dto: GoogleSignInDto): Promise<AuthResponse> {
    return this.authService.loginWithGoogle(dto);
  }
}
