import React, { useState, useEffect } from 'react';
import { botService } from '@services/bot.service';
import { Position } from '@/types/bot.types';
import ConfigRow from './ConfigRow';

interface BotPositionProps {
  botName: string;
}

/**
 * Displays current bot position with auto-refresh
 * Polls position data every 5 seconds
 */
const BotPosition: React.FC<BotPositionProps> = ({ botName }) => {
  const [position, setPosition] = useState<Position | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);

  useEffect(() => {
    const fetchPosition = async () => {
      try {
        const positions = await botService.getPositions();
        const botInfo = positions.find(p => p.settings.name === botName);

        if (botInfo?.position) {
          setPosition(botInfo.position);
          setLastUpdate(new Date());
        } else {
          setPosition(null);
        }
        setError(null);
      } catch (err: any) {
        console.error('Error fetching position:', err);
        setError('Failed to load position data');
      } finally {
        setLoading(false);
      }
    };

    // Initial fetch
    fetchPosition();

    // Poll every 5 seconds
    const interval = setInterval(fetchPosition, 5000);

    return () => clearInterval(interval);
  }, [botName]);

  if (loading) {
    return (
      <div className="bg-white border border-gray-200 rounded-lg p-5">
        <h3 className="text-md font-semibold text-gray-800 mb-3">Current Position</h3>
        <div className="text-center py-4">
          <div className="inline-block animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600"></div>
          <p className="mt-2 text-sm text-gray-500">Loading position...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white border border-gray-200 rounded-lg p-5">
        <h3 className="text-md font-semibold text-gray-800 mb-3">Current Position</h3>
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded">
          {error}
        </div>
      </div>
    );
  }

  if (!position) {
    return (
      <div className="bg-white border border-gray-200 rounded-lg p-5">
        <h3 className="text-md font-semibold text-gray-800 mb-3">Current Position</h3>
        <p className="text-sm text-gray-500">No active position for this bot</p>
        {lastUpdate && (
          <p className="text-xs text-gray-400 mt-2">
            Last checked: {lastUpdate.toLocaleTimeString()}
          </p>
        )}
      </div>
    );
  }

  const isProfitable = position.unrealisedPnl >= 0;

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-5">
      <div className="flex justify-between items-center mb-3">
        <h3 className="text-md font-semibold text-gray-800">Current Position</h3>
        {lastUpdate && (
          <span className="text-xs text-gray-400">
            Updated: {lastUpdate.toLocaleTimeString()}
          </span>
        )}
      </div>

      {/* Main Metrics Grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
        <div className="bg-gray-50 p-3 rounded">
          <p className="text-xs text-gray-600 mb-1">Size</p>
          <p className="text-lg font-bold text-gray-900">{position.size}</p>
        </div>
        <div className="bg-gray-50 p-3 rounded">
          <p className="text-xs text-gray-600 mb-1">Entry Price</p>
          <p className="text-lg font-bold text-gray-900">{position.entryPrice}</p>
        </div>
        <div className="bg-gray-50 p-3 rounded">
          <p className="text-xs text-gray-600 mb-1">Market Price</p>
          <p className="text-lg font-bold text-gray-900">{position.marketPrice}</p>
        </div>
        <div className={`p-3 rounded ${isProfitable ? 'bg-green-50' : 'bg-red-50'}`}>
          <p className="text-xs text-gray-600 mb-1">Unrealised PnL</p>
          <p className={`text-lg font-bold ${isProfitable ? 'text-green-600' : 'text-red-600'}`}>
            {position.unrealisedPnl >= 0 ? '+' : ''}{position.unrealisedPnl.toFixed(2)}
          </p>
        </div>
      </div>

      {/* Additional Details */}
      <div className="grid grid-cols-2 md:grid-cols-3 gap-x-4 gap-y-2 pt-3 border-t">
        <ConfigRow label="Realised PnL" value={position.realisedPnl.toFixed(2)} />
        <ConfigRow label="Break Even" value={position.breakEvenPrice} />
        <ConfigRow label="Leverage" value={`${position.leverage}x`} />
        <ConfigRow label="Liquidation" value={position.liqPrice} />
        <ConfigRow label="Pair" value={position.pair} />
        <ConfigRow
          label="Side"
          value={position.side}
          badge
          badgeColor={
            position.side === 'LONG' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
          }
        />
      </div>
    </div>
  );
};

export default BotPosition;
