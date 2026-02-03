import React from 'react';

interface ConfigCardProps {
  title: string;
  children: React.ReactNode;
  className?: string;
}

/**
 * Reusable card wrapper for displaying configuration sections
 */
const ConfigCard: React.FC<ConfigCardProps> = ({ title, children, className = '' }) => {
  return (
    <div className={`bg-white border border-gray-200 rounded-lg p-5 ${className}`}>
      <h3 className="text-md font-semibold text-gray-800 mb-3 border-b pb-2">
        {title}
      </h3>
      <div className="space-y-2">
        {children}
      </div>
    </div>
  );
};

export default ConfigCard;
