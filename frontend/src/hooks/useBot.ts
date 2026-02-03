import { useState } from 'react';
import { botService } from '../services/bot.service';
import { BotSettings } from '../types/bot.types';
import { ApiResponse } from '../types/api.types';

interface UseBotResult {
  loading: boolean;
  error: string | null;
  createBot: (settings: BotSettings) => Promise<string>;
  loadBot: (name: string) => Promise<string>;
  startBot: (name: string) => Promise<string>;
  resumeBot: (name: string) => Promise<string>;
  clearError: () => void;
}

export const useBot = (): UseBotResult => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRequest = async (
    requestFn: () => Promise<ApiResponse>,
    successMessage?: string
  ): Promise<string> => {
    setLoading(true);
    setError(null);
    try {
      const response = await requestFn();

      // Check for error status in response
      if (response.status === 'error') {
        const errorMsg = (response.data as string) || 'An error occurred';
        setError(errorMsg);
        throw new Error(errorMsg);
      }

      const resultMessage = (response.data as string) || successMessage || 'Success';
      console.log(resultMessage);
      return resultMessage;
    } catch (err: any) {
      const errorMessage = err.response?.data?.data || err.response?.data?.message || err.message || 'An error occurred';
      setError(errorMessage);
      throw err;
    } finally {
      setLoading(false);
    }
  };

  const createBot = async (settings: BotSettings): Promise<string> => {
    return await handleRequest(
      () => botService.createBot(settings),
      'Bot created successfully'
    );
  };

  const loadBot = async (name: string): Promise<string> => {
    return await handleRequest(
      () => botService.loadBot(name),
      `Bot ${name} loaded successfully`
    );
  };

  const startBot = async (name: string): Promise<string> => {
    return await handleRequest(
      () => botService.startBot(name),
      `Bot ${name} started successfully`
    );
  };

  const resumeBot = async (name: string): Promise<string> => {
    return await handleRequest(
      () => botService.resumeBot(name),
      `Bot ${name} resumed successfully`
    );
  };

  const clearError = () => setError(null);

  return {
    loading,
    error,
    createBot,
    loadBot,
    startBot,
    resumeBot,
    clearError,
  };
};
