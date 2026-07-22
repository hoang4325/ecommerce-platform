import React from 'react';
import { useNavigate } from 'react-router-dom';
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
import { AppShell, PageHeader } from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import { createCommissionRule } from '../../../api/AdminRequest';
import type { CreateCommissionRuleRequest } from '../../../../types/partner';

const AdminCommissionRuleNewPage = () => {
  const navigate = useNavigate();
  const { showSnackbar } = useSnackbar();

  const [name, setName] = React.useState('');
  const [scope, setScope] = React.useState<'Global' | 'Partner' | 'Category' | 'Product'>('Global');
  const [partnerId, setPartnerId] = React.useState('');
  const [categoryId, setCategoryId] = React.useState('');
  const [productId, setProductId] = React.useState('');
  const [rate, setRate] = React.useState('');
  const [fixedFee, setFixedFee] = React.useState('');
  const [currency, setCurrency] = React.useState('VND');
  const [priority, setPriority] = React.useState('0');
  const [validFrom, setValidFrom] = React.useState('');
  const [validTo, setValidTo] = React.useState('');
  const [submitting, setSubmitting] = React.useState(false);
  const [errors, setErrors] = React.useState<Record<string, string>>({});

  const validate = (): boolean => {
    const errs: Record<string, string> = {};
    if (!name.trim()) errs.name = 'Vui lòng nhập tên quy tắc';
    if (!rate || Number(rate) < 0 || Number(rate) > 100) errs.rate = 'Tỷ lệ phải nằm trong khoảng 0 đến 100';
    if (fixedFee && Number(fixedFee) < 0) errs.fixedFee = 'Phí cố định không được âm';
    if (!validFrom) errs.validFrom = 'Vui lòng chọn ngày bắt đầu';
    if (scope === 'Partner' && !partnerId) errs.partnerId = 'Vui lòng nhập ID đối tác';
    if (scope === 'Category' && !categoryId) errs.categoryId = 'Vui lòng nhập ID danh mục';
    if (scope === 'Product' && !productId) errs.productId = 'Vui lòng nhập ID sản phẩm';
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSubmitting(true);
    try {
      const data: CreateCommissionRuleRequest = {
        name: name.trim(),
        rate: Number(rate) / 100,
        fixedFee: fixedFee ? Number(fixedFee) : undefined,
        currency,
        priority: Number(priority),
        validFrom: new Date(validFrom).toISOString(),
        validTo: validTo ? new Date(validTo).toISOString() : undefined,
      };
      if (scope === 'Partner') data.partnerId = Number(partnerId);
      if (scope === 'Category') data.categoryId = Number(categoryId);
      if (scope === 'Product') data.productId = Number(productId);

      await createCommissionRule(data);
      showSnackbar('Đã tạo quy tắc hoa hồng', 'success');
      navigate('/admin/commission-rules');
    } catch (err: any) {
      showSnackbar(err?.message || 'Không tạo được quy tắc hoa hồng', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AppShell>
      <PageHeader
        title="Tạo quy tắc hoa hồng"
        breadcrumbs={[{ label: 'Hoa hồng', href: '/admin/commission-rules' }, { label: 'Tạo mới' }]}
      />

      <Card elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider', maxWidth: 800 }}>
        <CardHeader title="Thông tin quy tắc" />
        <CardContent>
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField fullWidth label="Tên quy tắc" value={name} onChange={(e) => setName(e.target.value)} error={!!errors.name} helperText={errors.name} />
            </Grid>
            <Grid item xs={12} sm={4}>
              <FormControl fullWidth>
                <InputLabel>Phạm vi</InputLabel>
                <Select value={scope} label="Phạm vi" onChange={(e) => setScope(e.target.value as any)}>
                  <MenuItem value="Global">Toàn sàn</MenuItem>
                  <MenuItem value="Partner">Theo đối tác</MenuItem>
                  <MenuItem value="Category">Theo danh mục</MenuItem>
                  <MenuItem value="Product">Theo sản phẩm</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            {scope === 'Partner' && (
              <Grid item xs={12} sm={4}>
                <TextField fullWidth label="ID đối tác" type="number" value={partnerId} onChange={(e) => setPartnerId(e.target.value)} error={!!errors.partnerId} helperText={errors.partnerId} />
              </Grid>
            )}
            {scope === 'Category' && (
              <Grid item xs={12} sm={4}>
                <TextField fullWidth label="ID danh mục" type="number" value={categoryId} onChange={(e) => setCategoryId(e.target.value)} error={!!errors.categoryId} helperText={errors.categoryId} />
              </Grid>
            )}
            {scope === 'Product' && (
              <Grid item xs={12} sm={4}>
                <TextField fullWidth label="ID sản phẩm" type="number" value={productId} onChange={(e) => setProductId(e.target.value)} error={!!errors.productId} helperText={errors.productId} />
              </Grid>
            )}
            <Grid item xs={12} sm={4}>
              <TextField fullWidth label="Tỷ lệ hoa hồng (%)" type="number" value={rate} onChange={(e) => setRate(e.target.value)} error={!!errors.rate} helperText={errors.rate || 'Nhập từ 0 đến 100'} inputProps={{ min: 0, max: 100, step: 0.01 }} />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField fullWidth label="Phí cố định" type="number" value={fixedFee} onChange={(e) => setFixedFee(e.target.value)} error={!!errors.fixedFee} helperText={errors.fixedFee || 'Không bắt buộc'} inputProps={{ min: 0, step: 0.01 }} />
            </Grid>
            <Grid item xs={12} sm={4}>
              <FormControl fullWidth>
                <InputLabel>Tiền tệ</InputLabel>
                <Select value={currency} label="Tiền tệ" onChange={(e) => setCurrency(e.target.value)}>
                  <MenuItem value="VND">VND</MenuItem>
                  <MenuItem value="USD">USD</MenuItem>
                  <MenuItem value="EUR">EUR</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField fullWidth label="Độ ưu tiên" type="number" value={priority} onChange={(e) => setPriority(e.target.value)} />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField fullWidth label="Áp dụng từ" type="date" InputLabelProps={{ shrink: true }} value={validFrom} onChange={(e) => setValidFrom(e.target.value)} error={!!errors.validFrom} helperText={errors.validFrom} />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField fullWidth label="Áp dụng đến" type="date" InputLabelProps={{ shrink: true }} value={validTo} onChange={(e) => setValidTo(e.target.value)} helperText="Không bắt buộc" />
            </Grid>
          </Grid>

          <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end', mt: 3 }}>
            <Button variant="outlined" onClick={() => navigate('/admin/commission-rules')} disabled={submitting}>Hủy</Button>
            <Button variant="contained" onClick={handleSubmit} disabled={submitting}>
              {submitting ? 'Đang tạo...' : 'Tạo quy tắc'}
            </Button>
          </Box>
        </CardContent>
      </Card>
    </AppShell>
  );
};

export default AdminCommissionRuleNewPage;
