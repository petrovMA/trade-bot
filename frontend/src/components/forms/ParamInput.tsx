import { Param } from '@/types/bot.types';

interface ParamInputProps {
  label: string;
  value: Param;
  onChange: (param: Param) => void;
  disabled?: boolean;
  min?: number;
}

const ParamInput = ({ label, value, onChange, disabled = false, min = 0 }: ParamInputProps) => {
  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-2">
        {label}
      </label>
      <div className="flex gap-2 items-center">
        <input
          type="number"
          className="input flex-1"
          placeholder="0.0"
          value={value.value || ''}
          onChange={(e) => onChange({ ...value, value: parseFloat(e.target.value) || 0 })}
          disabled={disabled}
          min={min}
          step="any"
        />
        <label className="flex items-center gap-2 text-sm whitespace-nowrap">
          <input
            type="checkbox"
            checked={value.use_percent}
            onChange={(e) => onChange({ ...value, use_percent: e.target.checked })}
            disabled={disabled}
            className="rounded border-gray-300"
          />
          Use %
        </label>
      </div>
    </div>
  );
};

export default ParamInput;
