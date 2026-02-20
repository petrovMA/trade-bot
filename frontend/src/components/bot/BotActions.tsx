import React, { useState, useEffect } from 'react';
import api from '@services/api';
import { BotSettings } from '@/types/bot.types';

interface BotActionsProps {
  botName: string;
  onActionComplete?: () => void;
}

interface ApiResponse {
  status: string;
  data: any;
}

interface BotConfigInfo {
  name: string;
  settings: BotSettings;
}

/**
 * Action buttons for bot control (Load, Start, Resume, View JSON)
 */
const BotActions: React.FC<BotActionsProps> = ({ botName, onActionComplete }) => {
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showJson, setShowJson] = useState(false);
  const [config, setConfig] = useState<BotConfigInfo | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  // Fetch bot config for JSON view
  useEffect(() => {
    const loadConfig = async () => {
      try {
        const response = await api.get<BotConfigInfo[]>('/bot_configs');
        const bot = response.data.find(b => b.name === botName);
        if (bot) {
          setConfig(bot);
        }
      } catch (err) {
        console.error('Error loading config:', err);
      }
    };

    loadConfig();
  }, [botName]);

  const handleLoadBot = async () => {
    try {
      setActionLoading('load');
      setError(null);
      setSuccessMessage(null);

      const response = await api.post<ApiResponse>('/load_bot', botName, {
        headers: {
          'Content-Type': 'text/plain',
        },
      });

      if (response.data.status === 'error') {
        setError(response.data.data as string || `Failed to load bot "${botName}"`);
      } else {
        setSuccessMessage(response.data.data as string || `Bot "${botName}" loaded successfully!`);
        setTimeout(() => setSuccessMessage(null), 5000);
        onActionComplete?.();
      }
    } catch (err: any) {
      const errorMessage = err.response?.data?.data || err.response?.data?.message || err.message || `Failed to load bot "${botName}"`;
      setError(errorMessage);
    } finally {
      setActionLoading(null);
    }
  };

  const handleStartBot = async () => {
    try {
      setActionLoading('start');
      setError(null);
      setSuccessMessage(null);

      const response = await api.post<ApiResponse>('/start_bot', botName, {
        headers: {
          'Content-Type': 'text/plain',
        },
      });

      if (response.data.status === 'error') {
        setError(response.data.data as string || `Failed to start bot "${botName}"`);
      } else {
        setSuccessMessage(response.data.data as string || `Bot "${botName}" started successfully!`);
        setTimeout(() => setSuccessMessage(null), 5000);
        onActionComplete?.();
      }
    } catch (err: any) {
      const errorMessage = err.response?.data?.data || err.response?.data?.message || err.message || `Failed to start bot "${botName}"`;
      setError(errorMessage);
    } finally {
      setActionLoading(null);
    }
  };

  const handleResumeBot = async () => {
    try {
      setActionLoading('resume');
      setError(null);
      setSuccessMessage(null);

      const response = await api.post<ApiResponse>('/resume_bot', botName, {
        headers: {
          'Content-Type': 'text/plain',
        },
      });

      if (response.data.status === 'error') {
        setError(response.data.data as string || `Failed to resume bot "${botName}"`);
      } else {
        setSuccessMessage(response.data.data as string || `Bot "${botName}" resumed successfully!`);
        setTimeout(() => setSuccessMessage(null), 5000);
        onActionComplete?.();
      }
    } catch (err: any) {
      const errorMessage = err.response?.data?.data || err.response?.data?.message || err.message || `Failed to resume bot "${botName}"`;
      setError(errorMessage);
    } finally {
      setActionLoading(null);
    }
  };

  const handleForceSyncBot = async () => {
    try {
      setActionLoading('forcesync');
      setError(null);
      setSuccessMessage(null);

      const response = await api.post<ApiResponse>('/force_sync_bot', botName, {
        headers: {
          'Content-Type': 'text/plain',
        },
      });

      if (response.data.status === 'error') {
        setError(response.data.data as string || `Failed to force sync bot "${botName}"`);
      } else {
        setSuccessMessage(response.data.data as string || `Force sync queued for "${botName}". Check Telegram for result.`);
        setTimeout(() => setSuccessMessage(null), 8000);
        onActionComplete?.();
      }
    } catch (err: any) {
      const errorMessage = err.response?.data?.data || err.response?.data?.message || err.message || `Failed to force sync bot "${botName}"`;
      setError(errorMessage);
    } finally {
      setActionLoading(null);
    }
  };

  const handleDeleteBot = async () => {
    try {
      setActionLoading('delete');
      setError(null);
      setSuccessMessage(null);

      const response = await api.post<ApiResponse>('/delete_bot', botName, {
        headers: {
          'Content-Type': 'text/plain',
        },
      });

      if (response.data.status === 'error') {
        setError(response.data.data as string || `Failed to delete bot "${botName}"`);
      } else {
        setSuccessMessage(response.data.data as string || `Bot "${botName}" deleted successfully!`);
        setTimeout(() => setSuccessMessage(null), 5000);
        onActionComplete?.();
      }
    } catch (err: any) {
      const errorMessage = err.response?.data?.data || err.response?.data?.message || err.message || `Failed to delete bot "${botName}"`;
      setError(errorMessage);
    } finally {
      setActionLoading(null);
      setConfirmDelete(false);
    }
  };

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-5">
      <h3 className="text-md font-semibold text-gray-800 mb-3">Bot Actions</h3>

      {/* Success Message */}
      {successMessage && (
        <div className="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded mb-4">
          {successMessage}
        </div>
      )}

      {/* Error Message */}
      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4 flex justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-red-700 hover:text-red-900">
            ×
          </button>
        </div>
      )}

      {/* Action Buttons */}
      <div className="flex gap-3 flex-wrap">
        <button
          onClick={handleLoadBot}
          disabled={actionLoading !== null}
          className="btn btn-primary"
        >
          {actionLoading === 'load' ? 'Loading...' : 'Load Bot'}
        </button>
        <button
          onClick={handleStartBot}
          disabled={actionLoading !== null}
          className="btn btn-success"
        >
          {actionLoading === 'start' ? 'Starting...' : 'Start Bot'}
        </button>
        <button
          onClick={handleResumeBot}
          disabled={actionLoading !== null}
          className="btn btn-secondary"
        >
          {actionLoading === 'resume' ? 'Resuming...' : 'Resume Bot'}
        </button>
        <button
          onClick={handleForceSyncBot}
          disabled={actionLoading !== null}
          className="btn btn-warning"
          title="Force sync bypasses the safety check that blocks sync when many orders are missing (e.g. after a spike)"
        >
          {actionLoading === 'forcesync' ? 'Syncing...' : '⚡ Force Sync'}
        </button>
        <button
          onClick={() => setShowJson(true)}
          className="btn btn-outline"
        >
          View JSON
        </button>
        <button
          onClick={() => setConfirmDelete(true)}
          disabled={actionLoading !== null}
          className="btn btn-danger"
        >
          {actionLoading === 'delete' ? 'Deleting...' : 'Delete Bot'}
        </button>
      </div>

      {/* Delete Confirmation Dialog */}
      {confirmDelete && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg max-w-md w-full p-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Delete Bot</h3>
            <p className="text-gray-600 mb-4">
              Are you sure you want to delete bot "<strong>{botName}</strong>"? This will stop and remove the bot from memory.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => setConfirmDelete(false)}
                className="btn btn-outline"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteBot}
                disabled={actionLoading === 'delete'}
                className="btn btn-danger"
              >
                {actionLoading === 'delete' ? 'Deleting...' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* JSON Modal */}
      {showJson && config && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg max-w-3xl w-full max-h-[80vh] overflow-auto">
            <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex justify-between items-center">
              <h3 className="text-xl font-semibold">{config.name} - Configuration</h3>
              <button
                onClick={() => setShowJson(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div className="p-6">
              <pre className="bg-gray-50 p-4 rounded-lg overflow-auto text-sm">
                {JSON.stringify(config.settings, null, 2)}
              </pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default BotActions;
