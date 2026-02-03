import { useState } from 'react';
import { useBot } from '@hooks/useBot';
import { Link } from 'react-router-dom';

const BotManagement = () => {
  const { loading, error, loadBot, startBot, resumeBot, clearError } = useBot();
  const [botName, setBotName] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const handleLoad = async () => {
    if (!botName.trim()) return;
    setSuccessMessage('');
    try {
      const result = await loadBot(botName);
      setSuccessMessage(result || `Bot "${botName}" loaded successfully!`);
      setBotName('');
    } catch (err) {
      // Error is handled by useBot hook
    }
  };

  const handleStart = async () => {
    if (!botName.trim()) return;
    setSuccessMessage('');
    try {
      const result = await startBot(botName);
      setSuccessMessage(result || `Bot "${botName}" started successfully!`);
      setBotName('');
    } catch (err) {
      // Error is handled by useBot hook
    }
  };

  const handleResume = async () => {
    if (!botName.trim()) return;
    setSuccessMessage('');
    try {
      const result = await resumeBot(botName);
      setSuccessMessage(result || `Bot "${botName}" resumed successfully!`);
      setBotName('');
    } catch (err) {
      // Error is handled by useBot hook
    }
  };

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-bold">Bot Management</h2>
        <div className="flex gap-2">
          <Link to="/bots/create" className="btn btn-success">
            Create New Bot
          </Link>
          <Link to="/" className="btn btn-secondary">
            Back to Dashboard
          </Link>
        </div>
      </div>

      <div className="card max-w-2xl">
        <h3 className="text-xl font-semibold mb-4">Bot Operations</h3>

        {error && (
          <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4 flex justify-between">
            <span>{error}</span>
            <button onClick={clearError} className="text-red-700 hover:text-red-900">
              ×
            </button>
          </div>
        )}

        {successMessage && (
          <div className="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded mb-4">
            {successMessage}
          </div>
        )}

        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Bot Name
            </label>
            <input
              type="text"
              className="input"
              placeholder="Enter bot name..."
              value={botName}
              onChange={(e) => setBotName(e.target.value)}
              disabled={loading}
            />
          </div>

          <div className="flex gap-4">
            <button
              onClick={handleLoad}
              disabled={loading || !botName.trim()}
              className="btn btn-primary flex-1"
            >
              {loading ? 'Loading...' : 'Load Bot'}
            </button>
            <button
              onClick={handleStart}
              disabled={loading || !botName.trim()}
              className="btn btn-success flex-1"
            >
              {loading ? 'Starting...' : 'Start Bot'}
            </button>
            <button
              onClick={handleResume}
              disabled={loading || !botName.trim()}
              className="btn btn-secondary flex-1"
            >
              {loading ? 'Resuming...' : 'Resume Bot'}
            </button>
          </div>
        </div>

        <div className="mt-6 pt-6 border-t border-gray-200">
          <h4 className="font-medium mb-2">Instructions:</h4>
          <ul className="text-sm text-gray-600 space-y-1 list-disc list-inside">
            <li>
              <strong>Load Bot:</strong> Load bot configuration from
              exchangeBots/[botName]/settings.json
            </li>
            <li>
              <strong>Start Bot:</strong> Start bot with balance validation and grid
              order initialization
            </li>
            <li>
              <strong>Resume Bot:</strong> Resume bot without clearing previous data
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default BotManagement;
