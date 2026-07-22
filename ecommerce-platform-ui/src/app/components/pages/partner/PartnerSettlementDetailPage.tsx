import React from 'react';
import { useParams } from 'react-router-dom';
import {
  Box,
  Paper,
  Grid,
  Typography,
} from '@mui/material';
import {
  AppShell,
  PageHeader,
  StatusBadge,
  SkeletonTable,
  ErrorState,
} from '../../shared';
import { getPartnerSettlement } from '../../../api/SettlementRequest';
import type { SettlementResponse } from '../../../../types/partner';

const PartnerSettlementDetailPage = () => {
  const { id } = useParams<{ id: string }>();

  const [settlement, setSettlement] = React.useState<SettlementResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState('');

  const fetchSettlement = React.useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError('');
    try {
      const res = await getPartnerSettlement(Number(id));
      setSettlement(res);
    } catch {
      setError('Không tải được chi tiết thanh toán');
    } finally {
      setLoading(false);
    }
  }, [id]);

  React.useEffect(() => { fetchSettlement(); }, [fetchSettlement]);

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency: settlement?.currency || 'VND', maximumFractionDigits: settlement?.currency === 'VND' ? 0 : 2 }).format(amount);

  const formatDate = (dateStr?: string) =>
    dateStr ? new Date(dateStr).toLocaleString('vi-VN', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-';

  const timeline = settlement ? [
    { label: 'Đã tạo', date: settlement.createdAt },
    { label: 'Đã duyệt', date: settlement.approvedAt },
    { label: 'Đã thanh toán', date: settlement.paidAt },
  ] : [];

  if (loading) {
    return (
      <AppShell>
        <PageHeader title="Chi tiết thanh toán" />
        <SkeletonTable rows={4} columns={2} />
      </AppShell>
    );
  }

  if (error || !settlement) {
    return (
      <AppShell>
        <PageHeader title="Chi tiết thanh toán" />
        <ErrorState message={error || 'Không tìm thấy kỳ thanh toán'} onRetry={fetchSettlement} />
      </AppShell>
    );
  }

  return (
    <AppShell>
      <PageHeader
        title={`Thanh toán #${settlement.id}`}
        subtitle={`${formatDate(settlement.periodStart)} - ${formatDate(settlement.periodEnd)}`}
        breadcrumbs={[
          { label: 'Đối tác', href: '/partner/dashboard' },
          { label: 'Thanh toán', href: '/partner/settlements' },
          { label: `#${settlement.id}` },
        ]}
      />

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Paper elevation={0} sx={{ p: 3, borderRadius: 2, border: 1, borderColor: 'divider', mb: 3 }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Chi tiết đối soát</Typography>
            <Grid container spacing={2}>
              <Grid item xs={6}><Typography variant="body2" color="text.secondary">Doanh thu</Typography><Typography>{formatCurrency(settlement.grossSales)}</Typography></Grid>
              <Grid item xs={6}><Typography variant="body2" color="text.secondary">Hoàn tiền</Typography><Typography color="error.main">{formatCurrency(settlement.refundAmount)}</Typography></Grid>
              <Grid item xs={6}><Typography variant="body2" color="text.secondary">Hoa hồng</Typography><Typography color="error.main">{formatCurrency(settlement.commissionAmount)}</Typography></Grid>
              <Grid item xs={6}><Typography variant="body2" color="text.secondary">Phí khác</Typography><Typography color="error.main">{formatCurrency(settlement.otherFees)}</Typography></Grid>
              <Grid item xs={6}><Typography variant="body2" color="text.secondary">Điều chỉnh thủ công</Typography><Typography>{formatCurrency(settlement.manualAdjustment)}</Typography></Grid>
              <Grid item xs={6}><Typography variant="body2" color="text.secondary"><strong>Shop nhận</strong></Typography><Typography variant="h6" color="success.main">{formatCurrency(settlement.payableAmount)}</Typography></Grid>
            </Grid>
          </Paper>

          <Paper elevation={0} sx={{ p: 3, borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Tiến trình thanh toán</Typography>
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
          <Paper elevation={0} sx={{ p: 3, borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Thông tin</Typography>
            <Box sx={{ mb: 2 }}>
              <Typography variant="body2" color="text.secondary">Trạng thái</Typography>
              <StatusBadge status={settlement.status} size="medium" />
            </Box>
            <Box sx={{ mb: 1 }}>
              <Typography variant="body2" color="text.secondary">Tiền tệ</Typography>
              <Typography>{settlement.currency}</Typography>
            </Box>
            {settlement.paymentReference && (
              <Box sx={{ mb: 1 }}>
                <Typography variant="body2" color="text.secondary">Mã tham chiếu thanh toán</Typography>
                <Typography>{settlement.paymentReference}</Typography>
              </Box>
            )}
          </Paper>
        </Grid>
      </Grid>
    </AppShell>
  );
};

export default PartnerSettlementDetailPage;
