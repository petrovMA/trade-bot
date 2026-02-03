import React, { useState } from 'react';
import { botService } from '@services/bot.service';
import { BalanceRequirement } from '@/types/bot.types';
import { transformToBackendFormat } from '@/utils/botFormatters';

interface BotConfigInfo {
  name: string;
  settings: any;
}

interface BalanceCalculatorProps {
  config: BotConfigInfo;
}

/**
 * Balance calculation tool for Grid bots
 * Calculates required balance and displays planned orders if available
 */
const BalanceCalculator: React.FC<BalanceCalculatorProps> = ({ config }) => {
  const [price, setPrice] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<BalanceRequirement | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleCalculate = async () => {
    if (config.settings.type !== 'AlgorithmGrid') {
      setError('Balance calculation is only available for Grid bots');
      return;
    }

    const priceValue = parseFloat(price);
    if (!priceValue || priceValue <= 0) {
      setError('Please enter a valid price');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const transformedSettings = transformToBackendFormat(config.settings);
      const balanceResult = await botService.calculateBalance({
        price: priceValue,
        bot_params: transformedSettings as any
      });

      setResult(balanceResult);
    } catch (err: any) {
      const errorMessage = err.response?.data?.message || err.message || 'Failed to calculate balance';
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-5">
      <h3 className="text-md font-semibold text-gray-800 mb-3">Balance Calculator</h3>

      {/* Input Section */}
      <div className="flex gap-3 mb-4">
        <input
          type="number"
          placeholder="Enter current price"
          value={price}
          onChange={(e) => setPrice(e.target.value)}
          className="flex-1 px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500"
          step="any"
        />
        <button
          onClick={handleCalculate}
          disabled={loading || !price}
          className="btn btn-primary whitespace-nowrap"
        >
          {loading ? 'Calculating...' : 'Calculate Balance'}
        </button>
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
          {error}
        </div>
      )}

      {/* Results Display */}
      {result && (
        <div className="mt-4 space-y-4">
          {/* Summary Cards */}
          <div className="grid grid-cols-2 gap-3">
            <div className="bg-blue-50 p-3 rounded">
              <p className="text-xs text-gray-600 mb-1">Current Price</p>
              <p className="text-lg font-bold text-blue-600">{result.currentPrice.toFixed(8)}</p>
            </div>
            <div className="bg-purple-50 p-3 rounded">
              <p className="text-xs text-gray-600 mb-1">Total Orders</p>
              <p className="text-lg font-bold text-purple-600">{result.totalOrders}</p>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="bg-green-50 p-3 rounded">
              <p className="text-xs text-gray-600 mb-1">Buy Orders</p>
              <p className="text-xl font-bold text-green-600">{result.buyOrders}</p>
            </div>
            <div className="bg-red-50 p-3 rounded">
              <p className="text-xs text-gray-600 mb-1">Sell Orders</p>
              <p className="text-xl font-bold text-red-600">{result.sellOrders}</p>
            </div>
          </div>

          {/* Required Balance */}
          <div className="border-t pt-4">
            <h4 className="font-semibold mb-3">Required Balance</h4>
            <div className="space-y-2">
              <div className="flex justify-between items-center bg-gray-50 p-3 rounded">
                <span className="font-medium text-gray-700">{result.firstToken}:</span>
                <span className="text-lg font-bold text-gray-900">{result.requiredFirst.toFixed(8)}</span>
              </div>
              <div className="flex justify-between items-center bg-gray-50 p-3 rounded">
                <span className="font-medium text-gray-700">{result.secondToken}:</span>
                <span className="text-lg font-bold text-gray-900">{result.requiredSecond.toFixed(2)}</span>
              </div>
            </div>
          </div>

          {/* Planned Orders List (if backend supports it) */}
          {(result as any).orders && (result as any).orders.length > 0 && (
            <div className="border-t pt-4">
              <h4 className="font-semibold mb-2">
                Planned Orders ({(result as any).orders.length})
              </h4>
              <div className="max-h-64 overflow-y-auto border border-gray-200 rounded">
                <table className="min-w-full text-sm">
                  <thead className="bg-gray-50 sticky top-0">
                    <tr>
                      <th className="px-3 py-2 text-left font-semibold text-gray-700">Price</th>
                      <th className="px-3 py-2 text-left font-semibold text-gray-700">Amount</th>
                      <th className="px-3 py-2 text-left font-semibold text-gray-700">Side</th>
                      <th className="px-3 py-2 text-left font-semibold text-gray-700">Total</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {(result as any).orders.map((order: any, idx: number) => (
                      <tr
                        key={idx}
                        className={order.side === 'BUY' ? 'bg-green-50' : 'bg-red-50'}
                      >
                        <td className="px-3 py-2 font-medium">{order.price}</td>
                        <td className="px-3 py-2">{order.amount}</td>
                        <td className="px-3 py-2">
                          <span
                            className={`px-2 py-0.5 rounded text-xs font-medium ${
                              order.side === 'BUY'
                                ? 'bg-green-100 text-green-800'
                                : 'bg-red-100 text-red-800'
                            }`}
                          >
                            {order.side}
                          </span>
                        </td>
                        <td className="px-3 py-2">{(order.price * order.amount).toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="text-xs text-gray-500 mt-2">
                * These are theoretical orders that will be placed based on the grid parameters
              </p>
            </div>
          )}

          {/* Note if orders list is not available */}
          {!(result as any).orders && (
            <div className="border-t pt-4">
              <p className="text-sm text-gray-600 italic">
                Note: Planned orders list is not available from the backend.
                Contact the administrator to enable this feature.
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default BalanceCalculator;
