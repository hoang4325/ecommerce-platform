import React from 'react';
import { useNavigate } from 'react-router-dom';
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
  Autocomplete,
} from '@mui/material';
import { AppShell, PageHeader } from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import { createOffer } from '../../../api/PartnerOfferRequest';
import { getProducts } from '../../../api/ProductRequest';

interface ProductOption {
  id: number;
  name: string;
}

interface FormData {
  productId: number | null;
  partnerSku: string;
  price: string;
  currency: string;
  onHandQuantity: string;
}

interface FormErrors {
  productId?: string;
  partnerSku?: string;
  price?: string;
  currency?: string;
  onHandQuantity?: string;
}

const PartnerOfferNewPage = () => {
  const navigate = useNavigate();
  const { showSnackbar } = useSnackbar();

  const [products, setProducts] = React.useState<ProductOption[]>([]);
  const [productsLoading, setProductsLoading] = React.useState(true);
  const [submitting, setSubmitting] = React.useState(false);
  const [error, setError] = React.useState('');

  const [formData, setFormData] = React.useState<FormData>({
    productId: null,
    partnerSku: '',
    price: '',
    currency: 'VND',
    onHandQuantity: '',
  });
  const [errors, setErrors] = React.useState<FormErrors>({});

  React.useEffect(() => {
    loadProducts();
  }, []);

  const loadProducts = async () => {
    try {
      const res = await getProducts(0, 100);
      const data = res.data || res;
      setProducts(Array.isArray(data) ? data.map((p: any) => ({ id: p.id, name: p.name })) : []);
    } catch {
      setError('Không tải được danh sách sản phẩm');
    } finally {
      setProductsLoading(false);
    }
  };

  const validate = (): boolean => {
    const newErrors: FormErrors = {};
    if (!formData.productId) newErrors.productId = 'Vui lòng chọn sản phẩm';
    if (!formData.partnerSku.trim()) newErrors.partnerSku = 'Vui lòng nhập SKU của shop';
    if (!formData.price || Number(formData.price) <= 0) newErrors.price = 'Giá phải lớn hơn 0';
    if (!formData.currency) newErrors.currency = 'Vui lòng chọn tiền tệ';
    if (formData.onHandQuantity === '' || Number(formData.onHandQuantity) < 0) newErrors.onHandQuantity = 'Tồn kho phải lớn hơn hoặc bằng 0';
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setError('');
    setSubmitting(true);
    try {
      await createOffer({
        productId: formData.productId!,
        partnerSku: formData.partnerSku.trim(),
        price: Number(formData.price),
        currency: formData.currency,
        onHandQuantity: Number(formData.onHandQuantity),
      });
      showSnackbar('Đã thêm sản phẩm', 'success');
      navigate('/partner/offers');
    } catch (err: any) {
      setError(err?.message || 'Không thêm được sản phẩm');
    } finally {
      setSubmitting(false);
    }
  };

  const handleChange = (field: keyof FormData) => (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>
  ) => {
    setFormData(prev => ({ ...prev, [field]: e.target.value }));
    if (errors[field as keyof FormErrors]) setErrors(prev => ({ ...prev, [field]: undefined }));
  };

  return (
    <AppShell>
      <PageHeader
        title="Thêm sản phẩm"
        subtitle="Chọn sản phẩm gốc rồi thiết lập SKU, giá bán và tồn kho của shop"
        breadcrumbs={[
          { label: 'Đối tác', href: '/partner/dashboard' },
          { label: 'Sản phẩm', href: '/partner/offers' },
          { label: 'Thêm mới' },
        ]}
      />

      <Paper elevation={0} sx={{ p: 4, borderRadius: 2, border: 1, borderColor: 'divider', maxWidth: 720 }}>
        {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}

        <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Thông tin sản phẩm</Typography>
        <Grid container spacing={2.5} sx={{ mb: 4 }}>
          <Grid item xs={12}>
            <Autocomplete
              options={products}
              loading={productsLoading}
              getOptionLabel={opt => `${opt.name} (ID: ${opt.id})`}
              onChange={(_, val) => {
                setFormData(prev => ({ ...prev, productId: val?.id ?? null }));
                if (errors.productId) setErrors(prev => ({ ...prev, productId: undefined }));
              }}
              renderInput={params => (
                <TextField
                  {...params}
                  label="Sản phẩm gốc"
                  error={!!errors.productId}
                  helperText={errors.productId}
                  required
                />
              )}
            />
          </Grid>
        </Grid>

        <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Thông tin bán hàng</Typography>
        <Grid container spacing={2.5}>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              label="SKU của shop"
              value={formData.partnerSku}
              onChange={handleChange('partnerSku')}
              error={!!errors.partnerSku}
              helperText={errors.partnerSku}
              required
            />
          </Grid>
          <Grid item xs={12} sm={3}>
            <TextField
              fullWidth
              label="Giá bán"
              type="number"
              value={formData.price}
              onChange={handleChange('price')}
              error={!!errors.price}
              helperText={errors.price}
              inputProps={{ min: 0, step: 0.01 }}
              required
            />
          </Grid>
          <Grid item xs={12} sm={3}>
            <FormControl fullWidth error={!!errors.currency}>
              <InputLabel>Tiền tệ</InputLabel>
              <Select
                value={formData.currency}
                label="Tiền tệ"
                onChange={e => setFormData(prev => ({ ...prev, currency: e.target.value }))}
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
              value={formData.onHandQuantity}
              onChange={handleChange('onHandQuantity')}
              error={!!errors.onHandQuantity}
              helperText={errors.onHandQuantity}
              inputProps={{ min: 0 }}
              required
            />
          </Grid>
        </Grid>

        <Box sx={{ mt: 4, display: 'flex', gap: 2 }}>
          <Button
            variant="contained"
            size="large"
            onClick={handleSubmit}
            disabled={submitting}
            startIcon={submitting ? <CircularProgress size={20} /> : null}
          >
            {submitting ? 'Đang thêm...' : 'Thêm sản phẩm'}
          </Button>
          <Button variant="outlined" size="large" onClick={() => navigate('/partner/offers')}>
            Hủy
          </Button>
        </Box>
      </Paper>
    </AppShell>
  );
};

export default PartnerOfferNewPage;
