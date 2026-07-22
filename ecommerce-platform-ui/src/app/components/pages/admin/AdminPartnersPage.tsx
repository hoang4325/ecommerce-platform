import React from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
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
import VisibilityIcon from '@mui/icons-material/Visibility';
import { AppShell, PageHeader, StatusBadge, SkeletonTable, EmptyState, ErrorState } from '../../shared';
import { getPartners } from '../../../api/AdminRequest';
import { PartnerResponse, PartnerStatus } from '../../../../types/partner';
import { PaginatedDTO } from '../../../../types/PaginatedDTO';

const PAGE_SIZE = 10;

const AdminPartnersPage = () => {
  const navigate = useNavigate();
  const [data, setData] = React.useState<PaginatedDTO<PartnerResponse>>({ data: [], currentPage: 0, totalPages: 0, totalItems: 0, totalPrice: 0, pageSize: PAGE_SIZE, hasNext: false, hasPrevious: false });
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [page, setPage] = React.useState(0);
  const [statusFilter, setStatusFilter] = React.useState<string>('');
  const [search, setSearch] = React.useState('');

  const fetchPartners = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await getPartners({ status: statusFilter as PartnerStatus || undefined, page, size: PAGE_SIZE, sort: 'createdAt,desc' });
      setData(result);
    } catch (err: any) {
      setError(err?.message || 'Không thể tải danh sách đối tác');
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter]);

  React.useEffect(() => {
    fetchPartners();
  }, [fetchPartners]);

  const handleSearch = () => {
    setPage(0);
    fetchPartners();
  };

  const filteredData = search
    ? { ...data, data: data.data.filter(p =>
        p.name.toLowerCase().includes(search.toLowerCase()) ||
        p.email.toLowerCase().includes(search.toLowerCase()) ||
        p.businessName.toLowerCase().includes(search.toLowerCase())
      )}
    : data;

  return (
    <AppShell>
      <PageHeader title="Đối tác" subtitle="Quản lý hồ sơ đăng ký và tài khoản đối tác" />

      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          label="Tìm tên, email, doanh nghiệp"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
          sx={{ minWidth: 300 }}
        />
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel>Trạng thái</InputLabel>
          <Select value={statusFilter} label="Trạng thái" onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}>
            <MenuItem value="">Tất cả</MenuItem>
            <MenuItem value="PENDING_REVIEW">Chờ duyệt</MenuItem>
            <MenuItem value="CHANGES_REQUESTED">Cần bổ sung</MenuItem>
            <MenuItem value="APPROVED">Đã duyệt</MenuItem>
            <MenuItem value="REJECTED">Từ chối</MenuItem>
            <MenuItem value="SUSPENDED">Tạm dừng</MenuItem>
            <MenuItem value="TERMINATED">Đã chấm dứt</MenuItem>
          </Select>
        </FormControl>
      </Box>

      {loading ? (
        <SkeletonTable rows={PAGE_SIZE} columns={7} />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchPartners} />
      ) : filteredData.data.length === 0 ? (
        <EmptyState title="Không tìm thấy đối tác" description="Thử đổi từ khóa tìm kiếm hoặc bộ lọc trạng thái." />
      ) : (
        <Paper elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', overflow: 'hidden' }}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>ID</TableCell>
                  <TableCell>Code</TableCell>
                  <TableCell>Doanh nghiệp</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell>Ngày tạo</TableCell>
                  <TableCell align="right">Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredData.data.map((partner) => (
                  <TableRow
                    key={partner.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/admin/partners/${partner.id}`)}
                  >
                    <TableCell>{partner.id}</TableCell>
                    <TableCell>{partner.code}</TableCell>
                    <TableCell>{partner.businessName}</TableCell>
                    <TableCell>{partner.email}</TableCell>
                    <TableCell><StatusBadge status={partner.status} /></TableCell>
                    <TableCell>{new Date(partner.createdAt).toLocaleDateString('vi-VN')}</TableCell>
                    <TableCell align="right" onClick={(event) => event.stopPropagation()}>
                      <Button
                        size="small"
                        variant="outlined"
                        startIcon={<VisibilityIcon />}
                        onClick={() => navigate(`/admin/partners/${partner.id}`)}
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
            count={filteredData.totalItems}
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

export default AdminPartnersPage;
