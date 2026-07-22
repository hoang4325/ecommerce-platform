import { API_BASE_URL } from "../../env-config";
import { authenticatedGet, authenticatedPost, authenticatedPut } from "./AuthInterceptor";
import { mapPage } from "./mapPage";
import { getMyPartnerId } from "./PartnerRequest";
import type { PaginatedDTO } from "../../types/PaginatedDTO";
import type {
  CreateOfferRequest,
  UpdateOfferRequest,
  PartnerOfferResponse,
  OfferFilterParams,
  InventoryAdjustmentRequest,
} from "../../types/partner";

export const getPartnerOffers = async (partnerId: number, params?: OfferFilterParams): Promise<PaginatedDTO<PartnerOfferResponse>> => {
  const query = new URLSearchParams();
  query.append('partnerId', String(partnerId || await getMyPartnerId()));
  if (params?.status) query.append('status', params.status);
  if (params?.page !== undefined) query.append('page', String(params.page));
  if (params?.size !== undefined) query.append('size', String(params.size));
  if (params?.sort) query.append('sort', params.sort);
  const response = await authenticatedGet(`${API_BASE_URL}/api/partner/offers?${query.toString()}`);
  return mapPage(await response.json());
};

export const getPartnerOffer = async (offerId: number): Promise<PartnerOfferResponse> => {
  const partnerId = await getMyPartnerId();
  const response = await authenticatedGet(`${API_BASE_URL}/api/partner/offers/${offerId}?partnerId=${partnerId}`);
  return response.json();
};

export const createOffer = async (data: CreateOfferRequest): Promise<PartnerOfferResponse> => {
  const partnerId = await getMyPartnerId();
  const response = await authenticatedPost(`${API_BASE_URL}/api/partner/offers?partnerId=${partnerId}`, data);
  return response.json();
};

export const updateOffer = async (offerId: number, data: UpdateOfferRequest): Promise<PartnerOfferResponse> => {
  const partnerId = await getMyPartnerId();
  const response = await authenticatedPut(`${API_BASE_URL}/api/partner/offers/${offerId}?partnerId=${partnerId}`, data);
  return response.json();
};

export const submitOffer = async (offerId: number): Promise<PartnerOfferResponse> => {
  const partnerId = await getMyPartnerId();
  const response = await authenticatedPost(`${API_BASE_URL}/api/partner/offers/${offerId}/submit?partnerId=${partnerId}`);
  return response.json();
};

export const archiveOffer = async (offerId: number): Promise<void> => {
  const partnerId = await getMyPartnerId();
  await authenticatedPost(`${API_BASE_URL}/api/partner/offers/${offerId}/archive?partnerId=${partnerId}`);
};

export const adjustInventory = async (offerId: number, data: InventoryAdjustmentRequest): Promise<PartnerOfferResponse> => {
  const partnerId = await getMyPartnerId();
  const query = new URLSearchParams({
    partnerId: String(partnerId),
    delta: String(data.delta),
    reason: data.reason,
  });
  const response = await authenticatedPost(`${API_BASE_URL}/api/partner/offers/${offerId}/inventory-adjustments?${query.toString()}`);
  return response.json();
};
