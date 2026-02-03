import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import api from '@services/api';
import { BotSettings } from '@/types/bot.types';
import { formatSymbol, getStrategyBadge, getDirectionBadge } from '@/utils/botFormatters';
import BotConfigDisplay from '@components/bot/BotConfigDisplay';
import BotActions from '@components/bot/BotActions';
import BalanceCalculator from '@components/bot/BalanceCalculator';
import BotPosition from '@components/bot/BotPosition';
import BotOrders from '@components/bot/BotOrders';

interface BotConfigInfo {
  name: string;
  settings: BotSettings;
}

/**
 * Bot Detail Page
 * Displays comprehensive information about a specific bot
 */
const BotDetail = () => {
  const { botName } = useParams<{ botName: string }>();
  const navigate = useNavigate();

  const [config, setConfig] = useState<BotConfigInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadBotData = async () => {
      if (!botName) {
        setError('Bot name not provided');
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        setError(null);

        const response = await api.get<BotConfigInfo[]>('/bot_configs');
        const bot = response.data.find(b => b.name === botName);

        if (!bot) {
          setError('Bot not found');
          return;
        }

        setConfig(bot);
      } catch (err: any) {
        const errorMessage = err.response?.data?.message || err.message || 'Failed to load bot data';
        setError(errorMessage);
      } finally {
        setLoading(false);
      }
    };

    loadBotData();
  }, [botName]);

  // Loading State
  if (loading) {
    return (
      <div className="text-center py-12">
        <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
        <p className="mt-4 text-gray-600">Loading bot details...</p>
      </div>
    );
  }

  // Bot Not Found
  if (error === 'Bot not found') {
    return (
      <div className="text-center py-12">
        <div className="mb-6">
          <svg
            className="mx-auto h-16 w-16 text-gray-400"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
        </div>
        <h2 className="text-2xl font-bold text-gray-800 mb-4">Bot Not Found</h2>
        <p className="text-gray-600 mb-6">
          The bot "{botName}" does not exist or has been deleted.
        </p>
        <Link to="/configs" className="btn btn-primary">
          Back to Bot List
        </Link>
      </div>
    );
  }

  // Error State
  if (error) {
    return (
      <div className="text-center py-12">
        <div className="mb-6">
          <svg
            className="mx-auto h-16 w-16 text-red-500"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
        </div>
        <h2 className="text-2xl font-bold text-gray-800 mb-4">Error Loading Bot</h2>
        <p className="text-gray-600 mb-6">{error}</p>
        <div className="flex gap-3 justify-center">
          <button onClick={() => window.location.reload()} className="btn btn-primary">
            Retry
          </button>
          <Link to="/configs" className="btn btn-outline">
            Back to Bot List
          </Link>
        </div>
      </div>
    );
  }

  // Main Content
  if (!config) return null;

  return (
    <div className="space-y-6">
      {/* Header Section */}
      <div className="flex flex-col md:flex-row md:justify-between md:items-center gap-4 bg-white border border-gray-200 rounded-lg p-5">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/configs')}
            className="btn btn-outline flex items-center gap-2"
          >
            <svg
              className="w-4 h-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M15 19l-7-7 7-7"
              />
            </svg>
            Back
          </button>
          <div>
            <h2 className="text-2xl font-bold text-gray-900">{config.name}</h2>
            <p className="text-gray-600 mt-1">
              {formatSymbol(config.settings)} on {config.settings.exchange}
            </p>
          </div>
        </div>
        <div className="flex gap-2">
          {getStrategyBadge(config.settings)}
          {getDirectionBadge(config.settings)}
        </div>
      </div>

      {/* Action Buttons */}
      <BotActions botName={botName!} />

      {/* Configuration Display */}
      <BotConfigDisplay config={config} />

      {/* Balance Calculator (Grid bots only) */}
      {config.settings.type === 'AlgorithmGrid' && (
        <BalanceCalculator config={config} />
      )}

      {/* Position */}
      <BotPosition botName={botName!} />

      {/* Orders */}
      <BotOrders botName={botName!} />
    </div>
  );
};

export default BotDetail;
