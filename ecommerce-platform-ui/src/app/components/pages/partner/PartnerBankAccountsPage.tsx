import React from 'react';
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  CircularProgress,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import {
  AppShell,
  PageHeader,
  StatusBadge,
  SkeletonTable,
  EmptyState,
  ErrorState,
} from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import { getBankAccounts, addBankAccount } from '../../../api/PartnerRequest';
import type { PartnerBankAccountResponse } from '../../../../types/partner';

const PartnerBankAccountsPage = () => {
  const { showSnackbar } = useSnackbar();

  const [accounts, setAccounts] = React.useState<PartnerBankAccountResponse[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState('');

  const [addDialogOpen, setAddDialogOpen] = React.useState(false);
  const [bankName, setBankName] = React.useState('');
  const [accountName, setAccountName] = React.useState('');
  const [accountNumber, setAccountNumber] = React.useState('');
  const [addSubmitting, setAddSubmitting] = React.useState(false);

  const fetchAccounts = React.useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await getBankAccounts();
      setAccounts(res);
    } catch {
      setError('Không tải được danh sách tài khoản ngân hàng');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => { fetchAccounts(); }, [fetchAccounts]);

  const handleAdd = async () => {
    if (!bankName.trim() || !accountName.trim() || !accountNumber.trim()) {
      showSnackbar('Vui lòng nhập đầy đủ thông tin tài khoản', 'error');
      return;
    }
    setAddSubmitting(true);
    try {
      await addBankAccount({
        bankName: bankName.trim(),
        accountName: accountName.trim(),
        accountNumber: accountNumber.trim(),
      });
      showSnackbar('Đã thêm tài khoản ngân hàng', 'success');
      setAddDialogOpen(false);
      setBankName('');
      setAccountName('');
      setAccountNumber('');
      fetchAccounts();
    } catch {
      showSnackbar('Không thể thêm tài khoản ngân hàng', 'error');
    } finally {
      setAddSubmitting(false);
    }
  };

  return (
    <AppShell>
      <PageHeader
        title="Tài khoản ngân hàng"
        subtitle="Quản lý tài khoản nhận tiền thanh toán của shop"
        breadcrumbs={[{ label: 'Đối tác', href: '/partner/dashboard' }, { label: 'Tài khoản ngân hàng' }]}
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setAddDialogOpen(true)}>
            Thêm tài khoản
          </Button>
        }
      />

      {loading ? (
        <SkeletonTable rows={4} columns={5} />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchAccounts} />
      ) : accounts.length === 0 ? (
        <EmptyState
          title="Chưa có tài khoản ngân hàng"
          description="Thêm tài khoản ngân hàng để nhận tiền đối soát từ hệ thống"
          action={
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setAddDialogOpen(true)}>
              Thêm tài khoản
            </Button>
          }
        />
      ) : (
        <TableContainer component={Paper} elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>Ngân hàng</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Chủ tài khoản</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Số tài khoản</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Trạng thái</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {accounts.map(acc => (
                <TableRow key={acc.id} hover>
                  <TableCell>{acc.bankName}</TableCell>
                  <TableCell>{acc.accountName}</TableCell>
                  <TableCell>{acc.maskedAccountNumber}</TableCell>
                  <TableCell><StatusBadge status={acc.status} size="small" /></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={addDialogOpen} onClose={() => setAddDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Thêm tài khoản ngân hàng</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="Tên ngân hàng"
            value={bankName}
            onChange={e => setBankName(e.target.value)}
            sx={{ mb: 2, mt: 1 }}
          />
          <TextField
            fullWidth
            label="Tên chủ tài khoản"
            value={accountName}
            onChange={e => setAccountName(e.target.value)}
            sx={{ mb: 2 }}
          />
          <TextField
            fullWidth
            label="Số tài khoản"
            value={accountNumber}
            onChange={e => setAccountNumber(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddDialogOpen(false)}>Hủy</Button>
          <Button
            variant="contained"
            onClick={handleAdd}
            disabled={addSubmitting}
            startIcon={addSubmitting ? <CircularProgress size={20} /> : null}
          >
            {addSubmitting ? 'Đang thêm...' : 'Thêm'}
          </Button>
        </DialogActions>
      </Dialog>
    </AppShell>
  );
};

export default PartnerBankAccountsPage;
