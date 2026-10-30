import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { VoiceController } from './voice.controller';
import { VoiceIntentRouterService } from './voice-intent-router.service';

@Module({
  imports: [AuthModule],
  controllers: [VoiceController],
  providers: [VoiceIntentRouterService],
  exports: [VoiceIntentRouterService],
})
export class VoiceModule {}
