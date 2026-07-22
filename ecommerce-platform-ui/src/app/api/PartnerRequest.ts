import { API_BASE_URL } from "../../env-config";
import { authenticatedGet, authenticatedPost, authenticatedPut } from "./AuthInterceptor";
import type {
  PartnerApplicationRequest,
  PartnerProfileUpdateRequest,
  PartnerResponse,
  PartnerMemberRequest,
  PartnerMemberResponse,
  PartnerDocumentResponse,
  PartnerBankAccountRequest,
  PartnerBankAccountResponse,
} from "../../types/partner";

const handleResponse = async (response: Response) => {
  if (!response.ok) {
    const body = await response.text();
    let msg: string;
    try { msg = JSON.parse(body).error || body; } catch { msg = body || response.statusText; }
    throw new Error(msg);
  }
  const ct = response.headers.get('content-type');
  if (ct && ct.includes('application/json')) return response.json();
  return undefined;
};

export const getMyPartnerId = async (): Promise<number> => {
  const response = await authenticatedGet(`${API_BASE_URL}/api/partners/me/status`);
  const data: any = await handleResponse(response);
  return data.id;
};

export const applyAsPartner = async (data: PartnerApplicationRequest): Promise<PartnerResponse> => {
  const response = await authenticatedPost(`${API_BASE_URL}/api/partners/applications`, data);
  return handleResponse(response);
};

export const getMyApplication = async (): Promise<PartnerResponse> => {
  const response = await authenticatedGet(`${API_BASE_URL}/api/partners/me`);
  return handleResponse(response);
};

export const updatePartnerProfile = async (data: PartnerProfileUpdateRequest): Promise<PartnerResponse> => {
  const response = await authenticatedPut(`${API_BASE_URL}/api/partners/me`, data);
  return handleResponse(response);
};

export const getPartnerMembers = async (): Promise<PartnerMemberResponse[]> => {
  const response = await authenticatedGet(`${API_BASE_URL}/api/partners/${await getMyPartnerId()}/members`);
  return handleResponse(response);
};

export const inviteMember = async (data: PartnerMemberRequest): Promise<PartnerMemberResponse> => {
  const response = await authenticatedPost(`${API_BASE_URL}/api/partners/${await getMyPartnerId()}/members`, data);
  return handleResponse(response);
};

export const activateMember = async (memberId: number): Promise<void> => {
  const response = await authenticatedPost(`${API_BASE_URL}/api/partners/${await getMyPartnerId()}/members/${memberId}/activate`);
  await handleResponse(response);
};

export const suspendMember = async (memberId: number): Promise<void> => {
  const response = await authenticatedPost(`${API_BASE_URL}/api/partners/${await getMyPartnerId()}/members/${memberId}/suspend`);
  await handleResponse(response);
};

export const restoreMember = async (memberId: number): Promise<void> => {
  const response = await authenticatedPost(`${API_BASE_URL}/api/partners/${await getMyPartnerId()}/members/${memberId}/restore`);
  await handleResponse(response);
};

export const transferOwnership = async (memberId: number): Promise<void> => {
  const response = await authenticatedPost(`${API_BASE_URL}/api/partners/${await getMyPartnerId()}/members/${memberId}/transfer-ownership`);
  await handleResponse(response);
};

export const getPartnerDocuments = async (): Promise<PartnerDocumentResponse[]> => {
  const response = await authenticatedGet(`${API_BASE_URL}/api/partners/${await getMyPartnerId()}/documents`);
  return handleResponse(response);
};

export const uploadDocument = async (formData: FormData): Promise<PartnerDocumentResponse> => {
  const response = await authenticatedPost(`${API_BASE_URL}/api/partners/${await getMyPartnerId()}/documents`, formData);
  return handleResponse(response);
};

export const getBankAccounts = async (): Promise<PartnerBankAccountResponse[]> => {
  const response = await authenticatedGet(`${API_BASE_URL}/api/partners/${await getMyPartnerId()}/bank-accounts`);
  return handleResponse(response);
};

export const addBankAccount = async (data: PartnerBankAccountRequest): Promise<PartnerBankAccountResponse> => {
  const response = await authenticatedPost(`${API_BASE_URL}/api/partners/${await getMyPartnerId()}/bank-accounts`, data);
  return handleResponse(response);
};
