import React from 'react';
import ConfigCard from './ConfigCard';
import ConfigRow from './ConfigRow';
import { BotSettings } from '@/types/bot.types';
import { formatSymbol } from '@/utils/botFormatters';

interface BotConfigInfo {
  name: string;
  settings: BotSettings;
}

interface BotConfigDisplayProps {
  config: BotConfigInfo;
}

/**
 * Component that displays all bot configuration parameters
 * Handles different layouts for Grid vs Trader bots
 */
const BotConfigDisplay: React.FC<BotConfigDisplayProps> = ({ config }) => {
  const { settings } = config;

  if (settings.type === 'AlgorithmGrid') {
    return <GridBotConfig config={config} />;
  }

  if (settings.type === 'AlgorithmTrader') {
    return <TraderBotConfig config={config} />;
  }

  return null;
};

/**
 * Display configuration for Grid trading bots
 */
const GridBotConfig: React.FC<BotConfigDisplayProps> = ({ config }) => {
  const { settings } = config;

  // Type guard to ensure this is a Grid bot
  if (settings.type !== 'AlgorithmGrid') return null;

  const params = settings.parameters;

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      <ConfigCard title="Basic Information">
        <ConfigRow label="Bot Name" value={config.name} />
        <ConfigRow label="Trading Pair" value={formatSymbol(settings)} />
        <ConfigRow label="Exchange" value={settings.exchange} />
        <ConfigRow label="Order Type" value={settings.order_type} />
        <ConfigRow
          label="Direction"
          value={settings.direction}
          badge
          badgeColor={
            settings.direction === 'LONG' ? 'bg-green-100 text-green-800' :
            settings.direction === 'SHORT' ? 'bg-red-100 text-red-800' :
            'bg-purple-100 text-purple-800'
          }
        />
        {settings.orderBalanceType && (
          <ConfigRow label="Balance Type" value={settings.orderBalanceType} />
        )}
      </ConfigCard>

      <ConfigCard title="Grid Parameters">
        {params.trading_range && (
          <ConfigRow
            label="Trading Range"
            value={`${params.trading_range.lower_bound} - ${params.trading_range.upper_bound}`}
          />
        )}
        {params.order_quantity && (
          <>
            <ConfigRow label="Order Quantity" value={params.order_quantity.value} />
            <ConfigRow label="Counter Balance" value={params.order_quantity.is_counter_balance ? 'Yes' : 'No'} />
          </>
        )}
        {params.order_distance && (
          <ConfigRow
            label="Order Distance"
            value={`${params.order_distance.value}${params.order_distance.use_percent ? '%' : ''}`}
          />
        )}
        {params.profit_distance && (
          <ConfigRow
            label="Profit Distance"
            value={`${params.profit_distance.value}${params.profit_distance.use_percent ? '%' : ''}`}
          />
        )}
        {params.order_max_quantity !== undefined && (
          <ConfigRow label="Max Quantity" value={params.order_max_quantity} />
        )}
      </ConfigCard>

      <ConfigCard title="Precision Settings">
        <ConfigRow label="Amount Decimals" value={settings.countOfDigitsAfterDotForAmount} />
        <ConfigRow label="Price Decimals" value={settings.countOfDigitsAfterDotForPrice} />
      </ConfigCard>
    </div>
  );
};

/**
 * Display configuration for Trader bots
 */
