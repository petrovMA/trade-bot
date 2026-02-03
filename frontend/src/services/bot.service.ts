import api from './api';
import { BotSettings, BotInfo, BalanceRequest, BalanceRequirement } from '../types/bot.types';
import { ApiResponse } from '../types/api.types';

export const botService = {
  // Create new bot
  createBot: async (settings: BotSettings): Promise<ApiResponse> => {
    const response = await api.post<ApiResponse>('/create_bot', JSON.stringify(settings));
    return response.data;
  },

  // Load bot from file
  loadBot: async (botName: string): Promise<ApiResponse> => {
    const response = await api.post<ApiResponse>('/load_bot', botName, {
      headers: {
        'Content-Type': 'text/plain',
      },
    });
    return response.data;
  },

  // Start bot
  startBot: async (botName: string): Promise<ApiResponse> => {
    const response = await api.post<ApiResponse>('/start_bot', botName, {
      headers: {
        'Content-Type': 'text/plain',
      },
    });
    return response.data;
  },

  // Resume bot
  resumeBot: async (botName: string): Promise<ApiResponse> => {
    const response = await api.post<ApiResponse>('/resume_bot', botName, {
      headers: {
        'Content-Type': 'text/plain',
      },
    });
    return response.data;
  },

  // Get all bot positions
  getPositions: async (): Promise<BotInfo[]> => {
    const response = await api.get<BotInfo[]>('/positions');
    return response.data;
  },

  // Send trade command
  sendTradeCommand: async (command: string): Promise<ApiResponse> => {
    const response = await api.post<ApiResponse>('/endpoint/trade', command);
    return response.data;
  },

  // Calculate necessary balance for grid bot
  calculateBalance: async (request: BalanceRequest): Promise<BalanceRequirement> => {
    const response = await api.post<BalanceRequirement>('/get_necessary_balance', JSON.stringify(request));
    return response.data;
  },
};
