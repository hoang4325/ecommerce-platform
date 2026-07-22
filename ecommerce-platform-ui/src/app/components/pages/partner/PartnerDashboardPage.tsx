import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Grid,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Button,
} from '@mui/material';
import SellIcon from '@mui/icons-material/Sell';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import InventoryIcon from '@mui/icons-material/Inventory';
import ReceiptIcon from '@mui/icons-material/Receipt';
import {
  AppShell,
  PageHeader,
  MetricCard,
  StatusBadge,
  SkeletonTable,
  EmptyState,
  ErrorState,
} from '../../shared';
import { getPartnerOffers } from '../../../api/PartnerOfferRequest';
import { getPartnerOrders } from '../../../api/PartnerOrderRequest';
import { getPartnerSettlements } from '../../../api/SettlementRequest';
import { getMyPartnerId } from '../../../api/PartnerRequest';
import type { PartnerOfferResponse, PartnerOrderResponse, SettlementResponse } from '../../../../types/partner';

type LoadState = 'loading' | 'loaded' | 'error';

const PartnerDashboardPage = () => {
  const navigate = useNavigate();

  const [loadState, setLoadState] = React.useState<LoadState>('loading');
  const [offers, setOffers] = React.useState<PartnerOfferResponse[]>([]);
  const [orders, setOrders] = React.useState<PartnerOrderResponse[]>([]);
  const [settlements, setSettlements] = React.useState<SettlementResponse[]>([]);

  const fetchData = React.useCallback(async () => {
    setLoadState('loading');
    try {
      const partnerId = await getMyPartnerId();
      const [offersRes, ordersRes, settlementsRes] = await Promise.all([
        getPartnerOffers(partnerId, { page: 0, size: 100 }),
        getPartnerOrders(partnerId, { page: 0, size: 5, sort: 'createdAt,desc' }),
        getPartnerSettlements(partnerId, { page: 0, size: 100 }),
      ]);
      setOffers(offersRes.data);
      setOrders(ordersRes.data);
      setSettlements(settlementsRes.data);
      setLoadState('loaded');
    } catch {
      setLoadState('error');
    }
  }, []);

  React.useEffect(() => { fetchData(); }, [fetchData]);

  const totalOffers = offers.length;
  const activeOrders = orders.filter(o => !['DELIVERED', 'CANCELLED', 'REJECTED'].includes(o.status)).length;
  const pendingSettlements = settlements.filter(s => s.status === 'OPEN' || s.status === 'CALCULATED' || s.status === 'UNDER_REVIEW').length;
  const totalRevenue = settlements
    .filter(s => s.status === 'PAID')
    .reduce((sum, s) => sum + s.payableAmount, 0);

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(amount);

  const formatDate = (dateStr: string) =>
    new Date(dateStr).toLocaleDateString('vi-VN', { year: 'numeric', month: 'short', day: 'numeric' });

  if (loadState === 'error') {
    return (
      <AppShell>
        <PageHeader title="Bảng điều khiển Đối tác" />
        <ErrorState message="Không thể tải dữ liệu bảng điều khiển" onRetry={fetchData} />
      </AppShell>
    );
  }

  return (
    <AppShell>
      <PageHeader
        title="Bảng điều khiển Đối tác"
        subtitle="Tổng quan tài khoản đối tác"
        actions={
          <Button variant="contained" startIcon={<SellIcon />} onClick={() => navigate('/partner/offers/new')}>
            Thêm sản phẩm
          </Button>
        }
      />

      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Tổng sản phẩm"
            value={totalOffers}
            icon={<InventoryIcon />}
            loading={loadState === 'loading'}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Đơn hàng đang hoạt động"
            value={activeOrders}
            icon={<ShoppingCartIcon />}
            loading={loadState === 'loading'}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Doanh thu (Đã thanh toán)"
            value={formatCurrency(totalRevenue)}
            icon={<AccountBalanceWalletIcon />}
            loading={loadState === 'loading'}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Thanh toán đang chờ"
            value={pendingSettlements}
            icon={<ReceiptIcon />}
            loading={loadState === 'loading'}
          />
        </Grid>
      </Grid>

      <Paper elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', p: 3, mb: 3 }}>
        <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Đơn hàng gần đây</Typography>
        {loadState === 'loading' ? (
          <SkeletonTable rows={5} columns={5} />
        ) : orders.length === 0 ? (
          <EmptyState
            title="Chưa có đơn hàng"
            description="Đơn hàng sẽ xuất hiện tại đây khi khách mua sản phẩm của shop"
          />
        ) : (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600 }}>Mã đơn</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Trạng thái</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Cần nhận</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Ngày tạo</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {orders.map(order => (
                  <TableRow key={order.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/partner/orders/${order.id}`)}>
                    <TableCell>#{order.orderId}</TableCell>
                    <TableCell><StatusBadge status={order.status} size="small" /></TableCell>
                    <TableCell>{formatCurrency(order.partnerPayableAmount)}</TableCell>
                    <TableCell>{formatDate(order.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      <Box sx={{ display: 'flex', gap: 2 }}>
        <Button variant="outlined" startIcon={<ReceiptIcon />} onClick={() => navigate('/partner/orders')}>
          Xem đơn hàng
        </Button>
        <Button variant="outlined" startIcon={<AccountBalanceWalletIcon />} onClick={() => navigate('/partner/settlements')}>
          Xem thanh toán
        </Button>
      </Box>
    </AppShell>
  );
};

export default PartnerDashboardPage;