const TraderBotConfig: React.FC<BotConfigDisplayProps> = ({ config }) => {
  const { settings } = config;

  // Type guard to ensure this is a Trader bot
  if (settings.type !== 'AlgorithmTrader') return null;

  const params = settings.parameters;

  return (
    <div className="grid grid-cols-1 gap-4">
      <ConfigCard title="Basic Information">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-4">
          <ConfigRow label="Bot Name" value={config.name} />
          <ConfigRow label="Trading Pair" value={formatSymbol(settings)} />
          <ConfigRow label="Exchange" value={settings.exchange} />
          <ConfigRow label="Order Type" value={settings.orderType} />
          <ConfigRow
            label="Strategy Type"
            value={settings.strategyType}
            badge
            badgeColor={
              settings.strategyType === 'LONG' ? 'bg-green-100 text-green-800' :
              settings.strategyType === 'SHORT' ? 'bg-red-100 text-red-800' :
              'bg-purple-100 text-purple-800'
            }
          />
          <ConfigRow label="Market Type" value={settings.marketType} />
          <ConfigRow label="Auto Balance" value={settings.autoBalance ? 'Enabled' : 'Disabled'} />
          {settings.orderBalanceType && (
            <ConfigRow label="Balance Type" value={settings.orderBalanceType} />
          )}
        </div>
      </ConfigCard>

      {params.longParameters && (
        <ConfigCard title="Long Parameters">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-x-4">
            {params.longParameters.trading_range && (
              <ConfigRow
                label="Trading Range"
                value={`${params.longParameters.trading_range.lower_bound} - ${params.longParameters.trading_range.upper_bound}`}
              />
            )}
            {params.longParameters.inOrderQuantity && (
              <ConfigRow
                label="In Order Quantity"
                value={`${params.longParameters.inOrderQuantity.value}${params.longParameters.inOrderQuantity.use_percent ? '%' : ''}`}
              />
            )}
            {params.longParameters.inOrderDistance && (
              <ConfigRow
                label="In Order Distance"
                value={`${params.longParameters.inOrderDistance.value}${params.longParameters.inOrderDistance.use_percent ? '%' : ''}`}
              />
            )}
            {params.longParameters.minTpDistance && (
              <ConfigRow
                label="Min TP Distance"
                value={`${params.longParameters.minTpDistance.value}${params.longParameters.minTpDistance.use_percent ? '%' : ''}`}
              />
            )}
            {params.longParameters.maxTpDistance && (
              <ConfigRow
                label="Max TP Distance"
                value={`${params.longParameters.maxTpDistance.value}${params.longParameters.maxTpDistance.use_percent ? '%' : ''}`}
              />
            )}
            {params.longParameters.triggerDistance && (
              <ConfigRow
                label="Trigger Distance"
                value={`${params.longParameters.triggerDistance.value}${params.longParameters.triggerDistance.use_percent ? '%' : ''}`}
              />
            )}
            <ConfigRow label="Max Trigger Count" value={params.longParameters.maxTriggerCount} />
            <ConfigRow label="Set Close Orders" value={params.longParameters.setCloseOrders ? 'Yes' : 'No'} />
            {params.longParameters.counterDistance !== undefined && (
              <ConfigRow label="Counter Distance" value={params.longParameters.counterDistance} />
            )}
          </div>
        </ConfigCard>
      )}

      {params.shortParameters && (
        <ConfigCard title="Short Parameters">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-x-4">
            {params.shortParameters.trading_range && (
              <ConfigRow
                label="Trading Range"
                value={`${params.shortParameters.trading_range.lower_bound} - ${params.shortParameters.trading_range.upper_bound}`}
              />
            )}
            {params.shortParameters.inOrderQuantity && (
              <ConfigRow
                label="In Order Quantity"
                value={`${params.shortParameters.inOrderQuantity.value}${params.shortParameters.inOrderQuantity.use_percent ? '%' : ''}`}
              />
            )}
            {params.shortParameters.inOrderDistance && (
              <ConfigRow
                label="In Order Distance"
                value={`${params.shortParameters.inOrderDistance.value}${params.shortParameters.inOrderDistance.use_percent ? '%' : ''}`}
              />
            )}
            {params.shortParameters.minTpDistance && (
              <ConfigRow
                label="Min TP Distance"
                value={`${params.shortParameters.minTpDistance.value}${params.shortParameters.minTpDistance.use_percent ? '%' : ''}`}
              />
            )}
            {params.shortParameters.maxTpDistance && (
              <ConfigRow
                label="Max TP Distance"
                value={`${params.shortParameters.maxTpDistance.value}${params.shortParameters.maxTpDistance.use_percent ? '%' : ''}`}
              />
            )}
            {params.shortParameters.triggerDistance && (
              <ConfigRow
                label="Trigger Distance"
                value={`${params.shortParameters.triggerDistance.value}${params.shortParameters.triggerDistance.use_percent ? '%' : ''}`}
              />
            )}
            <ConfigRow label="Max Trigger Count" value={params.shortParameters.maxTriggerCount} />
            <ConfigRow label="Set Close Orders" value={params.shortParameters.setCloseOrders ? 'Yes' : 'No'} />
            {params.shortParameters.counterDistance !== undefined && (
              <ConfigRow label="Counter Distance" value={params.shortParameters.counterDistance} />
            )}
          </div>
        </ConfigCard>
      )}

      {settings.trendDetector && (
        <ConfigCard title="Trend Detector">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-x-4">
            <ConfigRow label="Auto Calculate Trend" value={settings.trendDetector.notAutoCalcTrend ? 'No' : 'Yes'} />
            <ConfigRow label="RSI 1 Period" value={settings.trendDetector.rsi1.rsiPeriod} />
            <ConfigRow label="RSI 1 Timeframe" value={settings.trendDetector.rsi1.timeFrame} />
            <ConfigRow label="RSI 2 Period" value={settings.trendDetector.rsi2.rsiPeriod} />
            <ConfigRow label="RSI 2 Timeframe" value={settings.trendDetector.rsi2.timeFrame} />
            <ConfigRow label="HMA 1 Period" value={settings.trendDetector.hmaParameters.hma1Period} />
            <ConfigRow label="HMA 2 Period" value={settings.trendDetector.hmaParameters.hma2Period} />
            <ConfigRow label="HMA 3 Period" value={settings.trendDetector.hmaParameters.hma3Period} />
            <ConfigRow label="HMA Timeframe" value={settings.trendDetector.hmaParameters.timeFrame} />
          </div>
        </ConfigCard>
      )}

      <ConfigCard title="Precision Settings">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-4">
          <ConfigRow label="Amount Decimals" value={settings.countOfDigitsAfterDotForAmount} />
          <ConfigRow label="Price Decimals" value={settings.countOfDigitsAfterDotForPrice} />
          {settings.minOrderAmount && (
            <>
              <ConfigRow label="Min Order Amount" value={settings.minOrderAmount.amount} />
              <ConfigRow label="Min Amount Decimals" value={settings.minOrderAmount.countOfDigitsAfterDotForAmount} />
            </>
          )}
        </div>
      </ConfigCard>
    </div>
  );
};

export default BotConfigDisplay;
