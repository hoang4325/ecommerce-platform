import React from 'react';
import { useParams } from 'react-router-dom';
import Grid from '@mui/material/Grid';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Divider from '@mui/material/Divider';
import Skeleton from '@mui/material/Skeleton';
import { AppShell, PageHeader, StatusBadge, ErrorState, ConfirmDialog } from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import { approveSettlement, markSettlementPaid, addSettlementAdjustment, getSettlement } from '../../../api/AdminRequest';
import type { SettlementResponse } from '../../../../types/partner';

const formatCurrency = (value: number, currency = 'VND') =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: currency || 'VND',
    maximumFractionDigits: currency === 'VND' ? 0 : 2,
  }).format(Number(value || 0));

const formatDate = (value: string) =>
  new Intl.DateTimeFormat('vi-VN').format(new Date(value));

const formatDateTime = (value: string) =>
  new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));

const AdminSettlementDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const { showSnackbar } = useSnackbar();

  const [settlement, setSettlement] = React.useState<SettlementResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [actionLoading, setActionLoading] = React.useState(false);

  const [confirmDialog, setConfirmDialog] = React.useState<{ open: boolean; title: string; message: string; action: () => void; color?: 'error' | 'warning' | 'primary' }>({ open: false, title: '', message: '', action: () => {} });

  const [payDialogOpen, setPayDialogOpen] = React.useState(false);
  const [paymentReference, setPaymentReference] = React.useState('');

  const [adjustDialogOpen, setAdjustDialogOpen] = React.useState(false);
  const [adjustAmount, setAdjustAmount] = React.useState('');
  const [adjustReason, setAdjustReason] = React.useState('');

  const fetchSettlement = React.useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      setSettlement(await getSettlement(Number(id)));
    } catch (err: any) {
      setError(err?.message || 'Không tải được chi tiết thanh toán');
    } finally {
      setLoading(false);
    }
  }, [id]);

  React.useEffect(() => {
    fetchSettlement();
  }, [fetchSettlement]);

  const openConfirm = (title: string, message: string, action: () => Promise<void>, color?: 'error' | 'warning' | 'primary') => {
    setConfirmDialog({
      open: true,
      title,
      message,
      action: async () => {
        setActionLoading(true);
        try {
          await action();
          showSnackbar('Thao tác thành công', 'success');
          fetchSettlement();
        } catch (err: any) {
          showSnackbar(err?.message || 'Thao tác thất bại', 'error');
        } finally {
          setActionLoading(false);
          setConfirmDialog(prev => ({ ...prev, open: false }));
        }
      },
      color,
    });
  };

  const handleMarkPaid = async () => {
    if (!settlement || !paymentReference.trim()) return;
    setActionLoading(true);
    try {
      await markSettlementPaid(settlement.id, paymentReference.trim());
      showSnackbar('Đã ghi nhận thanh toán', 'success');
      setPayDialogOpen(false);
      setPaymentReference('');
      fetchSettlement();
    } catch (err: any) {
      showSnackbar(err?.message || 'Không ghi nhận được thanh toán', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleAddAdjustment = async () => {
    if (!settlement || !adjustAmount || !adjustReason.trim()) return;
    setActionLoading(true);
    try {
      await addSettlementAdjustment(settlement.id, {
        amount: Number(adjustAmount),
        reason: adjustReason.trim(),
        idempotencyKey: crypto.randomUUID(),
      });
      showSnackbar('Đã thêm điều chỉnh', 'success');
      setAdjustDialogOpen(false);
      setAdjustAmount('');
      setAdjustReason('');
      fetchSettlement();
    } catch (err: any) {
      showSnackbar(err?.message || 'Không thêm được điều chỉnh', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <AppShell>
        <PageHeader title="Đang tải..." breadcrumbs={[{ label: 'Thanh toán', href: '/admin/settlements' }, { label: '...' }]} />
        <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', p: 3 }}>
          <Skeleton variant="text" width="60%" height={40} />
          <Skeleton variant="text" width="40%" />
          <Skeleton variant="rectangular" height={200} sx={{ mt: 2 }} />
        </Card>
      </AppShell>
    );
  }

  if (error || !settlement) {
    return (
      <AppShell>
        <PageHeader title="Chi tiết thanh toán" breadcrumbs={[{ label: 'Thanh toán', href: '/admin/settlements' }, { label: 'Lỗi' }]} />
        <ErrorState message={error || 'Không tìm thấy kỳ thanh toán'} onRetry={fetchSettlement} />
      </AppShell>
    );
  }

  const status = settlement.status;

  return (
    <AppShell>
      <PageHeader
        title={`Thanh toán #${settlement.id}`}
        subtitle={`Đối tác #${settlement.partnerId} · ${settlement.currency}`}
        breadcrumbs={[{ label: 'Thanh toán', href: '/admin/settlements' }, { label: `#${settlement.id}` }]}
      />

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', mb: 3 }}>
            <CardHeader title="Chi tiết đối soát" />
            <CardContent>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography>Kỳ đối soát</Typography>
                  <Typography fontWeight={600}>{formatDate(settlement.periodStart)} - {formatDate(settlement.periodEnd)}</Typography>
                </Box>
                <Divider />
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography>Doanh thu</Typography>
                  <Typography>{formatCurrency(settlement.grossSales, settlement.currency)}</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography>Hoàn tiền</Typography>
                  <Typography color="error">- {formatCurrency(settlement.refundAmount, settlement.currency)}</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography>Hoa hồng</Typography>
                  <Typography color="error">- {formatCurrency(settlement.commissionAmount, settlement.currency)}</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography>Phí khác</Typography>
                  <Typography color="error">- {formatCurrency(settlement.otherFees, settlement.currency)}</Typography>
                </Box>
                {settlement.manualAdjustment !== 0 && (
                  <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                    <Typography>Điều chỉnh thủ công</Typography>
                    <Typography color={settlement.manualAdjustment > 0 ? 'success.main' : 'error'}>
                      {settlement.manualAdjustment > 0 ? '+' : ''}{formatCurrency(settlement.manualAdjustment, settlement.currency)}
                    </Typography>
                  </Box>
                )}
                <Divider />
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="h6">Số tiền cần trả</Typography>
                  <Typography variant="h6" color="primary">{formatCurrency(settlement.payableAmount, settlement.currency)}</Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>

          {settlement.paymentReference && (
            <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
              <CardHeader title="Thông tin chuyển khoản" />
              <CardContent>
                <Typography variant="body2" color="text.secondary">Mã tham chiếu</Typography>
                <Typography>{settlement.paymentReference}</Typography>
                {settlement.paidAt && (
                  <>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>Thời gian thanh toán</Typography>
                    <Typography>{formatDateTime(settlement.paidAt)}</Typography>
                  </>
                )}
              </CardContent>
            </Card>
          )}
        </Grid>

        <Grid item xs={12} md={4}>
          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', mb: 3 }}>
            <CardHeader title="Trạng thái" />
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <StatusBadge status={status} />
              </Box>
              <Typography variant="body2" color="text.secondary">Ngày tạo</Typography>
              <Typography sx={{ mb: 1 }}>{formatDateTime(settlement.createdAt)}</Typography>
              <Typography variant="body2" color="text.secondary">Cập nhật</Typography>
              <Typography sx={{ mb: 1 }}>{formatDateTime(settlement.updatedAt)}</Typography>
              {settlement.approvedAt && (
                <>
                  <Typography variant="body2" color="text.secondary">Đã duyệt</Typography>
                  <Typography sx={{ mb: 1 }}>{formatDateTime(settlement.approvedAt)}</Typography>
                </>
              )}
            </CardContent>
          </Card>

          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <CardHeader title="Thao tác" />
            <CardContent>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                {(status === 'OPEN' || status === 'CALCULATED') && (
                  <>
                    <Button variant="outlined" color="warning" disabled>
                      Đang rà soát
                    </Button>
                    <Button variant="contained" color="primary" onClick={() => openConfirm('Duyệt', `Duyệt kỳ thanh toán #${settlement.id}?`, () => approveSettlement(settlement.id))}>
                      Duyệt
                    </Button>
                    <Button variant="outlined" onClick={() => setAdjustDialogOpen(true)}>
                      Thêm điều chỉnh
                    </Button>
                  </>
                )}
                {status === 'UNDER_REVIEW' && (
                  <>
                    <Button variant="contained" color="primary" onClick={() => openConfirm('Duyệt', `Duyệt kỳ thanh toán #${settlement.id}?`, () => approveSettlement(settlement.id))}>
                      Duyệt
                    </Button>
                    <Button variant="outlined" disabled>
                      Yêu cầu chỉnh sửa
                    </Button>
                    <Button variant="outlined" onClick={() => setAdjustDialogOpen(true)}>
                      Thêm điều chỉnh
                    </Button>
                  </>
                )}
                {status === 'APPROVED' && (
                  <Button variant="contained" color="success" onClick={() => setPayDialogOpen(true)}>
                    Ghi nhận đã thanh toán
                  </Button>
                )}
                {status === 'PAID' && (
                  <Typography variant="body2" color="text.secondary">Kỳ thanh toán này đã hoàn tất.</Typography>
                )}
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <ConfirmDialog
        open={confirmDialog.open}
        title={confirmDialog.title}
        message={confirmDialog.message}
        confirmColor={confirmDialog.color}
        confirmLabel={confirmDialog.title}
        loading={actionLoading}
        onConfirm={confirmDialog.action}
        onCancel={() => setConfirmDialog(prev => ({ ...prev, open: false }))}
      />

      <Dialog open={payDialogOpen} onClose={() => !actionLoading && setPayDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Ghi nhận đã thanh toán</DialogTitle>
        <DialogContent>
          <TextField autoFocus fullWidth label="Mã tham chiếu thanh toán" value={paymentReference} onChange={(e) => setPaymentReference(e.target.value)} sx={{ mt: 1 }} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPayDialogOpen(false)} disabled={actionLoading}>Hủy</Button>
          <Button onClick={handleMarkPaid} variant="contained" color="success" disabled={actionLoading || !paymentReference.trim()}>
            {actionLoading ? 'Đang xử lý...' : 'Ghi nhận'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={adjustDialogOpen} onClose={() => !actionLoading && setAdjustDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Thêm điều chỉnh thủ công</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <TextField fullWidth type="number" label="Số tiền, có thể âm hoặc dương" value={adjustAmount} onChange={(e) => setAdjustAmount(e.target.value)} />
            <TextField fullWidth multiline rows={3} label="Lý do" value={adjustReason} onChange={(e) => setAdjustReason(e.target.value)} />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAdjustDialogOpen(false)} disabled={actionLoading}>Hủy</Button>
          <Button onClick={handleAddAdjustment} variant="contained" disabled={actionLoading || !adjustAmount || !adjustReason.trim()}>
            {actionLoading ? 'Đang xử lý...' : 'Thêm điều chỉnh'}
          </Button>
        </DialogActions>
      </Dialog>
    </AppShell>
  );
};

export default AdminSettlementDetailPage;
