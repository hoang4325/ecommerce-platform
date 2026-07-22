import React from 'react';
import { useParams } from 'react-router-dom';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Grid from '@mui/material/Grid';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import Skeleton from '@mui/material/Skeleton';
import { AppShell, PageHeader, StatusBadge, ErrorState, ConfirmDialog } from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import { getCommissionRule, updateCommissionRule, activateCommissionRule, deactivateCommissionRule, expireCommissionRule } from '../../../api/AdminRequest';
import type { CommissionRuleResponse, UpdateCommissionRuleRequest } from '../../../../types/partner';

const AdminCommissionRuleEditPage = () => {
  const { id } = useParams<{ id: string }>();
  const { showSnackbar } = useSnackbar();

  const [rule, setRule] = React.useState<CommissionRuleResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [submitting, setSubmitting] = React.useState(false);
  const [actionLoading, setActionLoading] = React.useState(false);

  const [name, setName] = React.useState('');
  const [rate, setRate] = React.useState('');
  const [fixedFee, setFixedFee] = React.useState('');
  const [currency, setCurrency] = React.useState('VND');
  const [priority, setPriority] = React.useState('0');
  const [validFrom, setValidFrom] = React.useState('');
  const [validTo, setValidTo] = React.useState('');
  const [errors, setErrors] = React.useState<Record<string, string>>({});

  const [confirmDialog, setConfirmDialog] = React.useState<{ open: boolean; title: string; message: string; action: () => void; color?: 'error' | 'warning' | 'primary' }>({ open: false, title: '', message: '', action: () => {} });

  const fetchRule = React.useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const result = await getCommissionRule(Number(id));
      setRule(result);
      setName(result.name);
      setRate(String(Number(result.rate || 0) * 100));
      setFixedFee(result.fixedFee ? String(result.fixedFee) : '');
      setCurrency(result.currency);
      setPriority(String(result.priority));
      setValidFrom(result.validFrom.split('T')[0]);
      setValidTo(result.validTo ? result.validTo.split('T')[0] : '');
    } catch (err: any) {
      setError(err?.message || 'Không tải được quy tắc hoa hồng');
    } finally {
      setLoading(false);
    }
  }, [id]);

  React.useEffect(() => {
    fetchRule();
  }, [fetchRule]);

  const isEditable = rule && (rule.status === 'DRAFT' || rule.status === 'INACTIVE');

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
          fetchRule();
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

  const validate = (): boolean => {
    const errs: Record<string, string> = {};
    if (!name.trim()) errs.name = 'Vui lòng nhập tên quy tắc';
    if (!rate || Number(rate) < 0 || Number(rate) > 100) errs.rate = 'Tỷ lệ phải nằm trong khoảng 0 đến 100';
    if (fixedFee && Number(fixedFee) < 0) errs.fixedFee = 'Phí cố định không được âm';
    if (!validFrom) errs.validFrom = 'Vui lòng chọn ngày bắt đầu';
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSave = async () => {
    if (!validate() || !rule) return;
    setSubmitting(true);
    try {
      const data: UpdateCommissionRuleRequest = {
        name: name.trim(),
        rate: Number(rate) / 100,
        fixedFee: fixedFee ? Number(fixedFee) : undefined,
        currency,
        priority: Number(priority),
        validFrom: new Date(validFrom).toISOString(),
        validTo: validTo ? new Date(validTo).toISOString() : undefined,
      };
      await updateCommissionRule(rule.id, data);
      showSnackbar('Đã cập nhật quy tắc hoa hồng', 'success');
      fetchRule();
    } catch (err: any) {
      showSnackbar(err?.message || 'Không cập nhật được quy tắc hoa hồng', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <AppShell>
        <PageHeader title="Đang tải..." breadcrumbs={[{ label: 'Hoa hồng', href: '/admin/commission-rules' }, { label: '...' }]} />
        <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', p: 3 }}>
          <Skeleton variant="text" width="60%" height={40} />
          <Skeleton variant="rectangular" height={300} sx={{ mt: 2 }} />
        </Card>
      </AppShell>
    );
  }

  if (error || !rule) {
    return (
      <AppShell>
        <PageHeader title="Quy tắc hoa hồng" breadcrumbs={[{ label: 'Hoa hồng', href: '/admin/commission-rules' }, { label: 'Lỗi' }]} />
        <ErrorState message={error || 'Không tìm thấy quy tắc'} onRetry={fetchRule} />
      </AppShell>
    );
  }

  const scope = rule.partnerId ? 'Đối tác' : rule.categoryId ? 'Danh mục' : rule.productId ? 'Sản phẩm' : 'Toàn sàn';

  return (
    <AppShell>
      <PageHeader
        title={rule.name}
        subtitle={`Trạng thái: ${rule.status}`}
        breadcrumbs={[{ label: 'Hoa hồng', href: '/admin/commission-rules' }, { label: rule.name }]}
      />

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <CardHeader title={isEditable ? 'Chỉnh sửa quy tắc' : 'Thông tin quy tắc'} />
            <CardContent>
              <Grid container spacing={2}>
                <Grid item xs={12}>
                  <TextField fullWidth label="Tên quy tắc" value={name} onChange={(e) => setName(e.target.value)} error={!!errors.name} helperText={errors.name} disabled={!isEditable} />
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField fullWidth label="Phạm vi" value={scope} disabled />
                </Grid>
                {rule.partnerId && (
                  <Grid item xs={12} sm={4}>
                    <TextField fullWidth label="ID đối tác" value={rule.partnerId} disabled />
                  </Grid>
                )}
                {rule.categoryId && (
                  <Grid item xs={12} sm={4}>
                    <TextField fullWidth label="ID danh mục" value={rule.categoryId} disabled />
                  </Grid>
                )}
                {rule.productId && (
                  <Grid item xs={12} sm={4}>
                    <TextField fullWidth label="ID sản phẩm" value={rule.productId} disabled />
                  </Grid>
                )}
                <Grid item xs={12} sm={4}>
                  <TextField fullWidth label="Tỷ lệ hoa hồng (%)" type="number" value={rate} onChange={(e) => setRate(e.target.value)} error={!!errors.rate} helperText={errors.rate || 'Nhập từ 0 đến 100'} disabled={!isEditable} inputProps={{ min: 0, max: 100, step: 0.01 }} />
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField fullWidth label="Phí cố định" type="number" value={fixedFee} onChange={(e) => setFixedFee(e.target.value)} error={!!errors.fixedFee} helperText={errors.fixedFee || 'Không bắt buộc'} disabled={!isEditable} inputProps={{ min: 0, step: 0.01 }} />
                </Grid>
                <Grid item xs={12} sm={4}>
                  <FormControl fullWidth>
                    <InputLabel>Tiền tệ</InputLabel>
                    <Select value={currency} label="Tiền tệ" onChange={(e) => setCurrency(e.target.value)} disabled={!isEditable}>
                      <MenuItem value="VND">VND</MenuItem>
                      <MenuItem value="USD">USD</MenuItem>
                      <MenuItem value="EUR">EUR</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField fullWidth label="Độ ưu tiên" type="number" value={priority} onChange={(e) => setPriority(e.target.value)} disabled={!isEditable} />
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField fullWidth label="Áp dụng từ" type="date" InputLabelProps={{ shrink: true }} value={validFrom} onChange={(e) => setValidFrom(e.target.value)} error={!!errors.validFrom} helperText={errors.validFrom} disabled={!isEditable} />
                </Grid>
                <Grid item xs={12} sm={4}>
                  <TextField fullWidth label="Áp dụng đến" type="date" InputLabelProps={{ shrink: true }} value={validTo} onChange={(e) => setValidTo(e.target.value)} helperText="Không bắt buộc" disabled={!isEditable} />
                </Grid>
              </Grid>

              {isEditable && (
                <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end', mt: 3 }}>
                  <Button variant="outlined" onClick={() => fetchRule()} disabled={submitting}>Hoàn tác</Button>
                  <Button variant="contained" onClick={handleSave} disabled={submitting}>
                    {submitting ? 'Đang lưu...' : 'Lưu thay đổi'}
                  </Button>
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', mb: 3 }}>
            <CardHeader title="Trạng thái" />
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <StatusBadge status={rule.status} />
              </Box>
              <Typography variant="body2" color="text.secondary">Ngày tạo</Typography>
              <Typography sx={{ mb: 1 }}>{new Date(rule.createdAt).toLocaleString()}</Typography>
              <Typography variant="body2" color="text.secondary">Cập nhật</Typography>
              <Typography sx={{ mb: 1 }}>{new Date(rule.updatedAt).toLocaleString()}</Typography>
              <Typography variant="body2" color="text.secondary">Độ ưu tiên</Typography>
              <Typography sx={{ mb: 1 }}>{rule.priority}</Typography>
            </CardContent>
          </Card>

          <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <CardHeader title="Thao tác" />
            <CardContent>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                {rule.status === 'DRAFT' && (
                  <Button variant="contained" onClick={() => openConfirm('Kích hoạt', `Kích hoạt quy tắc "${rule.name}"?`, () => activateCommissionRule(rule.id))}>Kích hoạt</Button>
                )}
                {rule.status === 'ACTIVE' && (
                  <>
                    <Button variant="outlined" color="warning" onClick={() => openConfirm('Tạm dừng', `Tạm dừng quy tắc "${rule.name}"?`, () => deactivateCommissionRule(rule.id))}>Tạm dừng</Button>
                    <Button variant="outlined" color="error" onClick={() => openConfirm('Hết hạn', `Cho quy tắc "${rule.name}" hết hạn?`, () => expireCommissionRule(rule.id))}>Hết hạn</Button>
                  </>
                )}
                {rule.status === 'INACTIVE' && (
                  <>
                    <Button variant="contained" onClick={() => openConfirm('Kích hoạt', `Kích hoạt quy tắc "${rule.name}"?`, () => activateCommissionRule(rule.id))}>Kích hoạt</Button>
                    <Button variant="outlined" color="error" onClick={() => openConfirm('Hết hạn', `Cho quy tắc "${rule.name}" hết hạn?`, () => expireCommissionRule(rule.id))}>Hết hạn</Button>
                  </>
                )}
                {rule.status === 'EXPIRED' && (
                  <Typography variant="body2" color="text.secondary">Quy tắc này đã hết hạn.</Typography>
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
    </AppShell>
  );
};

export default AdminCommissionRuleEditPage;
