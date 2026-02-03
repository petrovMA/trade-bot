import React from 'react';

/**
 * Get strategy badge component for a bot
 */
export const getStrategyBadge = (settings: any): React.ReactElement | null => {
  if (settings.type === 'AlgorithmGrid') {
    return (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
        Grid
      </span>
    );
  }
  if (settings.strategy) {
    return (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
        {settings.strategy}
      </span>
    );
  }
  return null;
};

/**
 * Get direction badge component for a bot
 */
export const getDirectionBadge = (settings: any): React.ReactElement | null => {
  const direction = settings.direction || settings.strategy;
  if (!direction) return null;

  const colors = {
    LONG: 'bg-green-100 text-green-800',
    SHORT: 'bg-red-100 text-red-800',
    BOTH: 'bg-purple-100 text-purple-800',
  };

  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${colors[direction as keyof typeof colors] || 'bg-gray-100 text-gray-800'}`}>
      {direction}
    </span>
  );
};

/**
 * Format trading pair symbol
 */
export const formatSymbol = (settings: any): string => {
  const pair = settings.pair || settings.symbol;
  if (!pair) return 'N/A';
  return `${pair.first}/${pair.second}`;
};

/**
 * Transform frontend bot settings to backend format for balance calculation
 */
export const transformToBackendFormat = (settings: any) => {
  return {
    flow_name: settings.name,
    symbol: settings.pair,
    exchange_type: settings.exchange,
    order_type: settings.ordersType,
    direction: settings.direction,
    parameters: {
      trading_range: {
        lower_bound: settings.parameters.tradingRange?.lowerBound || settings.parameters.trading_range?.lower_bound,
        upper_bound: settings.parameters.tradingRange?.upperBound || settings.parameters.trading_range?.upper_bound
      },
      order_quantity: {
        value: settings.parameters.orderQuantity?.value || settings.parameters.order_quantity?.value,
        is_counter_balance: settings.parameters.orderQuantity?.counterBalance || settings.parameters.order_quantity?.is_counter_balance || false
      },
      order_distance: {
        value: settings.parameters.orderDistance?.value || settings.parameters.order_distance?.value,
        use_percent: settings.parameters.orderDistance?.usePercent || settings.parameters.order_distance?.use_percent
      },
      profit_distance: {
        value: settings.parameters.profitDistance?.value || settings.parameters.profit_distance?.value,
        use_percent: settings.parameters.profitDistance?.usePercent || settings.parameters.profit_distance?.use_percent
      },
      order_max_quantity: settings.parameters.orderMaxQuantity || settings.parameters.order_max_quantity
    },
    countOfDigitsAfterDotForAmount: settings.countOfDigitsAfterDotForAmount,
    countOfDigitsAfterDotForPrice: settings.countOfDigitsAfterDotForPrice
  };
};
