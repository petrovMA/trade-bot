import api from './api';
import { ActiveOrder } from '../types/order.types';

export interface OrdersResponse {
  longOrders: ActiveOrder[];
  shortOrders: ActiveOrder[];
  trend?: string;
  prices?: {
    maxPriceInOrderLong?: number;
    minPriceInOrderLong?: number;
    maxPriceInOrderShort?: number;
    minPriceInOrderShort?: number;
    currentPrice?: number;
  };
}

export const orderService = {
  // Get orders for specific bot
  // Note: Currently returns HTML, may need backend modification to return JSON
  getOrders: async (botName: string): Promise<string> => {
    const response = await api.get<string>(`/orders`, {
      params: { botName },
    });
    return response.data;
  },
};
