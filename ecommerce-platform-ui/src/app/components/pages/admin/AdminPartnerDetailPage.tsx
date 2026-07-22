import React from 'react';
import { useParams } from 'react-router-dom';
import Grid from '@mui/material/Grid';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import TextField from '@mui/material/TextField';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Skeleton from '@mui/material/Skeleton';
import { AppShell, PageHeader, StatusBadge, ErrorState, ConfirmDialog } from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import { getPartner, approvePartner, rejectPartner, suspendPartner, restorePartner, terminatePartner, requestPartnerChanges } from '../../../api/AdminRequest';
import { PartnerResponse, PartnerDocumentResponse } from '../../../../types/partner';
import type { PartnerMemberResponse, PartnerBankAccountResponse } from '../../../../types/partner';

const AdminPartnerDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const { showSnackbar } = useSnackbar();

  const [partner, setPartner] = React.useState<PartnerResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [actionLoading, setActionLoading] = React.useState(false);

  const [confirmDialog, setConfirmDialog] = React.useState<{ open: boolean; title: string; message: string; action: () => void; color?: 'error' | 'warning' | 'primary' }>({ open: false, title: '', message: '', action: () => {} });
  const [reasonDialog, setReasonDialog] = React.useState<{ open: boolean; title: string; action: (reason: string) => Promise<void> } | null>(null);
  const [reasonText, setReasonText] = React.useState('');

  const [documents, setDocuments] = React.useState<PartnerDocumentResponse[]>([]);
  const [documentsError, setDocumentsError] = React.useState<string | null>(null);

  const fetchPartner = React.useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const result = await getPartner(Number(id));
      setPartner(result);
    } catch (err: any) {
      setError(err?.message || 'Không thể tải chi tiết đối tác');
    } finally {
      setLoading(false);
    }
  }, [id]);

  const fetchDocuments = React.useCallback(async () => {
    if (!id) return;
    try {
      const { getPartnerDocumentsForReview } = await import('../../../api/AdminRequest');
      const result = await getPartnerDocumentsForReview(Number(id));
      setDocuments(result);
    } catch (err: any) {
      setDocumentsError(err?.message || 'Không thể tải tài liệu');
    }
  }, [id]);

  React.useEffect(() => {
    fetchPartner();
    fetchDocuments();
  }, [fetchPartner, fetchDocuments]);

  const openConfirm = (title: string, message: string, action: () => Promise<void>, color?: 'error' | 'warning' | 'primary') => {
    setConfirmDialog({
      open: true,
      title,
      message,
      action: async () => {
        setActionLoading(true);
        try {
          await action();
          showSnackbar('Thao tác thành công', 'success');
          fetchPartner();
        } catch (err: any) {
          showSnackbar(err?.message || 'Thao tác thất bại', 'error');
        } finally {
          setActionLoading(false);
          setConfirmDialog(prev => ({ ...prev, open: false }));
        }
      },
      color,
    });
  };

  const openReasonDialog = (title: string, action: (reason: string) => Promise<void>) => {
    setReasonText('');
    setReasonDialog({ open: true, title, action });
  };

  const handleReasonSubmit = async () => {
    if (!reasonDialog || !reasonText.trim()) return;
    setActionLoading(true);
    try {
      await reasonDialog.action(reasonText.trim());
      showSnackbar('Thao tác thành công', 'success');
      setReasonDialog(null);
      fetchPartner();
    } catch (err: any) {
      showSnackbar(err?.message || 'Thao tác thất bại', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <AppShell>
        <PageHeader title="Đang tải..." breadcrumbs={[{ label: 'Đối tác', href: '/admin/partners' }, { label: '...' }]} />
        <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', p: 3 }}>
          <Skeleton variant="text" width="60%" height={40} />
          <Skeleton variant="text" width="40%" />
          <Skeleton variant="rectangular" height={200} sx={{ mt: 2 }} />
        </Card>
      </AppShell>
    );
  }

  if (error || !partner) {
    return (
      <AppShell>
        <PageHeader title="Chi tiết đối tác" breadcrumbs={[{ label: 'Đối tác', href: '/admin/partners' }, { label: 'Lỗi' }]} />
        <ErrorState message={error || 'Không tìm thấy đối tác'} onRetry={fetchPartner} />
      </AppShell>
    );
  }

  const status = partner.status;
  const bankAccounts: PartnerBankAccountResponse[] = [];
  const members: PartnerMemberResponse[] = [];

  return (
    <AppShell>
      <PageHeader
        title={partner.businessName}
        subtitle={`${partner.code} · ${partner.email}`}
        breadcrumbs={[{ label: 'Đối tác', href: '/admin/partners' }, { label: partner.businessName }]}
      />

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', mb: 3 }}>
            <CardHeader title="Thông tin hồ sơ" />
            <CardContent>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">Tên</Typography>
                  <Typography>{partner.name}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">Tên doanh nghiệp</Typography>
                  <Typography>{partner.businessName}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">Mã số thuế</Typography>
                  <Typography>{partner.taxCode || '-'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">Email</Typography>
                  <Typography>{partner.email}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">Số điện thoại</Typography>
                  <Typography>{partner.phone || '-'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">Địa chỉ</Typography>
                  <Typography>{partner.address || '-'}</Typography>
                </Grid>
              </Grid>
            </CardContent>
          </Card>

          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', mb: 3 }}>
            <CardHeader title="Tài liệu" />
            <CardContent>
              {documentsError ? (
                <Typography color="error">{documentsError}</Typography>
              ) : documents.length === 0 ? (
                <Typography color="text.secondary">Chưa có tài liệu</Typography>
              ) : (
                <List disablePadding>
                  {documents.map((doc) => (
                    <React.Fragment key={doc.id}>
                      <ListItem sx={{ px: 0 }}>
                        <ListItemText
                          primary={doc.documentType}
                          secondary={`${doc.originalFileName} · ${(doc.fileSize / 1024).toFixed(1)} KB`}
                        />
                        <StatusBadge status={doc.status} size="small" />
                      </ListItem>
                      <Divider />
                    </React.Fragment>
                  ))}
                </List>
              )}
            </CardContent>
          </Card>

          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', mb: 3 }}>
            <CardHeader title="Tài khoản ngân hàng" />
            <CardContent>
              {bankAccounts.length === 0 ? (
                <Typography color="text.secondary">Chưa đăng ký tài khoản ngân hàng</Typography>
              ) : (
                <List disablePadding>
                  {bankAccounts.map((acc) => (
                    <React.Fragment key={acc.id}>
                      <ListItem sx={{ px: 0 }}>
                        <ListItemText primary={acc.bankName} secondary={`${acc.accountName} · ${acc.maskedAccountNumber}`} />
                        <StatusBadge status={acc.status} size="small" />
                      </ListItem>
                      <Divider />
                    </React.Fragment>
                  ))}
                </List>
              )}
            </CardContent>
          </Card>

          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <CardHeader title="Thành viên" />
            <CardContent>
              {members.length === 0 ? (
                <Typography color="text.secondary">Chưa có thành viên</Typography>
              ) : (
                <List disablePadding>
                  {members.map((m) => (
                    <React.Fragment key={m.id}>
                      <ListItem sx={{ px: 0 }}>
                        <ListItemText primary={m.username} secondary={`${m.role} · ${m.email}`} />
                        <StatusBadge status={m.status} size="small" />
                      </ListItem>
                      <Divider />
                    </React.Fragment>
                  ))}
                </List>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', mb: 3 }}>
            <CardHeader title="Trạng thái" />
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <StatusBadge status={status} />
              </Box>
              <Typography variant="body2" color="text.secondary">Ngày tạo</Typography>
              <Typography sx={{ mb: 1 }}>{new Date(partner.createdAt).toLocaleString('vi-VN')}</Typography>
              <Typography variant="body2" color="text.secondary">Cập nhật</Typography>
              <Typography sx={{ mb: 1 }}>{new Date(partner.updatedAt).toLocaleString('vi-VN')}</Typography>
              {partner.approvedAt && (
                <>
                  <Typography variant="body2" color="text.secondary">Đã duyệt</Typography>
                  <Typography sx={{ mb: 1 }}>{new Date(partner.approvedAt).toLocaleString('vi-VN')}</Typography>
                </>
              )}
              {partner.rejectionReason && (
                <>
                  <Typography variant="body2" color="text.secondary">Lý do từ chối</Typography>
                  <Typography color="error">{partner.rejectionReason}</Typography>
                </>
              )}
            </CardContent>
          </Card>

          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <CardHeader title="Thao tác" />
            <CardContent>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                {status === 'PENDING_REVIEW' && (
                  <>
                    <Button variant="contained" color="success" onClick={() => openConfirm('Approve', `Approve ${partner.businessName}?`, () => approvePartner(partner.id))}>Approve</Button>
                    <Button variant="outlined" color="warning" onClick={() => openReasonDialog('Request Changes', (reason) => requestPartnerChanges(partner.id, reason))}>Request Changes</Button>
                    <Button variant="outlined" color="error" onClick={() => openReasonDialog('Reject', (reason) => rejectPartner(partner.id, reason))}>Reject</Button>
                  </>
                )}
                {status === 'APPROVED' && (
                  <Button variant="outlined" color="warning" onClick={() => openReasonDialog('Suspend', (reason) => suspendPartner(partner.id, reason))}>Suspend</Button>
                )}
                {status === 'SUSPENDED' && (
                  <>
                    <Button variant="contained" color="success" onClick={() => openConfirm('Restore', `Restore ${partner.businessName}?`, () => restorePartner(partner.id))}>Restore</Button>
                    <Button variant="outlined" color="error" onClick={() => openReasonDialog('Terminate', (reason) => terminatePartner(partner.id, reason))}>Terminate</Button>
                  </>
                )}
                {status === 'CHANGES_REQUESTED' && (
                  <>
                    <Button variant="contained" color="success" onClick={() => openConfirm('Approve', `Approve ${partner.businessName} after changes?`, () => approvePartner(partner.id))}>Approve</Button>
                    <Button variant="outlined" color="warning" onClick={() => openReasonDialog('Request Changes', (reason) => requestPartnerChanges(partner.id, reason))}>Request Changes</Button>
                    <Button variant="outlined" color="error" onClick={() => openReasonDialog('Reject', (reason) => rejectPartner(partner.id, reason))}>Reject</Button>
                  </>
                )}
                {status === 'REJECTED' && (
                  <Typography variant="body2" color="text.secondary">Partner has been rejected. No further actions available.</Typography>
                )}
                {status === 'TERMINATED' && (
                  <Typography variant="body2" color="text.secondary">Partner has been terminated. No further actions available.</Typography>
                )}
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <ConfirmDialog
        open={confirmDialog.open}
        title={confirmDialog.title}
        message={confirmDialog.message}
        confirmColor={confirmDialog.color}
        confirmLabel={confirmDialog.title}
        loading={actionLoading}
        onConfirm={confirmDialog.action}
        onCancel={() => setConfirmDialog(prev => ({ ...prev, open: false }))}
      />

      <Dialog open={!!reasonDialog} onClose={() => !actionLoading && setReasonDialog(null)} maxWidth="sm" fullWidth>
        <DialogTitle>{reasonDialog?.title}</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            multiline
            rows={3}
            label="Reason"
            value={reasonText}
            onChange={(e) => setReasonText(e.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReasonDialog(null)} disabled={actionLoading}>Cancel</Button>
          <Button onClick={handleReasonSubmit} variant="contained" disabled={actionLoading || !reasonText.trim()}>
            {actionLoading ? 'Processing...' : 'Submit'}
          </Button>
        </DialogActions>
      </Dialog>
    </AppShell>
  );
};

export default AdminPartnerDetailPage;
