import { useState } from 'react';
import { BotSettingsGrid, OrderType, Direction } from '@/types/bot.types';
import TradePairInput from './TradePairInput';
import ParamInput from './ParamInput';
import { EXCHANGES, ORDER_TYPES, DIRECTIONS } from '@utils/constants';

interface GridBotFormProps {
  onSubmit: (settings: BotSettingsGrid) => void;
  loading: boolean;
}

const GridBotForm = ({ onSubmit, loading }: GridBotFormProps) => {
  const [formData, setFormData] = useState<BotSettingsGrid>({
    type: 'AlgorithmGrid',
    name: '',
    pair: { first: '', second: '' },
    exchange: 'Bybit',
    order_type: 'LIMIT' as OrderType,
    direction: 'LONG' as Direction,
    parameters: {
      trading_range: { lower_bound: 0, upper_bound: 0 },
      order_quantity: { value: 0, is_counter_balance: false },
      order_distance: { value: 0, use_percent: true },
      profit_distance: { value: 0, use_percent: true },
      order_max_quantity: 10,
    },
    countOfDigitsAfterDotForAmount: 8,
    countOfDigitsAfterDotForPrice: 2,
  });

  const [errors, setErrors] = useState<Record<string, string>>({});

  const validate = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!formData.name.trim()) newErrors.name = 'Bot name is required';
    if (!formData.pair.first) newErrors.pairFirst = 'Base currency is required';
    if (!formData.pair.second) newErrors.pairSecond = 'Quote currency is required';
    if (formData.parameters.trading_range.lower_bound <= 0) {
      newErrors.lowerBound = 'Lower bound must be positive';
    }
    if (formData.parameters.trading_range.upper_bound <= 0) {
      newErrors.upperBound = 'Upper bound must be positive';
    }
    if (formData.parameters.trading_range.upper_bound <= formData.parameters.trading_range.lower_bound) {
      newErrors.upperBound = 'Upper bound must be greater than lower bound';
    }
    if (formData.parameters.order_quantity.value <= 0) {
      newErrors.orderQuantity = 'Order quantity must be positive';
    }
    if (formData.parameters.order_distance.value <= 0) {
      newErrors.orderDistance = 'Order distance must be positive';
    }
    if (formData.parameters.profit_distance.value <= 0) {
      newErrors.profitDistance = 'Profit distance must be positive';
    }
    if (formData.parameters.order_max_quantity <= 0) {
      newErrors.orderMaxQuantity = 'Max quantity must be positive';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (validate()) {
      onSubmit(formData);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {/* Basic Settings */}
      <div className="card">
        <h3 className="text-lg font-semibold mb-4">Basic Settings</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Bot Name *
            </label>
            <input
              type="text"
              className="input"
              placeholder="my-grid-bot"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              disabled={loading}
            />
            {errors.name && <p className="text-red-600 text-sm mt-1">{errors.name}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Exchange *
            </label>
            <select
              className="input"
              value={formData.exchange}
              onChange={(e) => setFormData({ ...formData, exchange: e.target.value })}
              disabled={loading}
            >
              {EXCHANGES.map((ex) => (
                <option key={ex} value={ex}>
                  {ex}
                </option>
              ))}
            </select>
          </div>

          <div className="md:col-span-2">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Trading Pair *
            </label>
            <TradePairInput
              value={formData.pair}
              onChange={(pair) => setFormData({ ...formData, pair })}
              disabled={loading}
            />
            {(errors.pairFirst || errors.pairSecond) && (
              <p className="text-red-600 text-sm mt-1">
                {errors.pairFirst || errors.pairSecond}
              </p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Direction *
            </label>
            <select
              className="input"
              value={formData.direction}
              onChange={(e) => setFormData({ ...formData, direction: e.target.value as Direction })}
              disabled={loading}
            >
              {DIRECTIONS.map((dir) => (
                <option key={dir} value={dir}>
                  {dir}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Order Type *
            </label>
            <select
              className="input"
              value={formData.order_type}
              onChange={(e) => setFormData({ ...formData, order_type: e.target.value as OrderType })}
              disabled={loading}
            >
              {ORDER_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Trading Range */}
      <div className="card">
        <h3 className="text-lg font-semibold mb-4">Trading Range</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Lower Bound *
            </label>
            <input
              type="number"
              className="input"
              placeholder="0.0"
              value={formData.parameters.trading_range.lower_bound || ''}
              onChange={(e) =>
                setFormData({
                  ...formData,
                  parameters: {
                    ...formData.parameters,
                    trading_range: {
                      ...formData.parameters.trading_range,
                      lower_bound: parseFloat(e.target.value) || 0,
                    },
                  },
                })
              }
              disabled={loading}
              min="0"
              step="any"
            />
            {errors.lowerBound && <p className="text-red-600 text-sm mt-1">{errors.lowerBound}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Upper Bound *
            </label>
            <input
              type="number"
              className="input"
              placeholder="0.0"
              value={formData.parameters.trading_range.upper_bound || ''}
              onChange={(e) =>
                setFormData({
                  ...formData,
                  parameters: {
                    ...formData.parameters,
                    trading_range: {
                      ...formData.parameters.trading_range,
                      upper_bound: parseFloat(e.target.value) || 0,
                    },
                  },
                })
              }
              disabled={loading}
              min="0"
              step="any"
            />
            {errors.upperBound && <p className="text-red-600 text-sm mt-1">{errors.upperBound}</p>}
          </div>
        </div>
      </div>

      {/* Grid Parameters */}
      <div className="card">
        <h3 className="text-lg font-semibold mb-4">Grid Parameters</h3>
        <div className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Order Quantity *
              </label>
              <div className="flex gap-2 items-center">
                <input
                  type="number"
                  className="input flex-1"
                  placeholder="0.0"
                  value={formData.parameters.order_quantity.value || ''}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      parameters: {
                        ...formData.parameters,
                        order_quantity: {
                          ...formData.parameters.order_quantity,
                          value: parseFloat(e.target.value) || 0,
                        },
                      },
                    })
                  }
                  disabled={loading}
                  min="0"
                  step="any"
                />
                <label className="flex items-center gap-2 text-sm whitespace-nowrap">
                  <input
                    type="checkbox"
                    checked={formData.parameters.order_quantity.is_counter_balance}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        parameters: {
                          ...formData.parameters,
                          order_quantity: {
                            ...formData.parameters.order_quantity,
                            is_counter_balance: e.target.checked,
                          },
                        },
                      })
                    }
                    disabled={loading}
                    className="rounded border-gray-300"
                  />
                  Counter Balance
                </label>
              </div>
              {errors.orderQuantity && <p className="text-red-600 text-sm mt-1">{errors.orderQuantity}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Max Order Quantity *
              </label>
              <input
                type="number"
                className="input"
                placeholder="10"
                value={formData.parameters.order_max_quantity || ''}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    parameters: {
                      ...formData.parameters,
                      order_max_quantity: parseInt(e.target.value) || 0,
                    },
                  })
                }
                disabled={loading}
                min="1"
              />
              {errors.orderMaxQuantity && <p className="text-red-600 text-sm mt-1">{errors.orderMaxQuantity}</p>}
            </div>
          </div>

          <ParamInput
            label="Order Distance *"
            value={formData.parameters.order_distance}
            onChange={(order_distance) =>
              setFormData({
                ...formData,
                parameters: { ...formData.parameters, order_distance },
              })
            }
            disabled={loading}
          />
          {errors.orderDistance && <p className="text-red-600 text-sm mt-1">{errors.orderDistance}</p>}

          <ParamInput
            label="Profit Distance *"
            value={formData.parameters.profit_distance}
            onChange={(profit_distance) =>
              setFormData({
                ...formData,
                parameters: { ...formData.parameters, profit_distance },
              })
            }
            disabled={loading}
          />
          {errors.profitDistance && <p className="text-red-600 text-sm mt-1">{errors.profitDistance}</p>}
        </div>
      </div>

      {/* Precision Settings */}
      <div className="card">
        <h3 className="text-lg font-semibold mb-4">Precision Settings</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Digits After Dot (Amount) *
            </label>
            <input
              type="number"
              className="input"
              placeholder="8"
              value={formData.countOfDigitsAfterDotForAmount || ''}
              onChange={(e) =>
                setFormData({ ...formData, countOfDigitsAfterDotForAmount: parseInt(e.target.value) || 0 })
              }
              disabled={loading}
              min="0"
              max="20"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Digits After Dot (Price) *
            </label>
            <input
              type="number"
              className="input"
              placeholder="2"
              value={formData.countOfDigitsAfterDotForPrice || ''}
              onChange={(e) =>
                setFormData({ ...formData, countOfDigitsAfterDotForPrice: parseInt(e.target.value) || 0 })
              }
              disabled={loading}
              min="0"
              max="20"
            />
          </div>
        </div>
      </div>

      {/* Submit Button */}
      <div className="flex justify-end gap-4">
        <button type="submit" disabled={loading} className="btn btn-success">
          {loading ? 'Creating Bot...' : 'Create Grid Bot'}
        </button>
      </div>
    </form>
  );
};

export default GridBotForm;
