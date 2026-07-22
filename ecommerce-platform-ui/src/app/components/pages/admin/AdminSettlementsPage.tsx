import React from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TablePagination from '@mui/material/TablePagination';
import Paper from '@mui/material/Paper';
import Alert from '@mui/material/Alert';
import AddIcon from '@mui/icons-material/Add';
import { AppShell, PageHeader, StatusBadge, SkeletonTable, EmptyState, ErrorState } from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import { getSettlements, calculateSettlement } from '../../../api/AdminRequest';
import { SettlementResponse, SettlementStatus } from '../../../../types/partner';
import { PaginatedDTO } from '../../../../types/PaginatedDTO';

const PAGE_SIZE = 10;

const settlementStatusOptions: Array<{ value: SettlementStatus; label: string }> = [
  { value: 'OPEN', label: 'Đang mở' },
  { value: 'CALCULATED', label: 'Đã tính' },
  { value: 'UNDER_REVIEW', label: 'Đang rà soát' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'PAID', label: 'Đã thanh toán' },
  { value: 'FAILED', label: 'Thất bại' },
  { value: 'CANCELLED', label: 'Đã hủy' },
];

const toDateTimeStart = (date: string) => `${date}T00:00:00`;
const toDateTimeEnd = (date: string) => `${date}T23:59:59`;

const formatCurrency = (value: number, currency = 'VND') =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: currency || 'VND',
    maximumFractionDigits: currency === 'VND' ? 0 : 2,
  }).format(Number(value || 0));

const formatDate = (value: string) =>
  new Intl.DateTimeFormat('vi-VN').format(new Date(value));

