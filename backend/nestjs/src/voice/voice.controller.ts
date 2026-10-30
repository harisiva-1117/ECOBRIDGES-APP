import { Body, Controller, HttpCode, HttpStatus, Post, Req, UseGuards } from '@nestjs/common';
import { IsOptional, IsString, Length } from 'class-validator';
import { SupabaseAuthGuard } from '../auth/guards/supabase-auth.guard';
import { RoleType } from '../auth/dto/auth.dto';
import { VoiceIntentRouterService, NavigationContext } from './voice-intent-router.service';

export class ProcessVoiceDto {
  @IsString()
  @Length(1, 1000)
  transcript: string;

  @IsOptional()
  context?: NavigationContext;

  /**
   * Deprecated: the server derives the role from the verified access token.
   * Accepted only for backward compatibility and never trusted.
   */
  @IsOptional()
  userRole?: RoleType;
}

@Controller('api/voice')
@UseGuards(SupabaseAuthGuard)
export class VoiceController {
  constructor(private readonly voiceIntentRouter: VoiceIntentRouterService) {}

  @Post('intent')
  @HttpCode(HttpStatus.OK)
  async processVoiceIntent(@Body() dto: ProcessVoiceDto, @Req() request: any) {
    // Authorization comes from the verified JWT + profiles row, never from
    // the request body or from a client-supplied context flag.
    const context: NavigationContext = {
      currentScreen: dto.context?.currentScreen ?? 'INTRO',
      currentRole: request.user?.role,
      isAuthenticated: true,
      activeLanguage: dto.context?.activeLanguage ?? 'en',
    };
    return this.voiceIntentRouter.routeVoiceIntent(dto.transcript, context, request.user?.role);
  }
}
