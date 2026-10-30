import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { PricesController } from './prices.controller';
import { PricePolicyService } from './price-policy.service';
import { PricesService } from './prices.service';

@Module({
  imports: [AuthModule],
  controllers: [PricesController],
  providers: [PricePolicyService, PricesService],
  exports: [PricePolicyService, PricesService],
})
export class PricesModule {}
