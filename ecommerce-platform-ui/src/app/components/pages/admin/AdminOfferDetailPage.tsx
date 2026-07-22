import React from 'react';
import { useParams } from 'react-router-dom';
import Grid from '@mui/material/Grid';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Skeleton from '@mui/material/Skeleton';
import { AppShell, PageHeader, StatusBadge, ErrorState, ConfirmDialog } from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import { approveOffer, rejectOffer, suspendOffer } from '../../../api/AdminRequest';
import type { PartnerOfferResponse } from '../../../../types/partner';

const AdminOfferDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const { showSnackbar } = useSnackbar();

  const [offer, setOffer] = React.useState<PartnerOfferResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [actionLoading, setActionLoading] = React.useState(false);

  const [confirmDialog, setConfirmDialog] = React.useState<{ open: boolean; title: string; message: string; action: () => void; color?: 'error' | 'warning' | 'primary' }>({ open: false, title: '', message: '', action: () => {} });
  const [reasonDialog, setReasonDialog] = React.useState<{ open: boolean; title: string; action: (reason: string) => Promise<void> } | null>(null);
  const [reasonText, setReasonText] = React.useState('');

  const fetchOffer = React.useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const { getOffers } = await import('../../../api/AdminRequest');
      const result = await getOffers({ page: 0, size: 100 });
      const found = result.data.find(o => o.id === Number(id));
      if (!found) throw new Error('Không tìm thấy ưu đãi');
      setOffer(found);
    } catch (err: any) {
      setError(err?.message || 'Không thể tải chi tiết ưu đãi');
    } finally {
      setLoading(false);
    }
  }, [id]);

  React.useEffect(() => {
    fetchOffer();
  }, [fetchOffer]);

  const openConfirm = (title: string, message: string, action: () => Promise<void>, color?: 'error' | 'warning' | 'primary') => {
    setConfirmDialog({
      open: true,
      title,
      message,
      action: async () => {
        setActionLoading(true);
        try {
          await action();
          showSnackbar(`${title} successful`, 'success');
          fetchOffer();
        } catch (err: any) {
          showSnackbar(err?.message || `Failed to ${title.toLowerCase()}`, 'error');
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
      showSnackbar(`${reasonDialog.title} successful`, 'success');
      setReasonDialog(null);
      fetchOffer();
    } catch (err: any) {
      showSnackbar(err?.message || `Failed to ${reasonDialog.title.toLowerCase()}`, 'error');
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <AppShell>
        <PageHeader title="Loading..." breadcrumbs={[{ label: 'Offers', href: '/admin/offers' }, { label: '...' }]} />
        <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', p: 3 }}>
          <Skeleton variant="text" width="60%" height={40} />
          <Skeleton variant="text" width="40%" />
          <Skeleton variant="rectangular" height={200} sx={{ mt: 2 }} />
        </Card>
      </AppShell>
    );
  }

  if (error || !offer) {
    return (
      <AppShell>
        <PageHeader title="Offer Detail" breadcrumbs={[{ label: 'Offers', href: '/admin/offers' }, { label: 'Error' }]} />
        <ErrorState message={error || 'Offer not found'} onRetry={fetchOffer} />
      </AppShell>
    );
  }

  const status = offer.status;

  return (
    <AppShell>
      <PageHeader
        title={offer.productName}
        subtitle={`SKU: ${offer.partnerSku} · Offer #${offer.id}`}
        breadcrumbs={[{ label: 'Offers', href: '/admin/offers' }, { label: `#${offer.id}` }]}
      />

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', mb: 3 }}>
            <CardHeader title="Product Information" />
            <CardContent>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">Product Name</Typography>
                  <Typography>{offer.productName}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">Partner SKU</Typography>
                  <Typography>{offer.partnerSku}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">Price</Typography>
                  <Typography>{offer.currency} {offer.price.toFixed(2)}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">On Hand Quantity</Typography>
                  <Typography>{offer.onHandQuantity}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">Reserved Quantity</Typography>
                  <Typography>{offer.reservedQuantity}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">Partner ID</Typography>
                  <Typography>{offer.partnerId}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">Submitted</Typography>
                  <Typography>{offer.submittedAt ? new Date(offer.submittedAt).toLocaleString() : '-'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">Approved</Typography>
                  <Typography>{offer.approvedAt ? new Date(offer.approvedAt).toLocaleString() : '-'}</Typography>
                </Grid>
              </Grid>
            </CardContent>
          </Card>

          {offer.rejectionReason && (
            <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
              <CardHeader title="Rejection Reason" />
              <CardContent>
                <Typography color="error">{offer.rejectionReason}</Typography>
              </CardContent>
            </Card>
          )}
        </Grid>

        <Grid item xs={12} md={4}>
          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', mb: 3 }}>
            <CardHeader title="Status" />
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <StatusBadge status={status} />
              </Box>
              <Typography variant="body2" color="text.secondary">Created</Typography>
              <Typography sx={{ mb: 1 }}>{new Date(offer.createdAt).toLocaleString()}</Typography>
              <Typography variant="body2" color="text.secondary">Updated</Typography>
              <Typography sx={{ mb: 1 }}>{new Date(offer.updatedAt).toLocaleString()}</Typography>
            </CardContent>
          </Card>

          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <CardHeader title="Moderation Actions" />
            <CardContent>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                {status === 'PENDING_REVIEW' && (
                  <>
                    <Button variant="contained" color="success" onClick={() => openConfirm('Approve', `Approve offer for ${offer.productName}?`, () => approveOffer(offer.id))}>Approve</Button>
                    <Button variant="outlined" color="error" onClick={() => openReasonDialog('Reject', (reason) => rejectOffer(offer.id, reason))}>Reject</Button>
                    <Button variant="outlined" color="warning" onClick={() => openReasonDialog('Suspend', (reason) => suspendOffer(offer.id, reason))}>Suspend</Button>
                  </>
                )}
                {status === 'APPROVED' && (
                  <>
                    <Button variant="outlined" color="warning" onClick={() => openReasonDialog('Suspend', (reason) => suspendOffer(offer.id, reason))}>Suspend</Button>
                    <Button variant="outlined" color="error" onClick={() => openReasonDialog('Reject', (reason) => rejectOffer(offer.id, reason))}>Reject</Button>
                  </>
                )}
                {status === 'SUSPENDED' && (
                  <Typography variant="body2" color="text.secondary">Offer is suspended. No actions available.</Typography>
                )}
                {status === 'REJECTED' && (
                  <Typography variant="body2" color="text.secondary">Offer has been rejected.</Typography>
                )}
                {status === 'DRAFT' && (
                  <Typography variant="body2" color="text.secondary">Offer is in draft state.</Typography>
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
          <TextField autoFocus fullWidth multiline rows={3} label="Reason" value={reasonText} onChange={(e) => setReasonText(e.target.value)} sx={{ mt: 1 }} />
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

export default AdminOfferDetailPage;
