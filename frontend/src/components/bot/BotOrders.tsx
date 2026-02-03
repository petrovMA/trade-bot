import React from 'react';

interface BotOrdersProps {
  botName: string;
}

/**
 * Displays active orders for the bot
 * Current limitation: Backend returns HTML instead of JSON
 * Interim solution: Link to HTML orders page
 */
const BotOrders: React.FC<BotOrdersProps> = ({ botName }) => {
  const ordersUrl = `/orders?botName=${encodeURIComponent(botName)}`;

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-5">
      <h3 className="text-md font-semibold text-gray-800 mb-3">Active Orders</h3>

      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
        <p className="text-sm text-gray-700 mb-3">
          View detailed order information for this bot on the dedicated orders page.
        </p>
        <a
          href={ordersUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 btn btn-primary"
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
              d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"
            />
          </svg>
          View Orders Page
        </a>
      </div>

      <div className="mt-4 text-xs text-gray-500">
        <p className="font-medium mb-1">Note for developers:</p>
        <p>
          The orders endpoint currently returns HTML. To enable inline orders display,
          the backend needs to provide a JSON response format for the <code className="bg-gray-100 px-1 rounded">/orders</code> endpoint.
        </p>
      </div>
    </div>
  );
};

export default BotOrders;

/*
 * FUTURE ENHANCEMENT:
 * When backend provides JSON endpoint, replace with this implementation:
 *
 * import { useState, useEffect } from 'react';
 * import { orderService, OrdersResponse } from '@services/order.service';
 * import { ActiveOrder } from '@/types/order.types';
 *
 * const BotOrders: React.FC<BotOrdersProps> = ({ botName }) => {
 *   const [orders, setOrders] = useState<OrdersResponse | null>(null);
 *   const [loading, setLoading] = useState(true);
 *   const [error, setError] = useState<string | null>(null);
 *
 *   useEffect(() => {
 *     const fetchOrders = async () => {
 *       try {
 *         const data = await orderService.getOrders(botName);
 *         setOrders(data); // Assuming backend returns JSON
 *       } catch (err) {
 *         setError('Failed to load orders');
 *       } finally {
 *         setLoading(false);
 *       }
 *     };
 *
 *     fetchOrders();
 *     const interval = setInterval(fetchOrders, 10000); // Poll every 10s
 *     return () => clearInterval(interval);
 *   }, [botName]);
 *
 *   // Render orders table with longOrders and shortOrders
 *   // Similar to balance calculator orders table
 * };
 */
