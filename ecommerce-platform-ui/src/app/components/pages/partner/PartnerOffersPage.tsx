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
  TextField,
  Pagination,
  Stack,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import ArchiveIcon from '@mui/icons-material/Archive';
import PublishIcon from '@mui/icons-material/Publish';
import {
  AppShell,
  PageHeader,
  StatusBadge,
  SkeletonTable,
  EmptyState,
  ErrorState,
} from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import { getPartnerOffers, submitOffer, archiveOffer } from '../../../api/PartnerOfferRequest';
import type { PartnerOfferResponse } from '../../../../types/partner';

const PartnerOffersPage = () => {
  const navigate = useNavigate();
  const { showSnackbar } = useSnackbar();

  const [offers, setOffers] = React.useState<PartnerOfferResponse[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState('');
  const [statusFilter, setStatusFilter] = React.useState<string>('');
  const [searchQuery, setSearchQuery] = React.useState('');
  const [page, setPage] = React.useState(0);
  const [totalPages, setTotalPages] = React.useState(0);
  const [actionLoading, setActionLoading] = React.useState<number | null>(null);

  const fetchOffers = React.useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const params: any = { page, size: 10 };
      if (statusFilter) params.status = statusFilter;
      const res = await getPartnerOffers(0, params);
      setOffers(res.data);
      setTotalPages(res.totalPages);
    } catch {
      setError('Không tải được danh sách sản phẩm');
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter]);

  React.useEffect(() => { fetchOffers(); }, [fetchOffers]);

  const handleSubmit = async (offerId: number) => {
    setActionLoading(offerId);
    try {
      await submitOffer(offerId);
      showSnackbar('Đã gửi sản phẩm để kiểm duyệt', 'success');
      fetchOffers();
    } catch {
      showSnackbar('Không gửi duyệt được sản phẩm', 'error');
    } finally {
      setActionLoading(null);
    }
  };

  const handleArchive = async (offerId: number) => {
    setActionLoading(offerId);
    try {
      await archiveOffer(offerId);
      showSnackbar('Đã lưu trữ sản phẩm', 'success');
      fetchOffers();
    } catch {
      showSnackbar('Không lưu trữ được sản phẩm', 'error');
    } finally {
      setActionLoading(null);
    }
  };

  const formatCurrency = (amount: number, currency: string) =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency: currency || 'VND', maximumFractionDigits: currency === 'VND' ? 0 : 2 }).format(amount);

  const filteredOffers = searchQuery
    ? offers.filter(o =>
        o.partnerSku.toLowerCase().includes(searchQuery.toLowerCase()) ||
        o.productName.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : offers;

  const actions = (
    <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/partner/offers/new')}>
      Thêm sản phẩm
    </Button>
  );

  return (
    <AppShell>
      <PageHeader
        title="Sản phẩm của shop"
        subtitle="Quản lý sản phẩm đang bán, giá và tồn kho"
        breadcrumbs={[{ label: 'Đối tác', href: '/partner/dashboard' }, { label: 'Sản phẩm' }]}
        actions={actions}
      />

      <Box sx={{ display: 'flex', gap: 2, mb: 3 }}>
        <FormControl size="small" sx={{ minWidth: 180 }}>
          <InputLabel>Trạng thái</InputLabel>
          <Select
            value={statusFilter}
            label="Trạng thái"
            onChange={e => { setStatusFilter(e.target.value); setPage(0); }}
          >
            <MenuItem value="">Tất cả</MenuItem>
            <MenuItem value="DRAFT">Bản nháp</MenuItem>
            <MenuItem value="PENDING_REVIEW">Chờ duyệt</MenuItem>
            <MenuItem value="APPROVED">Đã duyệt</MenuItem>
            <MenuItem value="REJECTED">Từ chối</MenuItem>
            <MenuItem value="SUSPENDED">Tạm dừng</MenuItem>
            <MenuItem value="ARCHIVED">Lưu trữ</MenuItem>
          </Select>
        </FormControl>
        <TextField
          size="small"
          placeholder="Tìm theo SKU hoặc tên sản phẩm..."
          value={searchQuery}
          onChange={e => setSearchQuery(e.target.value)}
          sx={{ minWidth: 280 }}
        />
      </Box>

      {loading ? (
        <SkeletonTable rows={8} columns={8} />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchOffers} />
      ) : filteredOffers.length === 0 ? (
        <EmptyState
          title="Chưa có sản phẩm"
          description={searchQuery || statusFilter ? 'Thử đổi bộ lọc hoặc từ khóa tìm kiếm' : 'Thêm sản phẩm đầu tiên để bắt đầu bán hàng'}
          action={
            !searchQuery && !statusFilter ? (
              <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/partner/offers/new')}>
                Thêm sản phẩm đầu tiên
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <TableContainer component={Paper} elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600 }}>ID</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>SKU</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Tên sản phẩm</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Giá</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Tồn kho</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Đã giữ</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Trạng thái</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredOffers.map(offer => (
                  <TableRow key={offer.id} hover>
                    <TableCell>{offer.id}</TableCell>
                    <TableCell>{offer.partnerSku}</TableCell>
                    <TableCell>{offer.productName}</TableCell>
                    <TableCell>{formatCurrency(offer.price, offer.currency)}</TableCell>
                    <TableCell>{offer.onHandQuantity}</TableCell>
                    <TableCell>{offer.reservedQuantity}</TableCell>
                    <TableCell><StatusBadge status={offer.status} size="small" /></TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={1}>
                        <Button
                          size="small"
                          startIcon={<EditIcon />}
                          onClick={() => navigate(`/partner/offers/edit/${offer.id}`)}
                        >
                          Sửa
                        </Button>
                        {offer.status === 'DRAFT' && (
                          <Button
                            size="small"
                            color="success"
                            startIcon={<PublishIcon />}
                            disabled={actionLoading === offer.id}
                            onClick={() => handleSubmit(offer.id)}
                          >
                            Gửi duyệt
                          </Button>
                        )}
                        {offer.status === 'APPROVED' && (
                          <Button
                            size="small"
                            color="warning"
                            startIcon={<ArchiveIcon />}
                            disabled={actionLoading === offer.id}
                            onClick={() => handleArchive(offer.id)}
                          >
                            Lưu trữ
                          </Button>
                        )}
                      </Stack>
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

export default PartnerOffersPage;
