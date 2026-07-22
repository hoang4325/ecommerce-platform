import React from 'react';
import { useParams } from 'react-router-dom';
import {
  Box,
  Paper,
  Grid,
  Typography,
  Button,
  CircularProgress,
  Stack,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import InventoryIcon from '@mui/icons-material/Inventory';
import {
  AppShell,
  PageHeader,
  StatusBadge,
  SkeletonTable,
  ErrorState,
} from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import {
  getPartnerOrder,
  acceptOrder,
  rejectOrder,
  markPacking,
  markReadyToShip,
  markShipped,
  markDelivered,
  requestReturn,
  approveReturn,
} from '../../../api/PartnerOrderRequest';
import type { PartnerOrderResponse } from '../../../../types/partner';

type ActionType =
  | 'accept' | 'reject'
  | 'packing' | 'readyToShip'
  | 'shipped' | 'delivered'
  | 'requestReturn' | 'approveReturn';

const ACTION_MAP: Record<string, ActionType[]> = {
  NEW: ['accept', 'reject'],
  ACCEPTED: ['packing'],
  PACKING: ['readyToShip'],
  READY_TO_SHIP: ['shipped'],
  SHIPPED: ['delivered'],
  DELIVERED: ['requestReturn'],
  RETURN_REQUESTED: ['approveReturn'],
};

const ACTION_LABELS: Record<ActionType, string> = {
  accept: 'Nhận đơn',
  reject: 'Từ chối',
  packing: 'Đang đóng gói',
  readyToShip: 'Sẵn sàng giao',
  shipped: 'Đã giao vận',
  delivered: 'Đã giao hàng',
  requestReturn: 'Yêu cầu trả hàng',
  approveReturn: 'Duyệt trả hàng',
};

const ACTION_ICONS: Record<ActionType, React.ReactNode> = {
  accept: <CheckCircleIcon />,
  reject: <CancelIcon />,
  packing: <InventoryIcon />,
  readyToShip: <LocalShippingIcon />,
  shipped: <LocalShippingIcon />,
  delivered: <CheckCircleIcon />,
  requestReturn: <CancelIcon />,
  approveReturn: <CheckCircleIcon />,
};

const ACTION_COLORS: Record<ActionType, 'success' | 'error' | 'primary' | 'warning'> = {
  accept: 'success',
  reject: 'error',
  packing: 'primary',
  readyToShip: 'warning',
  shipped: 'primary',
  delivered: 'success',
  requestReturn: 'warning',
  approveReturn: 'success',
};

const PartnerOrderDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const { showSnackbar } = useSnackbar();

  const [order, setOrder] = React.useState<PartnerOrderResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState('');
  const [actionLoading, setActionLoading] = React.useState<ActionType | null>(null);
  const [rejectDialogOpen, setRejectDialogOpen] = React.useState(false);
  const [rejectReason, setRejectReason] = React.useState('');
  const [rejectSubmitting, setRejectSubmitting] = React.useState(false);

  const fetchOrder = React.useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError('');
    try {
      const res = await getPartnerOrder(Number(id));
      setOrder(res);
    } catch {
      setError('Không tải được chi tiết đơn hàng');
    } finally {
      setLoading(false);
    }
  }, [id]);

  React.useEffect(() => { fetchOrder(); }, [fetchOrder]);

  const performAction = async (action: ActionType) => {
    if (!order) return;
    const idempotencyKey = Date.now().toString() + Math.random().toString(36);
    setActionLoading(action);
    try {
      switch (action) {
        case 'accept': await acceptOrder(order.id, idempotencyKey); break;
        case 'reject': return;
        case 'packing': await markPacking(order.id, idempotencyKey); break;
        case 'readyToShip': await markReadyToShip(order.id, idempotencyKey); break;
        case 'shipped': await markShipped(order.id, idempotencyKey); break;
        case 'delivered': await markDelivered(order.id, idempotencyKey); break;
        case 'requestReturn': await requestReturn(order.id, idempotencyKey); break;
        case 'approveReturn': await approveReturn(order.id, idempotencyKey); break;
      }
      showSnackbar('Cập nhật đơn hàng thành công', 'success');
      fetchOrder();
    } catch {
      showSnackbar('Không cập nhật được đơn hàng', 'error');
    } finally {
      setActionLoading(null);
    }
  };

  const handleReject = async () => {
    if (!order || !rejectReason.trim()) return;
    const idempotencyKey = Date.now().toString() + Math.random().toString(36);
    setRejectSubmitting(true);
    try {
      await rejectOrder(order.id, rejectReason.trim(), idempotencyKey);
      showSnackbar('Đã từ chối đơn hàng', 'success');
      setRejectDialogOpen(false);
      setRejectReason('');
      fetchOrder();
    } catch {
      showSnackbar('Không từ chối được đơn hàng', 'error');
    } finally {
      setRejectSubmitting(false);
    }
  };

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(amount);

  const formatDate = (dateStr?: string) =>
    dateStr ? new Date(dateStr).toLocaleString('vi-VN', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-';

  const timeline = order ? [
    { label: 'Đã tạo', date: order.createdAt },
    { label: 'Đã nhận', date: order.acceptedAt },
    { label: 'Đóng gói', date: order.packedAt },
    { label: 'Giao vận', date: order.shippedAt },
    { label: 'Đã giao', date: order.deliveredAt },
  ] : [];

  const availableActions = order ? ACTION_MAP[order.status] || [] : [];

  if (loading) {
    return (
      <AppShell>
        <PageHeader title="Chi tiết đơn hàng" />
        <SkeletonTable rows={6} columns={2} />
      </AppShell>
    );
  }

  if (error || !order) {
    return (
      <AppShell>
        <PageHeader title="Chi tiết đơn hàng" />
        <ErrorState message={error || 'Không tìm thấy đơn hàng'} onRetry={fetchOrder} />
      </AppShell>
    );
  }

  return (
    <AppShell>
      <PageHeader
        title={`Đơn hàng shop #${order.id}`}
        subtitle={`Đơn hàng #${order.orderId}`}
        breadcrumbs={[
          { label: 'Đối tác', href: '/partner/dashboard' },
          { label: 'Đơn hàng', href: '/partner/orders' },
          { label: `#${order.id}` },
        ]}
      />

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Paper elevation={0} sx={{ p: 3, borderRadius: 2, border: 1, borderColor: 'divider', mb: 3 }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Sản phẩm trong đơn</Typography>
            {order.items && order.items.length > 0 ? (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 600 }}>Sản phẩm</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>SKU</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>SL</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>Đơn giá</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>Thành tiền</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {order.items.map(item => (
                      <TableRow key={item.id} hover>
                        <TableCell sx={{ maxWidth: 360 }}>
                          <Typography
                            variant="body2"
                            sx={{
                              fontWeight: 600,
                              display: '-webkit-box',
                              WebkitLineClamp: 2,
                              WebkitBoxOrient: 'vertical',
                              overflow: 'hidden',
                            }}
                          >
                            {item.productName}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Product #{item.productId}{item.offerId ? ` - Offer #${item.offerId}` : ''}
                          </Typography>
                        </TableCell>
                        <TableCell>{item.partnerSku || '-'}</TableCell>
                        <TableCell align="right">{item.quantity}</TableCell>
                        <TableCell align="right">{formatCurrency(item.unitPrice)}</TableCell>
                        <TableCell align="right">
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>
                            {formatCurrency(item.lineTotal)}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            ) : (
              <Typography color="text.secondary">
                Đơn hàng này chưa có dòng sản phẩm. Các đơn mới sẽ hiển thị sản phẩm sau khi bảng order_items được khôi phục.
              </Typography>
            )}
          </Paper>

          <Paper elevation={0} sx={{ p: 3, borderRadius: 2, border: 1, borderColor: 'divider', mb: 3 }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Chi tiết tiền hàng</Typography>
            <Grid container spacing={2}>
              <Grid item xs={6}><Typography variant="body2" color="text.secondary">Tiền hàng</Typography><Typography>{formatCurrency(order.subtotal)}</Typography></Grid>
              <Grid item xs={6}><Typography variant="body2" color="text.secondary">Phân bổ giảm giá</Typography><Typography>{formatCurrency(order.discountAllocation)}</Typography></Grid>
              <Grid item xs={6}><Typography variant="body2" color="text.secondary">Phân bổ vận chuyển</Typography><Typography>{formatCurrency(order.shippingAllocation)}</Typography></Grid>
              <Grid item xs={6}><Typography variant="body2" color="text.secondary">Hoa hồng</Typography><Typography color="error.main">{formatCurrency(order.commissionAmount)}</Typography></Grid>
              <Grid item xs={6}><Typography variant="body2" color="text.secondary"><strong>Shop nhận</strong></Typography><Typography variant="h6" color="success.main">{formatCurrency(order.partnerPayableAmount)}</Typography></Grid>
            </Grid>
          </Paper>

          <Paper elevation={0} sx={{ p: 3, borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Tiến trình</Typography>
            {timeline.map(item => (
              <Box key={item.label} sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 0.5 }}>
                <Box
                  sx={{
                    width: 10,
                    height: 10,
                    borderRadius: '50%',
                    bgcolor: item.date !== '-' ? 'primary.main' : 'grey.300',
                    flexShrink: 0,
                  }}
                />
                <Typography variant="body2" sx={{ minWidth: 100, fontWeight: item.date !== '-' ? 600 : 400 }}>
                  {item.label}
                </Typography>
                <Typography variant="body2" color="text.secondary">{item.date !== '-' ? formatDate(item.date) : 'Đang chờ'}</Typography>
              </Box>
            ))}
          </Paper>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper elevation={0} sx={{ p: 3, borderRadius: 2, border: 1, borderColor: 'divider', mb: 3 }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Thông tin đơn</Typography>
            <Box sx={{ mb: 2 }}>
              <Typography variant="body2" color="text.secondary">Trạng thái</Typography>
              <StatusBadge status={order.status} size="medium" />
            </Box>
            <Box sx={{ mb: 1 }}>
              <Typography variant="body2" color="text.secondary">Tiền tệ</Typography>
              <Typography>{order.currency}</Typography>
            </Box>
            {order.settlementId && (
              <Box sx={{ mb: 1 }}>
                <Typography variant="body2" color="text.secondary">Kỳ thanh toán</Typography>
                <Typography>#{order.settlementId}</Typography>
              </Box>
            )}
          </Paper>

          {availableActions.length > 0 && (
            <Paper elevation={0} sx={{ p: 3, borderRadius: 2, border: 1, borderColor: 'divider' }}>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Thao tác</Typography>
              <Stack spacing={1.5}>
                {availableActions.map(action => (
                  <Button
                    key={action}
                    fullWidth
                    variant="contained"
                    color={ACTION_COLORS[action]}
                    startIcon={actionLoading === action ? <CircularProgress size={18} /> : ACTION_ICONS[action]}
                    disabled={actionLoading !== null}
                    onClick={() => action === 'reject' ? setRejectDialogOpen(true) : performAction(action)}
                  >
                    {ACTION_LABELS[action]}
                  </Button>
                ))}
              </Stack>
            </Paper>
          )}
        </Grid>
      </Grid>

      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Từ chối đơn hàng</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            multiline
            rows={3}
            label="Lý do từ chối"
            value={rejectReason}
            onChange={e => setRejectReason(e.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectDialogOpen(false)}>Hủy</Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleReject}
            disabled={rejectSubmitting || !rejectReason.trim()}
            startIcon={rejectSubmitting ? <CircularProgress size={20} /> : null}
          >
            Từ chối
          </Button>
        </DialogActions>
      </Dialog>
    </AppShell>
  );
};

export default PartnerOrderDetailPage;
