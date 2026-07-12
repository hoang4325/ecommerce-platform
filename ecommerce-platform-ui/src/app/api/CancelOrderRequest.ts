import { API_BASE_URL } from "../../env-config";
import { authenticatedPost } from "./AuthInterceptor";

export interface CancelOrderRequest {
  reason: string;
}

export const cancelOrder = async (orderId: number, idempotencyKey: string, request: CancelOrderRequest): Promise<any> => {
  const response = await authenticatedPost(
    `${API_BASE_URL}/api/orders/${orderId}/cancel`,
    request,
    { headers: { "Idempotency-Key": idempotencyKey } }
  );
  if (!response.ok) return response;
  return response.json();
};
