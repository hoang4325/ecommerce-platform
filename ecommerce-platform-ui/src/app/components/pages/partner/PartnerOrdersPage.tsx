import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Pagination,
} from '@mui/material';
import VisibilityIcon from '@mui/icons-material/Visibility';
import {
  AppShell,
  PageHeader,
  StatusBadge,
  SkeletonTable,
  EmptyState,
  ErrorState,
} from '../../shared';
import { getPartnerOrders } from '../../../api/PartnerOrderRequest';
import type { PartnerOrderResponse } from '../../../../types/partner';

const PartnerOrdersPage = () => {
  const navigate = useNavigate();

  const [orders, setOrders] = React.useState<PartnerOrderResponse[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState('');
  const [statusFilter, setStatusFilter] = React.useState<string>('');
  const [page, setPage] = React.useState(0);
  const [totalPages, setTotalPages] = React.useState(0);

  const fetchOrders = React.useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const params: any = { page, size: 10 };
      if (statusFilter) params.status = statusFilter;
      const res = await getPartnerOrders(0, params);
      setOrders(res.data);
      setTotalPages(res.totalPages);
    } catch {
      setError('Không tải được danh sách đơn hàng');
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter]);

  React.useEffect(() => { fetchOrders(); }, [fetchOrders]);

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(amount);

  const formatDate = (dateStr: string) =>
    new Date(dateStr).toLocaleDateString('vi-VN', { year: 'numeric', month: 'short', day: 'numeric' });

  return (
    <AppShell>
      <PageHeader
        title="Đơn hàng của shop"
        subtitle="Theo dõi và xử lý đơn hàng phát sinh từ sản phẩm của shop"
        breadcrumbs={[{ label: 'Đối tác', href: '/partner/dashboard' }, { label: 'Đơn hàng' }]}
      />

      <Box sx={{ mb: 3 }}>
        <FormControl size="small" sx={{ minWidth: 180 }}>
          <InputLabel>Trạng thái</InputLabel>
          <Select
            value={statusFilter}
            label="Trạng thái"
            onChange={e => { setStatusFilter(e.target.value); setPage(0); }}
          >
            <MenuItem value="">Tất cả</MenuItem>
            <MenuItem value="NEW">Đơn mới</MenuItem>
            <MenuItem value="ACCEPTED">Đã nhận</MenuItem>
            <MenuItem value="PACKING">Đang đóng gói</MenuItem>
            <MenuItem value="READY_TO_SHIP">Sẵn sàng giao</MenuItem>
            <MenuItem value="SHIPPED">Đang giao</MenuItem>
            <MenuItem value="DELIVERED">Đã giao</MenuItem>
            <MenuItem value="CANCELLED">Đã hủy</MenuItem>
          </Select>
        </FormControl>
      </Box>

      {loading ? (
        <SkeletonTable rows={8} columns={7} />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchOrders} />
      ) : orders.length === 0 ? (
        <EmptyState
          title="Chưa có đơn hàng"
          description={statusFilter ? 'Thử chọn trạng thái khác' : 'Đơn hàng sẽ xuất hiện khi khách mua sản phẩm của shop trên hệ thống'}
        />
      ) : (
        <>
          <TableContainer component={Paper} elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600 }}>Mã đơn shop</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Mã đơn</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Trạng thái</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Tiền hàng</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Hoa hồng</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Shop nhận</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Ngày tạo</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {orders.map(order => (
                  <TableRow key={order.id} hover>
                    <TableCell>{order.id}</TableCell>
                    <TableCell>#{order.orderId}</TableCell>
                    <TableCell><StatusBadge status={order.status} size="small" /></TableCell>
                    <TableCell>{formatCurrency(order.subtotal)}</TableCell>
                    <TableCell>{formatCurrency(order.commissionAmount)}</TableCell>
                    <TableCell>{formatCurrency(order.partnerPayableAmount)}</TableCell>
                    <TableCell>{formatDate(order.createdAt)}</TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        startIcon={<VisibilityIcon />}
                        onClick={() => navigate(`/partner/orders/${order.id}`)}
                      >
                        Xem
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          {totalPages > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
              <Pagination
                count={totalPages}
                page={page + 1}
                onChange={(_, val) => setPage(val - 1)}
                color="primary"
              />
            </Box>
          )}
        </>
      )}
    </AppShell>
  );
};

export default PartnerOrdersPage;
