import { BotSettings } from './bot.types';

export interface ApiResponse<T = any> {
  status: string;
  data: T;
}

export interface EmulateParams {
  from?: string;
  to?: string;
  fee?: number;
  fail_if_kline_gaps?: boolean;
  botParams: BotSettings;
  is_write_orders_to_log?: boolean;
}

export interface TestBalance {
  balance: number;
  profit: number;
  totalTrades: number;
  profitableTrades: number;
  losingTrades: number;
  maxDrawdown: number;
}

export enum ExchangeType {
  BYBIT = 'Bybit',
  BINANCE = 'Binance',
  HUOBI = 'Huobi',
  GATE = 'Gate',
  BITMAX = 'BitMAX'
}

export interface Notification {
  botName: string;
  message: string;
  price?: number;
  amount?: number;
}
