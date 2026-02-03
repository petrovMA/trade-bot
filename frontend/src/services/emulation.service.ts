import api from './api';
import { EmulateParams, TestBalance } from '../types/api.types';
import { BalanceRequirement, BalanceRequest } from '../types/bot.types';

export const emulationService = {
  // Run emulation
  emulate: async (params: EmulateParams): Promise<TestBalance> => {
    const response = await api.post<TestBalance>('/emulate', JSON.stringify(params));
    return response.data;
  },

  // Calculate required balance
  getNecessaryBalance: async (request: BalanceRequest): Promise<BalanceRequirement> => {
    const response = await api.post<BalanceRequirement>(
      '/get_necessary_balance',
      JSON.stringify(request)
    );
    return response.data;
  },
};
