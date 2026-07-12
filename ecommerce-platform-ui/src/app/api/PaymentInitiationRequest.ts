import { API_BASE_URL } from "../../env-config";
import { authenticatedPost } from "./AuthInterceptor";

export interface PaymentInitiationRequest {
  paymentMethodId: string;
}

export interface PaymentInitiationResponse {
  orderId: number;
  paymentId: number;
  amount: string;
  currency: string;
  paymentStatus: string;
}

export const initiatePayment = async (paymentId: number, idempotencyKey: string, request: PaymentInitiationRequest): Promise<any> => {
  const response = await authenticatedPost(
    `${API_BASE_URL}/api/payments/${paymentId}/initiate`,
    request,
    { headers: { "Idempotency-Key": idempotencyKey } }
  );
  if (!response.ok) return response;
  return response.json();
};
