import { API_BASE_URL } from "../../env-config";
import { authenticatedPost } from "./AuthInterceptor";

export interface CheckoutRequest {
  requestedPoints: number;
  couponCode: string | null;
  currency: string;
}

export interface CheckoutResponse {
  orderId: number;
  paymentId: number;
  amount: string;
  currency: string;
  paymentStatus: string;
  reservationExpiresAt: string;
}

export const submitCheckout = async (idempotencyKey: string, request: CheckoutRequest): Promise<any> => {
  const response = await authenticatedPost(
    `${API_BASE_URL}/api/orders/checkout`,
    request,
    { headers: { "Idempotency-Key": idempotencyKey } }
  );
  if (!response.ok) return response;
  return response.json();
};
