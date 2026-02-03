import React from 'react';

interface ConfigRowProps {
  label: string;
  value: string | number;
  badge?: boolean;
  badgeColor?: string;
}

/**
 * Display a label-value pair in a configuration card
 */
const ConfigRow: React.FC<ConfigRowProps> = ({ label, value, badge = false, badgeColor }) => {
  return (
    <div className="flex justify-between items-center py-1">
      <span className="text-sm text-gray-600">{label}:</span>
      {badge ? (
        <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${badgeColor || 'bg-gray-100 text-gray-800'}`}>
          {value}
        </span>
      ) : (
        <span className="text-sm font-medium text-gray-900">{value}</span>
      )}
    </div>
  );
};

export default ConfigRow;
