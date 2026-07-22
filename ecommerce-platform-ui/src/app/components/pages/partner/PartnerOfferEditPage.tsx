import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Paper,
  TextField,
  Button,
  Grid,
  Typography,
  Alert,
  CircularProgress,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Stack,
} from '@mui/material';
import {
  AppShell,
  PageHeader,
  StatusBadge,
  SkeletonTable,
  ErrorState,
} from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import {
  getPartnerOffer,
  updateOffer,
  submitOffer,
  archiveOffer,
  adjustInventory,
} from '../../../api/PartnerOfferRequest';
import type {
  PartnerOfferResponse,
  UpdateOfferRequest,
} from '../../../../types/partner';

const PartnerOfferEditPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { showSnackbar } = useSnackbar();

  const [offer, setOffer] = React.useState<PartnerOfferResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState('');
  const [saving, setSaving] = React.useState(false);
  const [actionLoading, setActionLoading] = React.useState(false);

  // Edit form state
  const [editData, setEditData] = React.useState<UpdateOfferRequest>({});
  const [inventoryDialogOpen, setInventoryDialogOpen] = React.useState(false);
  const [inventoryDelta, setInventoryDelta] = React.useState('');
  const [inventoryReason, setInventoryReason] = React.useState('');
  const [inventoryLoading, setInventoryLoading] = React.useState(false);

  const isEditable = offer && ['DRAFT', 'PENDING_REVIEW', 'REJECTED', 'SUSPENDED', 'APPROVED'].includes(offer.status);

  const fetchOffer = React.useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError('');
    try {
      const res = await getPartnerOffer(Number(id));
      setOffer(res);
      setEditData({
        productId: res.productId,
        partnerSku: res.partnerSku,
        price: res.price,
        currency: res.currency,
        onHandQuantity: res.onHandQuantity,
      });
    } catch {
      setError('Không tải được sản phẩm');
    } finally {
      setLoading(false);
    }
  }, [id]);

  React.useEffect(() => { fetchOffer(); }, [fetchOffer]);

  const handleSave = async () => {
    if (!offer) return;
    setSaving(true);
    try {
      const updated = await updateOffer(offer.id, editData);
      setOffer(updated);
      showSnackbar('Đã cập nhật sản phẩm', 'success');
    } catch {
      showSnackbar('Không cập nhật được sản phẩm', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleSubmit = async () => {
    if (!offer) return;
    setActionLoading(true);
    try {
      const updated = await submitOffer(offer.id);
      setOffer(updated);
      showSnackbar('Đã gửi sản phẩm để kiểm duyệt', 'success');
    } catch {
      showSnackbar('Không gửi duyệt được sản phẩm', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleArchive = async () => {
    if (!offer) return;
    setActionLoading(true);
    try {
      await archiveOffer(offer.id);
      showSnackbar('Đã lưu trữ sản phẩm', 'success');
      navigate('/partner/offers');
    } catch {
      showSnackbar('Không lưu trữ được sản phẩm', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleInventoryAdjust = async () => {
    if (!offer) return;
    const delta = Number(inventoryDelta);
    if (isNaN(delta) || delta === 0) {
      showSnackbar('Số lượng điều chỉnh phải khác 0', 'error');
      return;
    }
    if (!inventoryReason.trim()) {
      showSnackbar('Vui lòng nhập lý do điều chỉnh', 'error');
      return;
    }
    setInventoryLoading(true);
    try {
      const idempotencyKey = Date.now().toString() + Math.random().toString(36);
      const updated = await adjustInventory(offer.id, {
        delta,
        reason: inventoryReason.trim(),
        idempotencyKey,
      });
      setOffer(updated);
      setInventoryDialogOpen(false);
      setInventoryDelta('');
      setInventoryReason('');
      showSnackbar('Đã điều chỉnh tồn kho', 'success');
    } catch {
      showSnackbar('Không điều chỉnh được tồn kho', 'error');
    } finally {
      setInventoryLoading(false);
    }
  };

  const formatCurrency = (amount: number, currency: string) =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency: currency || 'VND', maximumFractionDigits: currency === 'VND' ? 0 : 2 }).format(amount);

  const formatDate = (dateStr: string) =>
    new Date(dateStr).toLocaleString('vi-VN', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

  if (loading) {
    return (
      <AppShell>
        <PageHeader title="Chi tiết sản phẩm" />
        <SkeletonTable rows={6} columns={2} />
      </AppShell>
    );
  }

  if (error || !offer) {
    return (
      <AppShell>
        <PageHeader title="Chi tiết sản phẩm" />
        <ErrorState message={error || 'Không tìm thấy sản phẩm'} onRetry={fetchOffer} />
      </AppShell>
    );
  }

  return (
    <AppShell>
      <PageHeader
        title={`Sản phẩm #${offer.id}`}
        subtitle={offer.productName}
        breadcrumbs={[
          { label: 'Đối tác', href: '/partner/dashboard' },
          { label: 'Sản phẩm', href: '/partner/offers' },
          { label: `#${offer.id}` },
        ]}
        actions={
          <Stack direction="row" spacing={1}>
            {offer.status === 'DRAFT' && (
              <Button
                variant="contained"
                color="success"
                disabled={actionLoading}
                onClick={handleSubmit}
              >
                Gửi duyệt
              </Button>
            )}
            {offer.status === 'APPROVED' && (
              <>
                <Button
                  variant="outlined"
                  color="warning"
                  disabled={actionLoading}
                  onClick={handleArchive}
                >
                  Lưu trữ
                </Button>
                <Button
                  variant="contained"
                  onClick={() => setInventoryDialogOpen(true)}
                >
                  Chỉnh tồn kho
                </Button>
              </>
            )}
          </Stack>
        }
      />

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Paper elevation={0} sx={{ p: 3, borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>Thông tin bán hàng</Typography>
            {isEditable ? (
              <Grid container spacing={2.5}>
                <Grid item xs={12} sm={6}>
                  <TextField
                    fullWidth
                    label="SKU của shop"
                    value={editData.partnerSku || ''}
                    onChange={e => setEditData((prev: UpdateOfferRequest) => ({ ...prev, partnerSku: e.target.value }))}
                  />
                </Grid>
                <Grid item xs={12} sm={3}>
                  <TextField
                    fullWidth
                    label="Giá bán"
                    type="number"
                    value={editData.price || ''}
                    onChange={e => setEditData((prev: UpdateOfferRequest) => ({ ...prev, price: Number(e.target.value) }))}
                    inputProps={{ min: 0, step: 0.01 }}
                  />
                </Grid>
                <Grid item xs={12} sm={3}>
                  <FormControl fullWidth>
                    <InputLabel>Tiền tệ</InputLabel>
                    <Select
                      value={editData.currency || 'VND'}
                      label="Tiền tệ"
                      onChange={e => setEditData((prev: UpdateOfferRequest) => ({ ...prev, currency: e.target.value }))}
                    >
                      <MenuItem value="VND">VND</MenuItem>
                      <MenuItem value="USD">USD</MenuItem>
                      <MenuItem value="EUR">EUR</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField
                    fullWidth
                    label="Tồn kho"
                    type="number"
                    value={editData.onHandQuantity ?? ''}
                    onChange={e => setEditData((prev: UpdateOfferRequest) => ({ ...prev, onHandQuantity: Number(e.target.value) }))}
                    inputProps={{ min: 0 }}
                  />
                </Grid>
                <Grid item xs={12}>
                  <Button
                    variant="contained"
                    onClick={handleSave}
                    disabled={saving}
                    startIcon={saving ? <CircularProgress size={20} /> : null}
                  >
                    {saving ? 'Đang lưu...' : 'Lưu thay đổi'}
                  </Button>
                </Grid>
              </Grid>
            ) : (
              <Grid container spacing={2}>
                <Grid item xs={6}><Typography variant="body2" color="text.secondary">SKU</Typography><Typography>{offer.partnerSku}</Typography></Grid>
                <Grid item xs={6}><Typography variant="body2" color="text.secondary">Giá bán</Typography><Typography>{formatCurrency(offer.price, offer.currency)}</Typography></Grid>
                <Grid item xs={6}><Typography variant="body2" color="text.secondary">Tồn kho</Typography><Typography>{offer.onHandQuantity}</Typography></Grid>
                <Grid item xs={6}><Typography variant="body2" color="text.secondary">Đã giữ</Typography><Typography>{offer.reservedQuantity}</Typography></Grid>
              </Grid>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper elevation={0} sx={{ p: 3, borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Trạng thái</Typography>
            <StatusBadge status={offer.status} size="medium" />
            <Box sx={{ mt: 2 }}>
              <Typography variant="body2" color="text.secondary">Ngày tạo</Typography>
              <Typography variant="body2">{formatDate(offer.createdAt)}</Typography>
            </Box>
            {offer.submittedAt && (
              <Box sx={{ mt: 1 }}>
                <Typography variant="body2" color="text.secondary">Ngày gửi duyệt</Typography>
                <Typography variant="body2">{formatDate(offer.submittedAt)}</Typography>
              </Box>
            )}
            {offer.approvedAt && (
              <Box sx={{ mt: 1 }}>
                <Typography variant="body2" color="text.secondary">Ngày duyệt</Typography>
                <Typography variant="body2">{formatDate(offer.approvedAt)}</Typography>
              </Box>
            )}
            {offer.rejectionReason && (
              <Alert severity="error" sx={{ mt: 2 }}>
                {offer.rejectionReason}
              </Alert>
            )}
          </Paper>
        </Grid>
      </Grid>

      <Dialog open={inventoryDialogOpen} onClose={() => setInventoryDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Điều chỉnh tồn kho</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              fullWidth
              label="Số lượng thay đổi"
              type="number"
              value={inventoryDelta}
              onChange={e => setInventoryDelta(e.target.value)}
              helperText="Nhập số dương để tăng kho, số âm để giảm kho"
            />
            <TextField
              fullWidth
              label="Lý do"
              value={inventoryReason}
              onChange={e => setInventoryReason(e.target.value)}
              multiline
              rows={2}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setInventoryDialogOpen(false)}>Hủy</Button>
          <Button
            variant="contained"
            onClick={handleInventoryAdjust}
            disabled={inventoryLoading}
            startIcon={inventoryLoading ? <CircularProgress size={20} /> : null}
          >
            Điều chỉnh
          </Button>
        </DialogActions>
      </Dialog>
    </AppShell>
  );
};

export default PartnerOfferEditPage;
