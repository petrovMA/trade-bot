export const COLORS = {
  BUY_BG: 'bg-buy-light',
  SELL_BG: 'bg-sell-light',
  BUY_TEXT: 'text-buy-dark',
  SELL_TEXT: 'text-sell-dark',
  BUY_BORDER: 'border-buy',
  SELL_BORDER: 'border-sell',
} as const;

export const EXCHANGES = [
  'Bybit',
  'Binance',
  'Huobi',
  'Gate',
  'BitMAX',
] as const;

export const BOT_TYPES = [
  'AlgorithmGrid',
  'AlgorithmTrader',
  'AlgorithmBobblesIndicator',
] as const;

export const POLLING_INTERVALS = {
  BOT_STATUS: parseInt(import.meta.env.VITE_POLLING_INTERVAL || '5000'),
  ORDERS: 10000,
  POSITIONS: 5000,
} as const;

export const ORDER_TYPES = ['LIMIT', 'MARKET'] as const;
export const DIRECTIONS = ['LONG', 'SHORT'] as const;
export const STRATEGY_TYPES = ['LONG', 'SHORT', 'BOTH'] as const;
export const MARKET_TYPES = ['SPOT', 'FUTURES'] as const;
