import { Module } from '@nestjs/common';
import { AuthModule } from './auth/auth.module';
import { PricesModule } from './prices/prices.module';
import { VoiceModule } from './voice/voice.module';

@Module({
  imports: [AuthModule, PricesModule, VoiceModule],
})
export class AppModule {}
