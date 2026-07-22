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
  TextField,
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
import { getPartnerSettlements } from '../../../api/SettlementRequest';

const PartnerSettlementsPage = () => {
  const navigate = useNavigate();

  const [settlements, setSettlements] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState('');
  const [statusFilter, setStatusFilter] = React.useState<string>('');
  const [startDate, setStartDate] = React.useState('');
  const [endDate, setEndDate] = React.useState('');
  const [page, setPage] = React.useState(0);
  const [totalPages, setTotalPages] = React.useState(0);

  const fetchSettlements = React.useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const params: any = { page, size: 10 };
      if (statusFilter) params.status = statusFilter;
      if (startDate) params.startDate = startDate;
      if (endDate) params.endDate = endDate;
      const res = await getPartnerSettlements(0, params);
      setSettlements(res.data);
      setTotalPages(res.totalPages);
    } catch {
      setError('Không tải được danh sách thanh toán');
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter, startDate, endDate]);

  React.useEffect(() => { fetchSettlements(); }, [fetchSettlements]);

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(amount);

  const formatDate = (dateStr: string) =>
    new Date(dateStr).toLocaleDateString('vi-VN', { year: 'numeric', month: 'short', day: 'numeric' });

  return (
    <AppShell>
      <PageHeader
        title="Thanh toán"
        subtitle="Theo dõi các kỳ đối soát và khoản tiền shop được nhận"
        breadcrumbs={[{ label: 'Đối tác', href: '/partner/dashboard' }, { label: 'Thanh toán' }]}
      />

      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel>Trạng thái</InputLabel>
          <Select
            value={statusFilter}
            label="Trạng thái"
            onChange={e => { setStatusFilter(e.target.value); setPage(0); }}
          >
            <MenuItem value="">Tất cả</MenuItem>
            <MenuItem value="OPEN">Đang mở</MenuItem>
            <MenuItem value="CALCULATED">Đã tính</MenuItem>
            <MenuItem value="UNDER_REVIEW">Đang rà soát</MenuItem>
            <MenuItem value="APPROVED">Đã duyệt</MenuItem>
            <MenuItem value="PAID">Đã thanh toán</MenuItem>
            <MenuItem value="FAILED">Thất bại</MenuItem>
          </Select>
        </FormControl>
        <TextField
          size="small"
          type="date"
          label="Từ ngày"
          InputLabelProps={{ shrink: true }}
          value={startDate}
          onChange={e => setStartDate(e.target.value)}
        />
        <TextField
          size="small"
          type="date"
          label="Đến ngày"
          InputLabelProps={{ shrink: true }}
          value={endDate}
          onChange={e => setEndDate(e.target.value)}
        />
      </Box>

      {loading ? (
        <SkeletonTable rows={8} columns={7} />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchSettlements} />
      ) : settlements.length === 0 ? (
        <EmptyState title="Chưa có kỳ thanh toán" description="Kỳ thanh toán sẽ xuất hiện sau khi shop có đơn hàng đã giao và admin tính đối soát." />
      ) : (
        <>
          <TableContainer component={Paper} elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600 }}>ID</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Kỳ đối soát</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Doanh thu</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Hoa hồng</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Hoàn tiền</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Shop nhận</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Trạng thái</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {settlements.map(s => (
                  <TableRow key={s.id} hover>
                    <TableCell>{s.id}</TableCell>
                    <TableCell>
                      {formatDate(s.periodStart)} - {formatDate(s.periodEnd)}
                    </TableCell>
                    <TableCell>{formatCurrency(s.grossSales)}</TableCell>
                    <TableCell>{formatCurrency(s.commissionAmount)}</TableCell>
                    <TableCell>{formatCurrency(s.refundAmount)}</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{formatCurrency(s.payableAmount)}</TableCell>
                    <TableCell><StatusBadge status={s.status} size="small" /></TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        startIcon={<VisibilityIcon />}
                        onClick={() => navigate(`/partner/settlements/${s.id}`)}
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

export default PartnerSettlementsPage;
