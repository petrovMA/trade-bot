export interface TradePair {
  first: string;
  second: string;
}

export interface Param {
  value: number;
  use_percent: boolean;
}

export interface TradingRange {
  lower_bound: number;
  upper_bound: number;
}

export interface OrderQuantity {
  value: number;
  is_counter_balance: boolean;
}

export enum OrderType {
  LIMIT = 'LIMIT',
  MARKET = 'MARKET'
}

export enum Direction {
  LONG = 'LONG',
  SHORT = 'SHORT'
}

export enum StrategyType {
  LONG = 'LONG',
  SHORT = 'SHORT',
  BOTH = 'BOTH'
}

// Grid Bot Settings
export interface GridParameters {
  trading_range: TradingRange;
  order_quantity: OrderQuantity;
  order_distance: Param;
  profit_distance: Param;
  order_max_quantity: number;
}

export interface BotSettingsGrid {
  type: 'AlgorithmGrid';
  name: string;
  pair: TradePair;
  exchange: string;
  order_type: OrderType;
  direction: Direction;
  parameters: GridParameters;
  orderBalanceType?: string;
  countOfDigitsAfterDotForAmount: number;
  countOfDigitsAfterDotForPrice: number;
}

// Trader Bot Settings
export interface RsiConfig {
  rsiPeriod: number;
  timeFrame: string;
}

export interface HmaParameters {
  hma1Period: number;
  hma2Period: number;
  hma3Period: number;
  timeFrame: string;
}

export interface TrendDetector {
  notAutoCalcTrend: boolean;
  rsi1: RsiConfig;
  rsi2: RsiConfig;
  hmaParameters: HmaParameters;
  inputKlineInterval?: string;
}

export interface EntireTp {
  maxTriggerAmount: number;
  maxProfitPercent: number;
  maxLossPercent: number;
  enabledInHedge: boolean;
  enabled: boolean;
  tpDistance: Param;
}

export interface TradeParametersConfig {
  trading_range: TradingRange;
  inOrderQuantity: Param;
  inOrderDistance: Param;
  trailingInOrderDistance?: Param;
  triggerInOrderDistance?: Param;
  triggerDistance: Param;
  minTpDistance: Param;
  maxTpDistance: Param;
  maxTriggerCount: number;
  setCloseOrders: boolean;
  counterDistance?: number;
  useRealizedPnlInCalcProfit?: boolean;
  entireTp?: EntireTp;
}

export interface TradeParameters {
  longParameters?: TradeParametersConfig;
  shortParameters?: TradeParametersConfig;
}

export interface MinOrderAmount {
  amount: number;
  countOfDigitsAfterDotForAmount: number;
}

export interface BotSettingsTrader {
  type: 'AlgorithmTrader';
  name: string;
  pair: TradePair;
  exchange: string;
  strategyType: StrategyType;
  orderType: OrderType;
  parameters: TradeParameters;
  trendDetector?: TrendDetector;
  minOrderAmount?: MinOrderAmount;
  marketType: string;
  marketTypeComment: string;
  strategyTypeComment: string;
  autoBalance: boolean;
  orderBalanceType?: string;
  countOfDigitsAfterDotForAmount: number;
  countOfDigitsAfterDotForPrice: number;
}

export type BotSettings = BotSettingsGrid | BotSettingsTrader;

// Bot Status
export interface Position {
  pair: string;
  marketPrice: number;
  unrealisedPnl: number;
  realisedPnl: number;
  entryPrice: number;
  breakEvenPrice: number;
  leverage: number;
  liqPrice: number;
  size: number;
  side: string;
}

export interface BotInfo {
  settings: BotSettings;
  position: Position | null;
}

export enum BotState {
  NEW = 'NEW',
  RUNNABLE = 'RUNNABLE',
  BLOCKED = 'BLOCKED',
  WAITING = 'WAITING',
  TIMED_WAITING = 'TIMED_WAITING',
  TERMINATED = 'TERMINATED'
}

// Balance Requirement
export interface BalanceRequirement {
  firstToken: string;
  secondToken: string;
  requiredFirst: number;
  requiredSecond: number;
  totalOrders: number;
  buyOrders: number;
  sellOrders: number;
  currentPrice: number;
}

export interface BalanceRequest {
  price: number;
  bot_params: BotSettingsGrid;
}

// Bot Configuration Info (used in API responses)
export interface BotConfigInfo {
  name: string;
  settings: BotSettings;
}

// Grid Order (for balance calculator planned orders)
export interface GridOrder {
  price: number;
  amount: number;
  side: 'BUY' | 'SELL';
}