const AdminSettlementsPage = () => {
  const navigate = useNavigate();
  const { showSnackbar } = useSnackbar();
  const [data, setData] = React.useState<PaginatedDTO<SettlementResponse>>({ data: [], currentPage: 0, totalPages: 0, totalItems: 0, totalPrice: 0, pageSize: PAGE_SIZE, hasNext: false, hasPrevious: false });
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [page, setPage] = React.useState(0);
  const [statusFilter, setStatusFilter] = React.useState<string>('');
  const [startDate, setStartDate] = React.useState('');
  const [endDate, setEndDate] = React.useState('');

  const [calcDialogOpen, setCalcDialogOpen] = React.useState(false);
  const [calcPartnerId, setCalcPartnerId] = React.useState('');
  const [calcPeriodStart, setCalcPeriodStart] = React.useState('');
  const [calcPeriodEnd, setCalcPeriodEnd] = React.useState('');
  const [calcCurrency, setCalcCurrency] = React.useState('VND');
  const [calcLoading, setCalcLoading] = React.useState(false);

  const fetchSettlements = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await getSettlements({
        status: statusFilter as SettlementStatus || undefined,
        page, size: PAGE_SIZE, sort: 'createdAt,desc',
        startDate: startDate || undefined,
        endDate: endDate || undefined,
      });
      setData(result);
    } catch (err: any) {
      setError(err?.message || 'Không tải được danh sách thanh toán');
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter, startDate, endDate]);

  React.useEffect(() => {
    fetchSettlements();
  }, [fetchSettlements]);

  const handleCalculate = async () => {
    if (!calcPartnerId || !calcPeriodStart || !calcPeriodEnd) return;
    setCalcLoading(true);
    try {
      await calculateSettlement(
        Number(calcPartnerId),
        toDateTimeStart(calcPeriodStart),
        toDateTimeEnd(calcPeriodEnd),
        calcCurrency
      );
      showSnackbar('Đã tạo kỳ đối soát thanh toán', 'success');
      setCalcDialogOpen(false);
      setCalcPartnerId('');
      setCalcPeriodStart('');
      setCalcPeriodEnd('');
      setCalcCurrency('VND');
      fetchSettlements();
    } catch (err: any) {
      showSnackbar(err?.message || 'Không tính được thanh toán', 'error');
    } finally {
      setCalcLoading(false);
    }
  };

  return (
    <AppShell>
      <PageHeader
        title="Thanh toán đối tác"
        subtitle="Tính, duyệt và ghi nhận các kỳ đối soát tiền hàng cho đối tác"
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setCalcDialogOpen(true)}>Tính thanh toán</Button>}
      />

      <Alert severity="info" sx={{ mb: 3 }}>
        Trang này chỉ có dữ liệu khi đã phát sinh đơn hàng của đối tác và đơn đã đủ điều kiện đối soát. Dữ liệu Shopee vừa import mới là shop và sản phẩm, nên danh sách thanh toán có thể đang trống.
      </Alert>

      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel>Trạng thái</InputLabel>
          <Select value={statusFilter} label="Trạng thái" onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}>
            <MenuItem value="">Tất cả</MenuItem>
            {settlementStatusOptions.map(option => (
              <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <TextField size="small" type="date" label="Từ ngày" InputLabelProps={{ shrink: true }} value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        <TextField size="small" type="date" label="Đến ngày" InputLabelProps={{ shrink: true }} value={endDate} onChange={(e) => setEndDate(e.target.value)} />
      </Box>

      {loading ? (
        <SkeletonTable rows={PAGE_SIZE} columns={7} />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchSettlements} />
      ) : data.data.length === 0 ? (
        <EmptyState title="Chưa có kỳ thanh toán" description="Kỳ thanh toán sẽ xuất hiện sau khi có đơn hàng đã giao hoặc khi admin tính đối soát cho một khoảng thời gian có doanh thu." />
      ) : (
        <Paper elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', overflow: 'hidden' }}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>ID</TableCell>
                  <TableCell>Đối tác</TableCell>
                  <TableCell>Kỳ đối soát</TableCell>
                  <TableCell>Doanh thu</TableCell>
                  <TableCell>Cần trả</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell align="right">Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.data.map((s) => (
                  <TableRow
                    key={s.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/admin/settlements/${s.id}`)}
                  >
                    <TableCell>{s.id}</TableCell>
                    <TableCell>#{s.partnerId}</TableCell>
                    <TableCell>{formatDate(s.periodStart)} - {formatDate(s.periodEnd)}</TableCell>
                    <TableCell>{formatCurrency(s.grossSales, s.currency)}</TableCell>
                    <TableCell>{formatCurrency(s.payableAmount, s.currency)}</TableCell>
                    <TableCell><StatusBadge status={s.status} /></TableCell>
                    <TableCell align="right">
                      <Button size="small" onClick={(event) => { event.stopPropagation(); navigate(`/admin/settlements/${s.id}`); }}>
                        Xem chi tiết
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={data.totalItems}
            page={page}
            onPageChange={(_, p) => setPage(p)}
            rowsPerPage={PAGE_SIZE}
            rowsPerPageOptions={[PAGE_SIZE]}
          />
        </Paper>
      )}

      <Dialog open={calcDialogOpen} onClose={() => !calcLoading && setCalcDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Tính thanh toán đối tác</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <TextField label="ID đối tác" type="number" value={calcPartnerId} onChange={(e) => setCalcPartnerId(e.target.value)} fullWidth />
            <TextField label="Từ ngày" type="date" InputLabelProps={{ shrink: true }} value={calcPeriodStart} onChange={(e) => setCalcPeriodStart(e.target.value)} fullWidth />
            <TextField label="Đến ngày" type="date" InputLabelProps={{ shrink: true }} value={calcPeriodEnd} onChange={(e) => setCalcPeriodEnd(e.target.value)} fullWidth />
            <FormControl fullWidth>
              <InputLabel>Tiền tệ</InputLabel>
              <Select value={calcCurrency} label="Tiền tệ" onChange={(e) => setCalcCurrency(e.target.value)}>
                <MenuItem value="VND">VND</MenuItem>
                <MenuItem value="USD">USD</MenuItem>
                <MenuItem value="EUR">EUR</MenuItem>
              </Select>
            </FormControl>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCalcDialogOpen(false)} disabled={calcLoading}>Hủy</Button>
          <Button onClick={handleCalculate} variant="contained" disabled={calcLoading || !calcPartnerId || !calcPeriodStart || !calcPeriodEnd}>
            {calcLoading ? 'Đang tính...' : 'Tính thanh toán'}
          </Button>
        </DialogActions>
      </Dialog>
    </AppShell>
  );
};

export default AdminSettlementsPage;
