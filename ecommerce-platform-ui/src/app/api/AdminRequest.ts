import { API_BASE_URL } from "../../env-config";
import { authenticatedGet, authenticatedPost, authenticatedPut } from "./AuthInterceptor";
import { mapPage } from "./mapPage";
import type { PaginatedDTO } from "../../types/PaginatedDTO";
import type {
  PartnerResponse,
  PartnerFilterParams,
  PartnerDocumentResponse,
  DocumentReviewRequest,
  PartnerOfferResponse,
  OfferFilterParams,
  SettlementResponse,
  SettlementFilterParams,
  SettlementAdjustmentRequest,
  CommissionRuleResponse,
  CreateCommissionRuleRequest,
  UpdateCommissionRuleRequest,
} from "../../types/partner";

export const getPartners = async (params?: PartnerFilterParams): Promise<PaginatedDTO<PartnerResponse>> => {
  const query = new URLSearchParams();
  if (params?.page !== undefined) query.append('page', String(params.page));
  if (params?.size !== undefined) query.append('size', String(params.size));
  if (params?.sort) query.append('sort', params.sort);
  const qs = query.toString();
  const path = params?.status ? `/api/admin/partners/status/${params.status}` : '/api/admin/partners';
  const response = await authenticatedGet(`${API_BASE_URL}${path}${qs ? `?${qs}` : ''}`);
  return mapPage(await response.json());
};

export const getPartner = async (partnerId: number): Promise<PartnerResponse> => {
  const response = await authenticatedGet(`${API_BASE_URL}/api/admin/partners/${partnerId}`);
  return response.json();
};

export const approvePartner = async (partnerId: number, reason?: string): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/partners/${partnerId}/approve`, reason ? { reason } : undefined);
};

export const rejectPartner = async (partnerId: number, reason: string): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/partners/${partnerId}/reject`, { reason });
};

export const suspendPartner = async (partnerId: number, reason: string): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/partners/${partnerId}/suspend`, { reason });
};

export const restorePartner = async (partnerId: number): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/partners/${partnerId}/restore`);
};

export const terminatePartner = async (partnerId: number, reason: string): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/partners/${partnerId}/terminate`, { reason });
};

export const requestPartnerChanges = async (partnerId: number, reason: string): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/partners/${partnerId}/request-changes`, { reason });
};

export const getPartnerDocumentsForReview = async (partnerId: number): Promise<PartnerDocumentResponse[]> => {
  const response = await authenticatedGet(`${API_BASE_URL}/api/admin/partners/${partnerId}/documents`);
  return response.json();
};

export const reviewDocument = async (documentId: number, data: DocumentReviewRequest): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/documents/${documentId}/review`, data);
};

export const getOffers = async (params?: OfferFilterParams): Promise<PaginatedDTO<PartnerOfferResponse>> => {
  const query = new URLSearchParams();
  if (params?.page !== undefined) query.append('page', String(params.page));
  if (params?.size !== undefined) query.append('size', String(params.size));
  if (params?.sort) query.append('sort', params.sort);
  const qs = query.toString();
  const path = params?.status ? `/api/admin/offers/status/${params.status}` : '/api/admin/offers';
  const response = await authenticatedGet(`${API_BASE_URL}${path}${qs ? `?${qs}` : ''}`);
  return mapPage(await response.json());
};

export const approveOffer = async (offerId: number): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/offers/${offerId}/approve`);
};

export const rejectOffer = async (offerId: number, reason: string): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/offers/${offerId}/reject`, { reason });
};

export const suspendOffer = async (offerId: number, reason: string): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/offers/${offerId}/suspend`, { reason });
};

export const getSettlements = async (params?: SettlementFilterParams): Promise<PaginatedDTO<SettlementResponse>> => {
  const query = new URLSearchParams();
  if (params?.status) query.append('status', params.status);
  if (params?.page !== undefined) query.append('page', String(params.page));
  if (params?.size !== undefined) query.append('size', String(params.size));
  if (params?.sort) query.append('sort', params.sort);
  if (params?.startDate) query.append('startDate', params.startDate);
  if (params?.endDate) query.append('endDate', params.endDate);
  const qs = query.toString();
  const response = await authenticatedGet(`${API_BASE_URL}/api/admin/settlements${qs ? `?${qs}` : ''}`);
  return mapPage(await response.json());
};

export const getSettlement = async (settlementId: number): Promise<SettlementResponse> => {
  const response = await authenticatedGet(`${API_BASE_URL}/api/admin/settlements/${settlementId}`);
  return response.json();
};

export const calculateSettlement = async (partnerId: number, periodStart: string, periodEnd: string, currency: string): Promise<void> => {
  const query = new URLSearchParams({
    partnerId: String(partnerId),
    periodStart,
    periodEnd,
    currency,
  });
  await authenticatedPost(`${API_BASE_URL}/api/admin/settlements/calculate?${query.toString()}`);
};

export const approveSettlement = async (settlementId: number): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/settlements/${settlementId}/approve`);
};

export const markSettlementPaid = async (settlementId: number, paymentReference: string): Promise<void> => {
  const query = new URLSearchParams({ paymentReference });
  await authenticatedPost(`${API_BASE_URL}/api/admin/settlements/${settlementId}/mark-paid?${query.toString()}`);
};

export const addSettlementAdjustment = async (settlementId: number, data: SettlementAdjustmentRequest): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/settlements/${settlementId}/adjustments`, data);
};

export const getCommissionRules = async (params?: any): Promise<PaginatedDTO<CommissionRuleResponse>> => {
  const query = new URLSearchParams();
  if (params?.page !== undefined) query.append('page', String(params.page));
  if (params?.size !== undefined) query.append('size', String(params.size));
  if (params?.sort) query.append('sort', params.sort);
  const qs = query.toString();
  const response = await authenticatedGet(`${API_BASE_URL}/api/admin/commission-rules${qs ? `?${qs}` : ''}`);
  return mapPage(await response.json());
};

export const getCommissionRule = async (ruleId: number): Promise<CommissionRuleResponse> => {
  const response = await authenticatedGet(`${API_BASE_URL}/api/admin/commission-rules/${ruleId}`);
  return response.json();
};

export const createCommissionRule = async (data: CreateCommissionRuleRequest): Promise<CommissionRuleResponse> => {
  const response = await authenticatedPost(`${API_BASE_URL}/api/admin/commission-rules`, data);
  return response.json();
};

export const updateCommissionRule = async (ruleId: number, data: UpdateCommissionRuleRequest): Promise<CommissionRuleResponse> => {
  const response = await authenticatedPut(`${API_BASE_URL}/api/admin/commission-rules/${ruleId}`, data);
  return response.json();
};

export const activateCommissionRule = async (ruleId: number): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/commission-rules/${ruleId}/activate`);
};

export const deactivateCommissionRule = async (ruleId: number): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/commission-rules/${ruleId}/deactivate`);
};

export const expireCommissionRule = async (ruleId: number): Promise<void> => {
  await authenticatedPost(`${API_BASE_URL}/api/admin/commission-rules/${ruleId}/expire`);
};
