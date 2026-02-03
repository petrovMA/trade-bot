import { useState } from 'react';
import { BotSettingsTrader, OrderType, StrategyType } from '@/types/bot.types';
import TradePairInput from './TradePairInput';
import ParamInput from './ParamInput';
import { EXCHANGES, ORDER_TYPES, STRATEGY_TYPES, MARKET_TYPES } from '@utils/constants';

interface TraderBotFormProps {
  onSubmit: (settings: BotSettingsTrader) => void;
  loading: boolean;
}

const TraderBotForm = ({ onSubmit, loading }: TraderBotFormProps) => {
  const [formData, setFormData] = useState<BotSettingsTrader>({
    type: 'AlgorithmTrader',
    name: '',
    pair: { first: '', second: '' },
    exchange: 'Bybit',
    strategyType: 'LONG' as StrategyType,
    orderType: 'LIMIT' as OrderType,
    marketType: 'FUTURES',
    marketTypeComment: 'FUTURES',
    strategyTypeComment: 'LONG',
    autoBalance: true,
    parameters: {
      longParameters: {
        trading_range: { lower_bound: 0, upper_bound: 0 },
        inOrderQuantity: { value: 0, use_percent: true },
        inOrderDistance: { value: 0, use_percent: true },
        triggerDistance: { value: 0, use_percent: true },
        minTpDistance: { value: 0, use_percent: true },
        maxTpDistance: { value: 0, use_percent: true },
        maxTriggerCount: 10,
        setCloseOrders: true,
      },
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

    // Validate LONG parameters if strategy includes LONG
    if (formData.strategyType === 'LONG' || formData.strategyType === 'BOTH') {
      const long = formData.parameters.longParameters;
      if (!long) {
        newErrors.longParams = 'Long parameters are required';
      } else {
        if (long.trading_range.lower_bound <= 0) newErrors.longLowerBound = 'Lower bound must be positive';
        if (long.trading_range.upper_bound <= 0) newErrors.longUpperBound = 'Upper bound must be positive';
        if (long.trading_range.upper_bound <= long.trading_range.lower_bound) {
          newErrors.longUpperBound = 'Upper bound must be greater than lower bound';
        }
        if (long.inOrderQuantity.value <= 0) newErrors.longInOrderQty = 'In order quantity must be positive';
        if (long.inOrderDistance.value <= 0) newErrors.longInOrderDist = 'In order distance must be positive';
        if (long.triggerDistance.value <= 0) newErrors.longTriggerDist = 'Trigger distance must be positive';
        if (long.minTpDistance.value <= 0) newErrors.longMinTp = 'Min TP distance must be positive';
        if (long.maxTpDistance.value <= 0) newErrors.longMaxTp = 'Max TP distance must be positive';
        if (long.maxTriggerCount <= 0) newErrors.longMaxTrigger = 'Max trigger count must be positive';
      }
    }

    // Validate SHORT parameters if strategy includes SHORT
    if (formData.strategyType === 'SHORT' || formData.strategyType === 'BOTH') {
      const short = formData.parameters.shortParameters;
      if (!short) {
        newErrors.shortParams = 'Short parameters are required';
      } else {
        if (short.trading_range.lower_bound <= 0) newErrors.shortLowerBound = 'Lower bound must be positive';
        if (short.trading_range.upper_bound <= 0) newErrors.shortUpperBound = 'Upper bound must be positive';
        if (short.trading_range.upper_bound <= short.trading_range.lower_bound) {
          newErrors.shortUpperBound = 'Upper bound must be greater than lower bound';
        }
        if (short.inOrderQuantity.value <= 0) newErrors.shortInOrderQty = 'In order quantity must be positive';
        if (short.inOrderDistance.value <= 0) newErrors.shortInOrderDist = 'In order distance must be positive';
        if (short.triggerDistance.value <= 0) newErrors.shortTriggerDist = 'Trigger distance must be positive';
        if (short.minTpDistance.value <= 0) newErrors.shortMinTp = 'Min TP distance must be positive';
        if (short.maxTpDistance.value <= 0) newErrors.shortMaxTp = 'Max TP distance must be positive';
        if (short.maxTriggerCount <= 0) newErrors.shortMaxTrigger = 'Max trigger count must be positive';
      }
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

  const handleStrategyChange = (newStrategy: StrategyType) => {
    const updated = { ...formData, strategyType: newStrategy, strategyTypeComment: newStrategy };

    // Initialize SHORT parameters if needed
    if ((newStrategy === 'SHORT' || newStrategy === 'BOTH') && !updated.parameters.shortParameters) {
      updated.parameters.shortParameters = {
        trading_range: { lower_bound: 0, upper_bound: 0 },
        inOrderQuantity: { value: 0, use_percent: true },
        inOrderDistance: { value: 0, use_percent: true },
        triggerDistance: { value: 0, use_percent: true },
        minTpDistance: { value: 0, use_percent: true },
        maxTpDistance: { value: 0, use_percent: true },
        maxTriggerCount: 10,
        setCloseOrders: true,
      };
    }

    setFormData(updated);
  };

  const showLongParams = formData.strategyType === 'LONG' || formData.strategyType === 'BOTH';
  const showShortParams = formData.strategyType === 'SHORT' || formData.strategyType === 'BOTH';

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
              placeholder="my-trader-bot"
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
              Strategy Type *
            </label>
            <select
              className="input"
              value={formData.strategyType}
              onChange={(e) => handleStrategyChange(e.target.value as StrategyType)}
              disabled={loading}
            >
              {STRATEGY_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
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
              value={formData.orderType}
              onChange={(e) => setFormData({ ...formData, orderType: e.target.value as OrderType })}
              disabled={loading}
            >
              {ORDER_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Market Type *
            </label>
            <select
              className="input"
              value={formData.marketType}
              onChange={(e) => setFormData({ ...formData, marketType: e.target.value, marketTypeComment: e.target.value })}
              disabled={loading}
            >
              {MARKET_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </div>

          <div className="flex items-center">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={formData.autoBalance}
                onChange={(e) => setFormData({ ...formData, autoBalance: e.target.checked })}
                disabled={loading}
                className="rounded border-gray-300"
              />
              <span className="text-sm font-medium text-gray-700">Auto Balance</span>
            </label>
          </div>
        </div>
      </div>

      {/* LONG Parameters */}
      {showLongParams && formData.parameters.longParameters && (
        <div className="card">
          <h3 className="text-lg font-semibold mb-4">LONG Parameters</h3>
          <div className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Lower Bound *
                </label>
                <input
                  type="number"
                  className="input"
                  value={formData.parameters.longParameters.trading_range.lower_bound || ''}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      parameters: {
                        ...formData.parameters,
                        longParameters: formData.parameters.longParameters
                          ? {
                              ...formData.parameters.longParameters,
                              trading_range: {
                                ...formData.parameters.longParameters.trading_range,
                                lower_bound: parseFloat(e.target.value) || 0,
                              },
                            }
                          : undefined,
                      },
                    })
                  }
                  disabled={loading}
                  min="0"
                  step="any"
                />
                {errors.longLowerBound && <p className="text-red-600 text-sm mt-1">{errors.longLowerBound}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Upper Bound *
                </label>
                <input
                  type="number"
                  className="input"
                  value={formData.parameters.longParameters.trading_range.upper_bound || ''}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      parameters: {
                        ...formData.parameters,
                        longParameters: formData.parameters.longParameters
                          ? {
                              ...formData.parameters.longParameters,
                              trading_range: {
                                ...formData.parameters.longParameters.trading_range,
                                upper_bound: parseFloat(e.target.value) || 0,
                              },
                            }
                          : undefined,
                      },
                    })
                  }
                  disabled={loading}
                  min="0"
                  step="any"
                />
                {errors.longUpperBound && <p className="text-red-600 text-sm mt-1">{errors.longUpperBound}</p>}
              </div>
            </div>

            <ParamInput
              label="In Order Quantity *"
              value={formData.parameters.longParameters.inOrderQuantity}
              onChange={(inOrderQuantity) =>
                setFormData({
                  ...formData,
                  parameters: {
                    ...formData.parameters,
                    longParameters: formData.parameters.longParameters
                      ? { ...formData.parameters.longParameters, inOrderQuantity }
                      : undefined,
                  },
                })
              }
              disabled={loading}
            />
            {errors.longInOrderQty && <p className="text-red-600 text-sm mt-1">{errors.longInOrderQty}</p>}

            <ParamInput
              label="In Order Distance *"
              value={formData.parameters.longParameters.inOrderDistance}
              onChange={(inOrderDistance) =>
                setFormData({
                  ...formData,
                  parameters: {
                    ...formData.parameters,
                    longParameters: formData.parameters.longParameters
                      ? { ...formData.parameters.longParameters, inOrderDistance }
                      : undefined,
                  },
                })
              }
              disabled={loading}
            />
            {errors.longInOrderDist && <p className="text-red-600 text-sm mt-1">{errors.longInOrderDist}</p>}

            <ParamInput
              label="Trigger Distance *"
              value={formData.parameters.longParameters.triggerDistance}
              onChange={(triggerDistance) =>
                setFormData({
                  ...formData,
                  parameters: {
                    ...formData.parameters,
                    longParameters: formData.parameters.longParameters
                      ? { ...formData.parameters.longParameters, triggerDistance }
                      : undefined,
                  },
                })
              }
              disabled={loading}
            />
            {errors.longTriggerDist && <p className="text-red-600 text-sm mt-1">{errors.longTriggerDist}</p>}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <ParamInput
                  label="Min TP Distance *"
                  value={formData.parameters.longParameters.minTpDistance}
                  onChange={(minTpDistance) =>
                    setFormData({
                      ...formData,
                      parameters: {
                        ...formData.parameters,
                        longParameters: formData.parameters.longParameters
                          ? { ...formData.parameters.longParameters, minTpDistance }
                          : undefined,
                      },
                    })
                  }
                  disabled={loading}
                />
                {errors.longMinTp && <p className="text-red-600 text-sm mt-1">{errors.longMinTp}</p>}
              </div>

              <div>
                <ParamInput
                  label="Max TP Distance *"
                  value={formData.parameters.longParameters.maxTpDistance}
                  onChange={(maxTpDistance) =>
                    setFormData({
                      ...formData,
                      parameters: {
                        ...formData.parameters,
                        longParameters: formData.parameters.longParameters
                          ? { ...formData.parameters.longParameters, maxTpDistance }
                          : undefined,
                      },
                    })
                  }
                  disabled={loading}
                />
                {errors.longMaxTp && <p className="text-red-600 text-sm mt-1">{errors.longMaxTp}</p>}
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Max Trigger Count *
              </label>
              <input
                type="number"
                className="input"
                value={formData.parameters.longParameters.maxTriggerCount || ''}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    parameters: {
                      ...formData.parameters,
                      longParameters: formData.parameters.longParameters
                        ? {
                            ...formData.parameters.longParameters,
                            maxTriggerCount: parseInt(e.target.value) || 0,
                          }
                        : undefined,
                    },
                  })
                }
                disabled={loading}
                min="1"
              />
              {errors.longMaxTrigger && <p className="text-red-600 text-sm mt-1">{errors.longMaxTrigger}</p>}
            </div>
          </div>
        </div>
      )}

      {/* SHORT Parameters */}
      {showShortParams && formData.parameters.shortParameters && (
        <div className="card">
          <h3 className="text-lg font-semibold mb-4">SHORT Parameters</h3>
          <div className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Lower Bound *
                </label>
                <input
                  type="number"
                  className="input"
                  value={formData.parameters.shortParameters.trading_range.lower_bound || ''}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      parameters: {
                        ...formData.parameters,
                        shortParameters: formData.parameters.shortParameters
                          ? {
                              ...formData.parameters.shortParameters,
                              trading_range: {
                                ...formData.parameters.shortParameters.trading_range,
                                lower_bound: parseFloat(e.target.value) || 0,
                              },
                            }
                          : undefined,
                      },
                    })
                  }
                  disabled={loading}
                  min="0"
                  step="any"
                />
                {errors.shortLowerBound && <p className="text-red-600 text-sm mt-1">{errors.shortLowerBound}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Upper Bound *
                </label>
                <input
                  type="number"
                  className="input"
                  value={formData.parameters.shortParameters.trading_range.upper_bound || ''}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      parameters: {
                        ...formData.parameters,
                        shortParameters: formData.parameters.shortParameters
                          ? {
                              ...formData.parameters.shortParameters,
                              trading_range: {
                                ...formData.parameters.shortParameters.trading_range,
                                upper_bound: parseFloat(e.target.value) || 0,
                              },
                            }
                          : undefined,
                      },
                    })
                  }
                  disabled={loading}
                  min="0"
                  step="any"
                />
                {errors.shortUpperBound && <p className="text-red-600 text-sm mt-1">{errors.shortUpperBound}</p>}
              </div>
            </div>

            <ParamInput
              label="In Order Quantity *"
              value={formData.parameters.shortParameters.inOrderQuantity}
              onChange={(inOrderQuantity) =>
                setFormData({
                  ...formData,
                  parameters: {
                    ...formData.parameters,
                    shortParameters: formData.parameters.shortParameters
                      ? { ...formData.parameters.shortParameters, inOrderQuantity }
                      : undefined,
                  },
                })
              }
              disabled={loading}
            />
            {errors.shortInOrderQty && <p className="text-red-600 text-sm mt-1">{errors.shortInOrderQty}</p>}

            <ParamInput
              label="In Order Distance *"
              value={formData.parameters.shortParameters.inOrderDistance}
              onChange={(inOrderDistance) =>
                setFormData({
                  ...formData,
                  parameters: {
                    ...formData.parameters,
                    shortParameters: formData.parameters.shortParameters
                      ? { ...formData.parameters.shortParameters, inOrderDistance }
                      : undefined,
                  },
                })
              }
              disabled={loading}
            />
            {errors.shortInOrderDist && <p className="text-red-600 text-sm mt-1">{errors.shortInOrderDist}</p>}

            <ParamInput
              label="Trigger Distance *"
              value={formData.parameters.shortParameters.triggerDistance}
              onChange={(triggerDistance) =>
                setFormData({
                  ...formData,
                  parameters: {
                    ...formData.parameters,
                    shortParameters: formData.parameters.shortParameters
                      ? { ...formData.parameters.shortParameters, triggerDistance }
                      : undefined,
                  },
                })
              }
              disabled={loading}
            />
            {errors.shortTriggerDist && <p className="text-red-600 text-sm mt-1">{errors.shortTriggerDist}</p>}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <ParamInput
                  label="Min TP Distance *"
                  value={formData.parameters.shortParameters.minTpDistance}
                  onChange={(minTpDistance) =>
                    setFormData({
                      ...formData,
                      parameters: {
                        ...formData.parameters,
                        shortParameters: formData.parameters.shortParameters
                          ? { ...formData.parameters.shortParameters, minTpDistance }
                          : undefined,
                      },
                    })
                  }
                  disabled={loading}
                />
                {errors.shortMinTp && <p className="text-red-600 text-sm mt-1">{errors.shortMinTp}</p>}
              </div>

              <div>
                <ParamInput
                  label="Max TP Distance *"
                  value={formData.parameters.shortParameters.maxTpDistance}
                  onChange={(maxTpDistance) =>
                    setFormData({
                      ...formData,
                      parameters: {
                        ...formData.parameters,
                        shortParameters: formData.parameters.shortParameters
                          ? { ...formData.parameters.shortParameters, maxTpDistance }
                          : undefined,
                      },
                    })
                  }
                  disabled={loading}
                />
                {errors.shortMaxTp && <p className="text-red-600 text-sm mt-1">{errors.shortMaxTp}</p>}
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Max Trigger Count *
              </label>
              <input
                type="number"
                className="input"
                value={formData.parameters.shortParameters.maxTriggerCount || ''}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    parameters: {
                      ...formData.parameters,
                      shortParameters: formData.parameters.shortParameters
                        ? {
                            ...formData.parameters.shortParameters,
                            maxTriggerCount: parseInt(e.target.value) || 0,
                          }
                        : undefined,
                    },
                  })
                }
                disabled={loading}
                min="1"
              />
              {errors.shortMaxTrigger && <p className="text-red-600 text-sm mt-1">{errors.shortMaxTrigger}</p>}
            </div>
          </div>
        </div>
      )}

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
          {loading ? 'Creating Bot...' : 'Create Trader Bot'}
        </button>
      </div>
    </form>
  );
};

export default TraderBotForm;
