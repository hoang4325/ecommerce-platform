export interface PartnerApplicationRequest {
  name: string;
  businessName: string;
  taxCode: string;
  email: string;
  phone: string;
  address: string;
}

export interface PartnerProfileUpdateRequest {
  name?: string;
  businessName?: string;
  email?: string;
  phone?: string;
  address?: string;
}

export interface PartnerResponse {
  id: number;
  code: string;
  name: string;
  businessName: string;
  taxCode: string;
  email: string;
  phone: string;
  address: string;
  status: PartnerStatus;
  applicant: UserInfoBrief;
  approvedAt?: string;
  rejectedAt?: string;
  rejectionReason?: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export type PartnerStatus = 'DRAFT' | 'PENDING_REVIEW' | 'CHANGES_REQUESTED' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'TERMINATED';

export interface PartnerMemberRequest {
  userId: number;
  role: PartnerMemberRole;
}

export interface PartnerMemberResponse {
  id: number;
  partnerId: number;
  userId: number;
  username: string;
  email: string;
  role: PartnerMemberRole;
  status: PartnerMemberStatus;
  joinedAt: string;
}

export type PartnerMemberRole = 'OWNER' | 'MANAGER' | 'PRODUCT_STAFF' | 'ORDER_STAFF' | 'FINANCE_STAFF';

export type PartnerMemberStatus = 'INVITED' | 'ACTIVE' | 'SUSPENDED' | 'REMOVED';

export interface PartnerDocumentResponse {
  id: number;
  documentType: string;
  status: string;
  originalFileName: string;
  contentType: string;
  fileSize: number;
  rejectionReason?: string;
  uploadedAt: string;
}

export interface DocumentReviewRequest {
  status: 'APPROVED' | 'REJECTED';
  rejectionReason?: string;
}

export interface PartnerBankAccountRequest {
  bankName: string;
  accountName: string;
  accountNumber: string;
}

export interface PartnerBankAccountResponse {
  id: number;
  bankName: string;
  accountName: string;
  maskedAccountNumber: string;
  status: string;
}

export interface CreateOfferRequest {
  productId: number;
  partnerSku: string;
  price: number;
  currency: string;
  onHandQuantity: number;
}

export interface UpdateOfferRequest {
  productId?: number;
  partnerSku?: string;
  price?: number;
  currency?: string;
  onHandQuantity?: number;
}

export interface PartnerOfferResponse {
  id: number;
  partnerId: number;
  productId: number;
  productName: string;
  partnerSku: string;
  price: number;
  currency: string;
  onHandQuantity: number;
  reservedQuantity: number;
  status: PartnerOfferStatus;
  submittedAt?: string;
  approvedAt?: string;
  rejectionReason?: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export type PartnerOfferStatus = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'OUT_OF_STOCK' | 'ARCHIVED';

export interface InventoryAdjustmentRequest {
  delta: number;
  reason: string;
  idempotencyKey: string;
}

export interface OfferFilterParams {
  status?: PartnerOfferStatus;
  page?: number;
  size?: number;
  sort?: string;
}

export interface PartnerOrderResponse {
  id: number;
  orderId: number;
  partnerId: number;
  partnerName: string;
  status: PartnerOrderStatus;
  subtotal: number;
  discountAllocation: number;
  shippingAllocation: number;
  commissionAmount: number;
  partnerPayableAmount: number;
  currency: string;
  settlementId?: number;
  settlementStatus?: string;
  acceptedAt?: string;
  packedAt?: string;
  shippedAt?: string;
  deliveredAt?: string;
  cancelledAt?: string;
  createdAt: string;
  updatedAt: string;
  items?: PartnerOrderItemResponse[];
}

export interface PartnerOrderItemResponse {
  id: number;
  productId: number;
  offerId?: number;
  productName: string;
  partnerSku?: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  discountAllocation: number;
  commissionAmount: number;
  partnerPayableAmount: number;
  currency: string;
}

export type PartnerOrderStatus =
  | 'AWAITING_PAYMENT' | 'NEW' | 'ACCEPTED' | 'REJECTED'
  | 'PACKING' | 'READY_TO_SHIP' | 'SHIPPED' | 'DELIVERED'
  | 'RETURN_REQUESTED' | 'RETURNED' | 'CANCELLED';

export interface OrderFilterParams {
  status?: PartnerOrderStatus;
  page?: number;
  size?: number;
  sort?: string;
}

export interface SettlementResponse {
  id: number;
  partnerId: number;
  periodStart: string;
  periodEnd: string;
  currency: string;
  grossSales: number;
  refundAmount: number;
  commissionAmount: number;
  otherFees: number;
  manualAdjustment: number;
  payableAmount: number;
  status: SettlementStatus;
  approvedAt?: string;
  paidAt?: string;
  paymentReference?: string;
  createdAt: string;
  updatedAt: string;
}

export type SettlementStatus = 'OPEN' | 'CALCULATED' | 'UNDER_REVIEW' | 'APPROVED' | 'PAID' | 'FAILED' | 'CANCELLED';

export interface SettlementFilterParams {
  status?: SettlementStatus;
  page?: number;
  size?: number;
  sort?: string;
  startDate?: string;
  endDate?: string;
}

export interface SettlementAdjustmentRequest {
  amount: number;
  reason: string;
  idempotencyKey: string;
}

export interface CommissionRuleResponse {
  id: number;
  name: string;
  partnerId?: number;
  categoryId?: number;
  productId?: number;
  rate: number;
  fixedFee: number;
  currency: string;
  priority: number;
  validFrom: string;
  validTo?: string;
  status: CommissionRuleStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export type CommissionRuleStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'EXPIRED';

export interface CreateCommissionRuleRequest {
  name: string;
  partnerId?: number;
  categoryId?: number;
  productId?: number;
  rate: number;
  fixedFee?: number;
  currency: string;
  priority?: number;
  validFrom: string;
  validTo?: string;
}

export interface UpdateCommissionRuleRequest {
  name?: string;
  rate?: number;
  fixedFee?: number;
  currency?: string;
  priority?: number;
  validFrom?: string;
  validTo?: string;
}

export interface PartnerFilterParams {
  status?: PartnerStatus;
  page?: number;
  size?: number;
  sort?: string;
}

export interface UserInfoBrief {
  id: number;
  username: string;
  email: string;
}
