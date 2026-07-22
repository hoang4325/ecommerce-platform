import React from 'react';
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
  Typography,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  CircularProgress,
} from '@mui/material';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RestoreIcon from '@mui/icons-material/Restore';
import {
  AppShell,
  PageHeader,
  StatusBadge,
  SkeletonTable,
  EmptyState,
  ErrorState,
  ConfirmDialog,
} from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import {
  getPartnerMembers,
  inviteMember,
  activateMember,
  suspendMember,
  restoreMember,
  transferOwnership,
} from '../../../api/PartnerRequest';
import type { PartnerMemberResponse, PartnerMemberRole } from '../../../../types/partner';

const ROLE_LABELS: Record<PartnerMemberRole, string> = {
  OWNER: 'Chủ shop',
  MANAGER: 'Quản lý',
  PRODUCT_STAFF: 'Nhân viên sản phẩm',
  ORDER_STAFF: 'Nhân viên đơn hàng',
  FINANCE_STAFF: 'Nhân viên tài chính',
};

const PartnerMembersPage = () => {
  const { showSnackbar } = useSnackbar();

  const [members, setMembers] = React.useState<PartnerMemberResponse[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState('');
  const [actionLoading, setActionLoading] = React.useState<number | null>(null);

  const [inviteDialogOpen, setInviteDialogOpen] = React.useState(false);
  const [inviteUserId, setInviteUserId] = React.useState('');
  const [inviteRole, setInviteRole] = React.useState<PartnerMemberRole>('ORDER_STAFF');
  const [inviteSubmitting, setInviteSubmitting] = React.useState(false);

  const [transferDialogOpen, setTransferDialogOpen] = React.useState(false);
  const [transferTargetId, setTransferTargetId] = React.useState<number | null>(null);

  const fetchMembers = React.useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await getPartnerMembers();
      setMembers(res);
    } catch {
      setError('Không tải được danh sách thành viên');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => { fetchMembers(); }, [fetchMembers]);

  const handleInvite = async () => {
    const userId = Number(inviteUserId);
    if (!userId) {
      showSnackbar('Vui lòng nhập ID người dùng hợp lệ', 'error');
      return;
    }
    setInviteSubmitting(true);
    try {
      await inviteMember({ userId, role: inviteRole });
      showSnackbar('Đã mời thành viên', 'success');
      setInviteDialogOpen(false);
      setInviteUserId('');
      fetchMembers();
    } catch {
      showSnackbar('Không thể mời thành viên', 'error');
    } finally {
      setInviteSubmitting(false);
    }
  };

  const handleActivate = async (memberId: number) => {
    setActionLoading(memberId);
    try {
      await activateMember(memberId);
      showSnackbar('Đã kích hoạt thành viên', 'success');
      fetchMembers();
    } catch {
      showSnackbar('Không thể kích hoạt thành viên', 'error');
    } finally {
      setActionLoading(null);
    }
  };

  const handleSuspend = async (memberId: number) => {
    setActionLoading(memberId);
    try {
      await suspendMember(memberId);
      showSnackbar('Đã tạm khóa thành viên', 'success');
      fetchMembers();
    } catch {
      showSnackbar('Không thể tạm khóa thành viên', 'error');
    } finally {
      setActionLoading(null);
    }
  };

  const handleRestore = async (memberId: number) => {
    setActionLoading(memberId);
    try {
      await restoreMember(memberId);
      showSnackbar('Đã khôi phục thành viên', 'success');
      fetchMembers();
    } catch {
      showSnackbar('Không thể khôi phục thành viên', 'error');
    } finally {
      setActionLoading(null);
    }
  };

  const handleTransferOwnership = async () => {
    if (!transferTargetId) return;
    setActionLoading(transferTargetId);
    try {
      await transferOwnership(transferTargetId);
      showSnackbar('Đã chuyển quyền sở hữu', 'success');
      setTransferDialogOpen(false);
      setTransferTargetId(null);
      fetchMembers();
    } catch {
      showSnackbar('Không thể chuyển quyền sở hữu', 'error');
    } finally {
      setActionLoading(null);
    }
  };

  const getActions = (member: PartnerMemberResponse) => {
    const actions: React.ReactNode[] = [];

    if (member.status === 'INVITED') {
      actions.push(
        <Button key="activate" size="small" color="success" startIcon={<CheckCircleIcon />}
          disabled={actionLoading === member.id} onClick={() => handleActivate(member.id)}>
          Kích hoạt
        </Button>,
        <Button key="remove" size="small" color="error" startIcon={<BlockIcon />}
          disabled={actionLoading === member.id} onClick={() => handleSuspend(member.id)}>
          Xóa
        </Button>
      );
    }
    if (member.status === 'ACTIVE' && member.role !== 'OWNER') {
      actions.push(
        <Button key="suspend" size="small" color="warning" startIcon={<BlockIcon />}
          disabled={actionLoading === member.id} onClick={() => handleSuspend(member.id)}>
          Tạm khóa
        </Button>,
        <Button key="transfer" size="small" color="secondary"
          onClick={() => { setTransferTargetId(member.id); setTransferDialogOpen(true); }}>
          Chuyển chủ shop
        </Button>
      );
    }
    if (member.status === 'SUSPENDED') {
      actions.push(
        <Button key="restore" size="small" color="success" startIcon={<RestoreIcon />}
          disabled={actionLoading === member.id} onClick={() => handleRestore(member.id)}>
          Khôi phục
        </Button>
      );
    }

    return actions;
  };

  return (
    <AppShell>
      <PageHeader
        title="Thành viên"
        subtitle="Quản lý nhân sự có quyền truy cập gian hàng"
        breadcrumbs={[{ label: 'Đối tác', href: '/partner/dashboard' }, { label: 'Thành viên' }]}
        actions={
          <Button variant="contained" startIcon={<PersonAddIcon />} onClick={() => setInviteDialogOpen(true)}>
            Mời thành viên
          </Button>
        }
      />

      {loading ? (
        <SkeletonTable rows={6} columns={5} />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchMembers} />
      ) : members.length === 0 ? (
        <EmptyState
          title="Chưa có thành viên"
          description="Mời thêm nhân sự để cùng quản lý sản phẩm, đơn hàng và tài chính của shop"
          action={
            <Button variant="contained" startIcon={<PersonAddIcon />} onClick={() => setInviteDialogOpen(true)}>
              Mời thành viên
            </Button>
          }
        />
      ) : (
        <TableContainer component={Paper} elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>Tên đăng nhập</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Email</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Vai trò</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Trạng thái</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {members.map(m => (
                <TableRow key={m.id} hover>
                  <TableCell>{m.username || '-'}</TableCell>
                  <TableCell>{m.email || '-'}</TableCell>
                  <TableCell>{ROLE_LABELS[m.role]}</TableCell>
                  <TableCell><StatusBadge status={m.status} size="small" /></TableCell>
                  <TableCell>
                    {getActions(m).length > 0 ? (
                      <Box sx={{ display: 'flex', gap: 0.5 }}>{getActions(m)}</Box>
                    ) : (
                      <Typography variant="body2" color="text.secondary">-</Typography>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={inviteDialogOpen} onClose={() => setInviteDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Mời thành viên</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="ID người dùng"
            type="number"
            value={inviteUserId}
            onChange={e => setInviteUserId(e.target.value)}
            sx={{ mb: 2, mt: 1 }}
          />
          <FormControl fullWidth>
            <InputLabel>Vai trò</InputLabel>
            <Select
              value={inviteRole}
              label="Vai trò"
              onChange={e => setInviteRole(e.target.value as PartnerMemberRole)}
            >
              {Object.entries(ROLE_LABELS).map(([value, label]) => (
                value !== 'OWNER' && <MenuItem key={value} value={value}>{label}</MenuItem>
              ))}
            </Select>
          </FormControl>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setInviteDialogOpen(false)}>Hủy</Button>
          <Button variant="contained" onClick={handleInvite} disabled={inviteSubmitting}>
            {inviteSubmitting ? <CircularProgress size={20} /> : 'Mời'}
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={transferDialogOpen}
        title="Chuyển quyền sở hữu"
        message="Bạn có chắc muốn chuyển quyền chủ shop cho thành viên này? Thao tác này không thể hoàn tác."
        confirmLabel="Chuyển quyền"
        confirmColor="error"
        loading={actionLoading !== null}
        onConfirm={handleTransferOwnership}
        onCancel={() => { setTransferDialogOpen(false); setTransferTargetId(null); }}
      />
    </AppShell>
  );
};

export default PartnerMembersPage;
