import { useBots } from '@hooks/useBots';
import { POLLING_INTERVALS } from '@utils/constants';
import { Link } from 'react-router-dom';
import { useState } from 'react';
import api from '@services/api';

const Dashboard = () => {
  const { bots, loading, error, refetch } = useBots(POLLING_INTERVALS.BOT_STATUS);
  const [deletingBot, setDeletingBot] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);

  const handleDeleteBot = async (botName: string) => {
    try {
      setDeletingBot(botName);
      setDeleteError(null);

      const response = await api.post<{ status: string; data: string }>('/delete_bot', botName, {
        headers: {
          'Content-Type': 'text/plain',
        },
      });

      if (response.data.status === 'error') {
        setDeleteError(response.data.data || `Failed to delete bot "${botName}"`);
      } else {
        // Refresh the bots list after successful deletion
        refetch?.();
        setConfirmDelete(null);
      }
    } catch (err: any) {
      const errorMessage = err.response?.data?.data || err.response?.data?.message || err.message || `Failed to delete bot "${botName}"`;
      setDeleteError(errorMessage);
    } finally {
      setDeletingBot(null);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-xl text-gray-600">Loading bots...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded">
        Error: {error}
      </div>
    );
  }

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-bold">Active Bots</h2>
        <div className="flex gap-2">
          <Link to="/bots/create" className="btn btn-success">
            Create Bot
          </Link>
          <Link to="/bots" className="btn btn-primary">
            Manage Bots
          </Link>
        </div>
      </div>

      {deleteError && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4 flex justify-between">
          <span>{deleteError}</span>
          <button onClick={() => setDeleteError(null)} className="text-red-700 hover:text-red-900">
            ×
          </button>
        </div>
      )}

      {bots.length === 0 ? (
        <div className="card text-center text-gray-500">
          No active bots found. Create or load a bot to get started.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {bots.map((bot, index) => (
            <div key={index} className="card hover:shadow-lg transition-shadow relative">
              <button
                onClick={(e) => {
                  e.preventDefault();
                  setConfirmDelete(bot.settings.name);
                }}
                className="absolute top-3 right-3 text-red-500 hover:text-red-700 text-xl font-bold"
                title="Delete bot"
                disabled={deletingBot === bot.settings.name}
              >
                {deletingBot === bot.settings.name ? '...' : '×'}
              </button>

              <Link to={`/configs/${bot.settings.name}`} className="block">
                <h3 className="text-lg font-semibold mb-2 pr-8">{bot.settings.name}</h3>
                <div className="text-sm text-gray-600 space-y-1">
                  <p>Type: {bot.settings.type}</p>
                  <p>Exchange: {bot.settings.exchange}</p>
                  <p>
                    Pair: {bot.settings.pair.first}/{bot.settings.pair.second}
                  </p>
                  {bot.position ? (
                    <>
                      <p className="mt-2 font-medium">Position:</p>
                      <p>Size: {bot.position.size}</p>
                      <p>Entry: {bot.position.entryPrice}</p>
                      <p
                        className={
                          bot.position.unrealisedPnl >= 0
                            ? 'text-green-600'
                            : 'text-red-600'
                        }
                      >
                        PnL: {bot.position.unrealisedPnl.toFixed(2)}
                      </p>
                    </>
                  ) : (
                    <p className="mt-2 text-sm text-gray-500">No position data available</p>
                  )}
                </div>
              </Link>
            </div>
          ))}
        </div>
      )}

      {/* Confirmation Dialog */}
      {confirmDelete && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg max-w-md w-full p-6">
            <h3 className="text-xl font-semibold mb-4">Confirm Delete</h3>
            <p className="text-gray-600 mb-6">
              Are you sure you want to delete bot "{confirmDelete}"? This will stop and remove the bot from memory.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => setConfirmDelete(null)}
                className="btn btn-outline"
                disabled={deletingBot !== null}
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  handleDeleteBot(confirmDelete);
                }}
                className="btn btn-danger"
                disabled={deletingBot !== null}
              >
                {deletingBot === confirmDelete ? 'Deleting...' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Dashboard;
