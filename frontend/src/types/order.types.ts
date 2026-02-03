export enum OrderSide {
  BUY = 'BUY',
  SELL = 'SELL'
}

export enum OrderStatus {
  NEW = 'NEW',
  FILLED = 'FILLED',
  PARTIALLY_FILLED = 'PARTIALLY_FILLED',
  CANCELED = 'CANCELED',
  REJECTED = 'REJECTED'
}

export interface Order {
  orderId: string;
  pair: string;
  price: number;
  origQty: number;
  executedQty: number;
  side: OrderSide;
  type: string;
  status: OrderStatus;
  stopPrice?: number;
  lastBorderPrice?: number;
  fee?: number;
}

export interface ActiveOrder {
  price: number;
  stopPrice?: number;
  lastBorderPrice?: number;
  amount: number;
  orderSide: OrderSide;
}
