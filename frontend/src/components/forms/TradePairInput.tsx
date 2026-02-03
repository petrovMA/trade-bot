import { TradePair } from '@/types/bot.types';

interface TradePairInputProps {
  value: TradePair;
  onChange: (pair: TradePair) => void;
  disabled?: boolean;
}

const TradePairInput = ({ value, onChange, disabled = false }: TradePairInputProps) => {
  return (
    <div className="grid grid-cols-2 gap-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">
          Base Currency
        </label>
        <input
          type="text"
          className="input"
          placeholder="BTC"
          value={value.first}
          onChange={(e) => onChange({ ...value, first: e.target.value.toUpperCase() })}
          disabled={disabled}
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">
          Quote Currency
        </label>
        <input
          type="text"
          className="input"
          placeholder="USDT"
          value={value.second}
          onChange={(e) => onChange({ ...value, second: e.target.value.toUpperCase() })}
          disabled={disabled}
        />
      </div>
    </div>
  );
};

export default TradePairInput;
