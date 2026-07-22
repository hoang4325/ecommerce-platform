import React from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TablePagination from '@mui/material/TablePagination';
import Paper from '@mui/material/Paper';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import VisibilityIcon from '@mui/icons-material/Visibility';
import RateReviewIcon from '@mui/icons-material/RateReview';
import { AppShell, PageHeader, StatusBadge, SkeletonTable, EmptyState, ErrorState } from '../../shared';
import { getOffers } from '../../../api/AdminRequest';
import { PartnerOfferResponse, PartnerOfferStatus } from '../../../../types/partner';
import { PaginatedDTO } from '../../../../types/PaginatedDTO';

const PAGE_SIZE = 10;

const formatCurrency = (amount: number, currency = 'VND') => {
  const normalizedCurrency = currency === 'USD' || currency === 'EUR' ? currency : 'VND';

  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: normalizedCurrency,
    maximumFractionDigits: normalizedCurrency === 'VND' ? 0 : 2,
  }).format(amount);
};

const formatDate = (value?: string) => {
  if (!value) {
    return '-';
  }

  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(value));
};

const AdminOffersPage = () => {
  const navigate = useNavigate();
  const [data, setData] = React.useState<PaginatedDTO<PartnerOfferResponse>>({ data: [], currentPage: 0, totalPages: 0, totalItems: 0, totalPrice: 0, pageSize: PAGE_SIZE, hasNext: false, hasPrevious: false });
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [page, setPage] = React.useState(0);
  const [statusFilter, setStatusFilter] = React.useState<string>('');

  const fetchOffers = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await getOffers({ status: statusFilter as PartnerOfferStatus || undefined, page, size: PAGE_SIZE, sort: 'createdAt,desc' });
      setData(result);
    } catch (err: any) {
      setError(err?.message || 'Không thể tải danh sách ưu đãi');
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter]);

  React.useEffect(() => {
    fetchOffers();
  }, [fetchOffers]);

  return (
    <AppShell>
      <PageHeader
        title="Kiểm duyệt ưu đãi"
        subtitle="Danh sách sản phẩm/offer do đối tác gửi lên sàn"
        actions={(
          <Stack direction="row" spacing={1}>
            <Chip icon={<RateReviewIcon />} label={`${data.totalItems} ưu đãi`} color="primary" variant="outlined" />
          </Stack>
        )}
      />

      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel>Trạng thái</InputLabel>
          <Select value={statusFilter} label="Trạng thái" onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}>
            <MenuItem value="">Tất cả</MenuItem>
            <MenuItem value="PENDING_REVIEW">Chờ duyệt</MenuItem>
            <MenuItem value="APPROVED">Đã duyệt</MenuItem>
            <MenuItem value="REJECTED">Từ chối</MenuItem>
            <MenuItem value="SUSPENDED">Tạm dừng</MenuItem>
            <MenuItem value="DRAFT">Bản nháp</MenuItem>
            <MenuItem value="ARCHIVED">Đã lưu trữ</MenuItem>
          </Select>
        </FormControl>
      </Box>

      {loading ? (
        <SkeletonTable rows={PAGE_SIZE} columns={7} />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchOffers} />
      ) : data.data.length === 0 ? (
        <EmptyState title="Không tìm thấy ưu đãi" description="Không có ưu đãi nào khớp với bộ lọc hiện tại." />
      ) : (
        <Paper elevation={0} sx={{ borderRadius: 3, border: 1, borderColor: 'divider', overflow: 'hidden' }}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>ID</TableCell>
                  <TableCell>Sản phẩm</TableCell>
                  <TableCell>Đối tác</TableCell>
                  <TableCell>SKU</TableCell>
                  <TableCell>Giá</TableCell>
                  <TableCell>Kho</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell>Ngày gửi</TableCell>
                  <TableCell align="right">Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.data.map((offer) => (
                  <TableRow
                    key={offer.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/admin/offers/${offer.id}`)}
                  >
                    <TableCell>{offer.id}</TableCell>
                    <TableCell sx={{ maxWidth: 520 }}>
                      <Box sx={{ fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={offer.productName || `Sản phẩm #${offer.productId}`}>
                        {offer.productName || `Sản phẩm #${offer.productId}`}
                      </Box>
                      <Box sx={{ color: 'text.secondary', fontSize: 13 }}>
                        Product ID: {offer.productId}
                      </Box>
                    </TableCell>
                    <TableCell>#{offer.partnerId}</TableCell>
                    <TableCell>{offer.partnerSku || '-'}</TableCell>
                    <TableCell>{formatCurrency(offer.price, offer.currency)}</TableCell>
                    <TableCell>{offer.reservedQuantity}/{offer.onHandQuantity}</TableCell>
                    <TableCell><StatusBadge status={offer.status} /></TableCell>
                    <TableCell>{formatDate(offer.submittedAt || offer.createdAt)}</TableCell>
                    <TableCell align="right" onClick={(event) => event.stopPropagation()}>
                      <Button
                        size="small"
                        variant="outlined"
                        startIcon={<VisibilityIcon />}
                        onClick={() => navigate(`/admin/offers/${offer.id}`)}
                        sx={{ textTransform: 'none', fontWeight: 700, whiteSpace: 'nowrap' }}
                      >
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
    </AppShell>
  );
};

export default AdminOffersPage;
