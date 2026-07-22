import Chip from '@mui/material/Chip';

const statusConfig: Record<string, { color: 'default' | 'warning' | 'success' | 'error'; variant?: 'filled' | 'outlined' }> = {
  DRAFT: { color: 'default' },
  ARCHIVED: { color: 'default' },
  PENDING_REVIEW: { color: 'warning' },
  UNDER_REVIEW: { color: 'warning' },
  AWAITING_PAYMENT: { color: 'warning' },
  PENDING: { color: 'warning' },
  CHANGES_REQUESTED: { color: 'warning', variant: 'outlined' },
  APPROVED: { color: 'success' },
  ACTIVE: { color: 'success' },
  ACCEPTED: { color: 'success' },
  PAID: { color: 'success' },
  SUCCEEDED: { color: 'success' },
  DELIVERED: { color: 'success' },
  COMPLETED: { color: 'success' },
  REJECTED: { color: 'error' },
  FAILED: { color: 'error' },
  CANCELLED: { color: 'error' },
  SUSPENDED: { color: 'error' },
  TERMINATED: { color: 'error' },
};

const statusLabels: Record<string, string> = {
  DRAFT: 'Bản nháp',
  ARCHIVED: 'Đã lưu trữ',
  PENDING_REVIEW: 'Chờ duyệt',
  UNDER_REVIEW: 'Đang duyệt',
  AWAITING_PAYMENT: 'Chờ thanh toán',
  PENDING: 'Đang chờ',
  CHANGES_REQUESTED: 'Cần bổ sung',
  APPROVED: 'Đã duyệt',
  ACTIVE: 'Đang hoạt động',
  ACCEPTED: 'Đã nhận',
  PAID: 'Đã thanh toán',
  SUCCEEDED: 'Thành công',
  DELIVERED: 'Đã giao',
  COMPLETED: 'Hoàn tất',
  REJECTED: 'Từ chối',
  FAILED: 'Thất bại',
  CANCELLED: 'Đã hủy',
  SUSPENDED: 'Tạm dừng',
  TERMINATED: 'Đã chấm dứt',
  OPEN: 'Đang mở',
  CALCULATED: 'Đã tính',
  OUT_OF_STOCK: 'Hết hàng',
  INACTIVE: 'Không hoạt động',
  EXPIRED: 'Hết hạn',
  NEW: 'Mới',
  PACKING: 'Đang đóng gói',
  READY_TO_SHIP: 'Sẵn sàng giao',
  SHIPPED: 'Đang giao',
  RETURN_REQUESTED: 'Yêu cầu trả hàng',
  RETURNED: 'Đã trả hàng',
  INVITED: 'Đã mời',
  REMOVED: 'Đã xóa',
};

function formatStatus(status: string): string {
  if (statusLabels[status]) {
    return statusLabels[status];
  }

  return status
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, c => c.toUpperCase());
}

interface StatusBadgeProps {
  status: string;
  size?: 'small' | 'medium';
}

const StatusBadge = ({ status, size = 'medium' }: StatusBadgeProps) => {
  const config = statusConfig[status] ?? { color: 'default' as const };

  return (
    <Chip
      label={formatStatus(status)}
      size={size}
      color={config.color}
      variant={config.variant ?? 'filled'}
    />
  );
};

export default StatusBadge;
