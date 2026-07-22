import { API_BASE_URL } from "../../env-config";
import { authenticatedGet, authenticatedPost } from "./AuthInterceptor";
import { mapPage } from "./mapPage";
import { getMyPartnerId } from "./PartnerRequest";
import type { PaginatedDTO } from "../../types/PaginatedDTO";
import type { PartnerOrderResponse, OrderFilterParams } from "../../types/partner";

export const getPartnerOrders = async (partnerId: number, params?: OrderFilterParams): Promise<PaginatedDTO<PartnerOrderResponse>> => {
  const query = new URLSearchParams();
  query.append('partnerId', String(partnerId || await getMyPartnerId()));
  if (params?.status) query.append('status', params.status);
  if (params?.page !== undefined) query.append('page', String(params.page));
  if (params?.size !== undefined) query.append('size', String(params.size));
  if (params?.sort) query.append('sort', params.sort);
  const response = await authenticatedGet(`${API_BASE_URL}/api/partner/orders?${query.toString()}`);
  return mapPage(await response.json());
};

export const getPartnerOrder = async (partnerOrderId: number): Promise<PartnerOrderResponse> => {
  const partnerId = await getMyPartnerId();
  const response = await authenticatedGet(`${API_BASE_URL}/api/partner/orders/${partnerOrderId}?partnerId=${partnerId}`);
  return response.json();
};

export const acceptOrder = async (partnerOrderId: number, idempotencyKey: string): Promise<void> => {
  const partnerId = await getMyPartnerId();
  await authenticatedPost(`${API_BASE_URL}/api/partner/orders/${partnerOrderId}/accept?partnerId=${partnerId}`, undefined, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
};

export const rejectOrder = async (partnerOrderId: number, reason: string, idempotencyKey: string): Promise<void> => {
  const partnerId = await getMyPartnerId();
  const query = new URLSearchParams({ partnerId: String(partnerId), reason });
  await authenticatedPost(`${API_BASE_URL}/api/partner/orders/${partnerOrderId}/reject?${query.toString()}`, undefined, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
};

export const markPacking = async (partnerOrderId: number, idempotencyKey: string): Promise<void> => {
  const partnerId = await getMyPartnerId();
  await authenticatedPost(`${API_BASE_URL}/api/partner/orders/${partnerOrderId}/packing?partnerId=${partnerId}`, undefined, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
};

export const markReadyToShip = async (partnerOrderId: number, idempotencyKey: string): Promise<void> => {
  const partnerId = await getMyPartnerId();
  await authenticatedPost(`${API_BASE_URL}/api/partner/orders/${partnerOrderId}/ready-to-ship?partnerId=${partnerId}`, undefined, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
};

export const markShipped = async (partnerOrderId: number, idempotencyKey: string): Promise<void> => {
  const partnerId = await getMyPartnerId();
  await authenticatedPost(`${API_BASE_URL}/api/partner/orders/${partnerOrderId}/ship?partnerId=${partnerId}`, undefined, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
};

export const markDelivered = async (partnerOrderId: number, idempotencyKey: string): Promise<void> => {
  const partnerId = await getMyPartnerId();
  await authenticatedPost(`${API_BASE_URL}/api/partner/orders/${partnerOrderId}/deliver?partnerId=${partnerId}`, undefined, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
};

export const cancelOrder = async (partnerOrderId: number, reason: string, idempotencyKey: string): Promise<void> => {
  const partnerId = await getMyPartnerId();
  const query = new URLSearchParams({ partnerId: String(partnerId), reason });
  await authenticatedPost(`${API_BASE_URL}/api/partner/orders/${partnerOrderId}/cancel?${query.toString()}`, undefined, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
};

export const requestReturn = async (partnerOrderId: number, idempotencyKey: string, reason = 'Partner requested return'): Promise<void> => {
  const partnerId = await getMyPartnerId();
  const query = new URLSearchParams({ partnerId: String(partnerId), reason });
  await authenticatedPost(`${API_BASE_URL}/api/partner/orders/${partnerOrderId}/return-request?${query.toString()}`, undefined, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
};

export const approveReturn = async (partnerOrderId: number, idempotencyKey: string): Promise<void> => {
  const partnerId = await getMyPartnerId();
  await authenticatedPost(`${API_BASE_URL}/api/partner/orders/${partnerOrderId}/approve-return?partnerId=${partnerId}`, undefined, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
};
