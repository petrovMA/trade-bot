import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useBot } from '@hooks/useBot';
import { BotSettings } from '@/types/bot.types';
import GridBotForm from '@/components/forms/GridBotForm';
import TraderBotForm from '@/components/forms/TraderBotForm';

const CreateBot = () => {
  const navigate = useNavigate();
  const { loading, error, createBot, clearError } = useBot();
  const [botType, setBotType] = useState<'grid' | 'trader'>('grid');
  const [successMessage, setSuccessMessage] = useState('');

  const handleSubmit = async (settings: BotSettings) => {
    setSuccessMessage('');
    try {
      await createBot(settings);
      setSuccessMessage(`Bot "${settings.name}" created successfully!`);

      // Redirect to dashboard after 2 seconds
      setTimeout(() => {
        navigate('/');
      }, 2000);
    } catch (err) {
      // Error is handled by useBot hook
    }
  };

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-bold">Create New Bot</h2>
        <Link to="/" className="btn btn-secondary">
          Back to Dashboard
        </Link>
      </div>

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
          <p className="text-sm mt-1">Redirecting to dashboard...</p>
        </div>
      )}

      {/* Bot Type Selector */}
      <div className="card mb-6">
        <h3 className="text-lg font-semibold mb-4">Select Bot Type</h3>
        <div className="flex gap-4">
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="radio"
              name="botType"
              value="grid"
              checked={botType === 'grid'}
              onChange={() => setBotType('grid')}
              disabled={loading}
              className="w-4 h-4"
            />
            <span className="text-sm font-medium">Grid Trading Bot</span>
          </label>
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="radio"
              name="botType"
              value="trader"
              checked={botType === 'trader'}
              onChange={() => setBotType('trader')}
              disabled={loading}
              className="w-4 h-4"
            />
            <span className="text-sm font-medium">Trader Bot (DCA/Trend)</span>
          </label>
        </div>
      </div>

      {/* Conditional Form Rendering */}
      {botType === 'grid' ? (
        <GridBotForm onSubmit={handleSubmit} loading={loading} />
      ) : (
        <TraderBotForm onSubmit={handleSubmit} loading={loading} />
      )}
    </div>
  );
};

export default CreateBot;
