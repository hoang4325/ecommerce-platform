import { API_BASE_URL } from "../../env-config";
import { authenticatedGet } from "./AuthInterceptor";
import { mapPage } from "./mapPage";
import { getMyPartnerId } from "./PartnerRequest";
import type { PaginatedDTO } from "../../types/PaginatedDTO";
import type { SettlementResponse, SettlementFilterParams } from "../../types/partner";

export const getPartnerSettlements = async (partnerId: number, params?: SettlementFilterParams): Promise<PaginatedDTO<SettlementResponse>> => {
  const query = new URLSearchParams();
  query.append('partnerId', String(partnerId || await getMyPartnerId()));
  if (params?.status) query.append('status', params.status);
  if (params?.page !== undefined) query.append('page', String(params.page));
  if (params?.size !== undefined) query.append('size', String(params.size));
  if (params?.sort) query.append('sort', params.sort);
  if (params?.startDate) query.append('startDate', params.startDate);
  if (params?.endDate) query.append('endDate', params.endDate);
  const response = await authenticatedGet(`${API_BASE_URL}/api/partner/settlements?${query.toString()}`);
  return mapPage(await response.json());
};

export const getPartnerSettlement = async (settlementId: number): Promise<SettlementResponse> => {
  const partnerId = await getMyPartnerId();
  const response = await authenticatedGet(`${API_BASE_URL}/api/partner/settlements/${settlementId}?partnerId=${partnerId}`);
  return response.json();
};
