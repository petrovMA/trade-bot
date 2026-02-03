import { useState } from 'react';
import { botService } from '../services/bot.service';
import { BotInfo } from '../types/bot.types';
import { usePolling } from './usePolling';

export const useBots = (pollingInterval: number = 5000) => {
  const [bots, setBots] = useState<BotInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchBots = async () => {
    try {
      const data = await botService.getPositions();
      console.log('Fetched bots:', data);
      setBots(data);
      setError(null);
    } catch (err: any) {
      console.error('Error fetching bots:', err);
      setError(err.message || 'Failed to fetch bots');
    } finally {
      setLoading(false);
    }
  };

  usePolling(fetchBots, { interval: pollingInterval });

  return { bots, loading, error, refetch: fetchBots };
};
