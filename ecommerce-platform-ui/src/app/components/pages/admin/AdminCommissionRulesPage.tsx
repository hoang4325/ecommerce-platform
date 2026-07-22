import React from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
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
import AddIcon from '@mui/icons-material/Add';
import { AppShell, PageHeader, StatusBadge, SkeletonTable, EmptyState, ErrorState } from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import { getCommissionRules, activateCommissionRule, deactivateCommissionRule, expireCommissionRule } from '../../../api/AdminRequest';
import { CommissionRuleResponse } from '../../../../types/partner';
import { PaginatedDTO } from '../../../../types/PaginatedDTO';

const PAGE_SIZE = 10;

const formatCurrency = (value: number, currency = 'VND') =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: currency || 'VND',
    maximumFractionDigits: currency === 'VND' ? 0 : 2,
  }).format(Number(value || 0));

const formatDate = (value: string) =>
  new Intl.DateTimeFormat('vi-VN').format(new Date(value));

const formatRate = (rate: number) =>
  `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(Number(rate || 0) * 100)}%`;

const AdminCommissionRulesPage = () => {
  const navigate = useNavigate();
  const { showSnackbar } = useSnackbar();
  const [data, setData] = React.useState<PaginatedDTO<CommissionRuleResponse>>({ data: [], currentPage: 0, totalPages: 0, totalItems: 0, totalPrice: 0, pageSize: PAGE_SIZE, hasNext: false, hasPrevious: false });
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [page, setPage] = React.useState(0);
  const [statusFilter, setStatusFilter] = React.useState<string>('');

  const fetchRules = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await getCommissionRules({ page, size: PAGE_SIZE, sort: 'createdAt,desc' });
      let filtered = result;
      if (statusFilter) {
        filtered = { ...result, data: result.data.filter(r => r.status === statusFilter) };
      }
      setData(filtered);
    } catch (err: any) {
      setError(err?.message || 'Không tải được quy tắc hoa hồng');
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter]);

  React.useEffect(() => {
    fetchRules();
  }, [fetchRules]);

  const handleActivate = async (ruleId: number) => {
    try {
      await activateCommissionRule(ruleId);
      showSnackbar('Đã kích hoạt quy tắc', 'success');
      fetchRules();
    } catch (err: any) {
      showSnackbar(err?.message || 'Không kích hoạt được quy tắc', 'error');
    }
  };

  const handleDeactivate = async (ruleId: number) => {
    try {
      await deactivateCommissionRule(ruleId);
      showSnackbar('Đã tạm dừng quy tắc', 'success');
      fetchRules();
    } catch (err: any) {
      showSnackbar(err?.message || 'Không tạm dừng được quy tắc', 'error');
    }
  };

  const handleExpire = async (ruleId: number) => {
    try {
      await expireCommissionRule(ruleId);
      showSnackbar('Đã hết hạn quy tắc', 'success');
      fetchRules();
    } catch (err: any) {
      showSnackbar(err?.message || 'Không hết hạn được quy tắc', 'error');
    }
  };

  return (
    <AppShell>
      <PageHeader
        title="Quy tắc hoa hồng"
        subtitle="Thiết lập tỷ lệ hoa hồng dùng khi khách đặt hàng sản phẩm của đối tác"
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/admin/commission-rules/new')}>Tạo quy tắc</Button>}
      />

      <Box sx={{ display: 'flex', gap: 2, mb: 3 }}>
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel>Trạng thái</InputLabel>
          <Select value={statusFilter} label="Trạng thái" onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}>
            <MenuItem value="">Tất cả</MenuItem>
            <MenuItem value="DRAFT">Bản nháp</MenuItem>
            <MenuItem value="ACTIVE">Đang áp dụng</MenuItem>
            <MenuItem value="INACTIVE">Tạm dừng</MenuItem>
            <MenuItem value="EXPIRED">Hết hạn</MenuItem>
          </Select>
        </FormControl>
      </Box>

      {loading ? (
        <SkeletonTable rows={PAGE_SIZE} columns={9} />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchRules} />
      ) : data.data.length === 0 ? (
        <EmptyState title="Chưa có quy tắc hoa hồng" description="Tạo quy tắc đầu tiên để hệ thống tính hoa hồng khi đơn hàng đối tác phát sinh." action={<Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/admin/commission-rules/new')}>Tạo quy tắc</Button>} />
      ) : (
        <Paper elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', overflow: 'hidden' }}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Tên</TableCell>
                  <TableCell>Phạm vi</TableCell>
                  <TableCell>Tỷ lệ (%)</TableCell>
                  <TableCell>Phí cố định</TableCell>
                  <TableCell>Ưu tiên</TableCell>
                  <TableCell>Từ ngày</TableCell>
                  <TableCell>Đến ngày</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.data.map((rule) => {
                  const scope = rule.partnerId ? 'Đối tác' : rule.categoryId ? 'Danh mục' : rule.productId ? 'Sản phẩm' : 'Toàn sàn';
                  return (
                    <TableRow
                      key={rule.id}
                      hover
                      sx={{ cursor: 'pointer' }}
                      onClick={() => navigate(`/admin/commission-rules/edit/${rule.id}`)}
                    >
                      <TableCell>{rule.name}</TableCell>
                      <TableCell>{scope}</TableCell>
                      <TableCell>{formatRate(rule.rate)}</TableCell>
                      <TableCell>{rule.fixedFee > 0 ? formatCurrency(rule.fixedFee, rule.currency) : '-'}</TableCell>
                      <TableCell>{rule.priority}</TableCell>
                      <TableCell>{formatDate(rule.validFrom)}</TableCell>
                      <TableCell>{rule.validTo ? formatDate(rule.validTo) : '-'}</TableCell>
                      <TableCell><StatusBadge status={rule.status} /></TableCell>
                      <TableCell>
                        <Box sx={{ display: 'flex', gap: 0.5 }}>
                          {rule.status === 'DRAFT' && (
                            <Button size="small" onClick={(e) => { e.stopPropagation(); handleActivate(rule.id); }}>Kích hoạt</Button>
                          )}
                          {rule.status === 'ACTIVE' && (
                            <>
                              <Button size="small" color="warning" onClick={(e) => { e.stopPropagation(); handleDeactivate(rule.id); }}>Tạm dừng</Button>
                              <Button size="small" color="error" onClick={(e) => { e.stopPropagation(); handleExpire(rule.id); }}>Hết hạn</Button>
                            </>
                          )}
                          {rule.status === 'INACTIVE' && (
                            <>
                              <Button size="small" onClick={(e) => { e.stopPropagation(); handleActivate(rule.id); }}>Kích hoạt</Button>
                              <Button size="small" color="error" onClick={(e) => { e.stopPropagation(); handleExpire(rule.id); }}>Hết hạn</Button>
                            </>
                          )}
                          <Button size="small" onClick={(e) => { e.stopPropagation(); navigate(`/admin/commission-rules/edit/${rule.id}`); }}>Sửa</Button>
                        </Box>
                      </TableCell>
                    </TableRow>
                  );
                })}
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

export default AdminCommissionRulesPage;
