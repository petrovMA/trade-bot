import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '@services/api';
import { BotConfigInfo } from '@/types/bot.types';
import { formatSymbol, getStrategyBadge, getDirectionBadge } from '@/utils/botFormatters';

const BotConfigs = () => {
  const [configs, setConfigs] = useState<BotConfigInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadConfigs = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await api.get<BotConfigInfo[]>('/bot_configs');
      setConfigs(response.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load bot configurations');
      console.error('Error loading configs:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadConfigs();
  }, []);

  const ConfigCard = ({ config }: { config: BotConfigInfo }) => {
    return (
      <Link to={`/configs/${config.name}`} className="block">
        <div className="bg-white border border-gray-200 rounded-lg p-5 hover:shadow-lg hover:border-blue-500 transition-all cursor-pointer">
          <div className="flex justify-between items-start">
            <div>
              <h3 className="text-lg font-semibold text-gray-900">{config.name}</h3>
              <p className="text-sm text-gray-600 mt-1">
                {formatSymbol(config.settings)} on {config.settings.exchange || 'N/A'}
              </p>
            </div>
            <div className="flex gap-2">
              {getStrategyBadge(config.settings)}
              {getDirectionBadge(config.settings)}
            </div>
          </div>
        </div>
      </Link>
    );
  };

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-bold">Bot Configurations</h2>
        <div className="flex gap-2">
          <button
            onClick={loadConfigs}
            disabled={loading}
            className="btn btn-secondary"
          >
            {loading ? 'Refreshing...' : 'Refresh'}
          </button>
          <Link to="/bots/create" className="btn btn-success">
            Create New Bot
          </Link>
          <Link to="/" className="btn btn-outline">
            Dashboard
          </Link>
        </div>
      </div>

      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4 flex justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-red-700 hover:text-red-900">
            ×
          </button>
        </div>
      )}

      {loading ? (
        <div className="text-center py-12">
          <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900"></div>
          <p className="mt-4 text-gray-600">Loading configurations...</p>
        </div>
      ) : configs.length === 0 ? (
        <div className="text-center py-12 bg-white rounded-lg border border-gray-200">
          <p className="text-gray-600 mb-4">No bot configurations found in exchangeBots folder</p>
          <Link to="/bots/create" className="btn btn-primary">
            Create Your First Bot
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {configs.map((config) => (
            <ConfigCard key={config.name} config={config} />
          ))}
        </div>
      )}
    </div>
  );
};

export default BotConfigs;