import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Paper,
  TextField,
  Button,
  Alert,
  CircularProgress,
  Grid,
} from '@mui/material';
import { AppShell, PageHeader } from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import { applyAsPartner, getMyApplication } from '../../../api/PartnerRequest';
import type { PartnerApplicationRequest, PartnerResponse } from '../../../../types/partner';

interface FormErrors {
  businessName?: string;
  name?: string;
  taxCode?: string;
  email?: string;
  phone?: string;
  address?: string;
}

const PartnerApplicationPage = () => {
  const navigate = useNavigate();
  const { showSnackbar } = useSnackbar();

  const [loading, setLoading] = React.useState(true);
  const [submitting, setSubmitting] = React.useState(false);
  const [error, setError] = React.useState('');
  const [existingApp, setExistingApp] = React.useState<PartnerResponse | null>(null);

  const [formData, setFormData] = React.useState<PartnerApplicationRequest>({
    businessName: '',
    name: '',
    taxCode: '',
    email: '',
    phone: '',
    address: '',
  });
  const [errors, setErrors] = React.useState<FormErrors>({});

  React.useEffect(() => {
    checkExistingApplication();
  }, []);

  const checkExistingApplication = async () => {
    try {
      const app = await getMyApplication();
      setExistingApp(app);
    } catch {
      // No existing application, proceed to form
    } finally {
      setLoading(false);
    }
  };

  const validate = (): boolean => {
    const newErrors: FormErrors = {};

    if (!formData.businessName.trim()) newErrors.businessName = 'Business name is required';
    if (!formData.name.trim()) newErrors.name = 'Contact name is required';
    if (!formData.taxCode.trim()) newErrors.taxCode = 'Tax code is required';
    else if (!/^[A-Za-z0-9-]+$/.test(formData.taxCode)) newErrors.taxCode = 'Invalid tax code format';
    if (!formData.email.trim()) newErrors.email = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) newErrors.email = 'Invalid email format';
    if (!formData.phone.trim()) newErrors.phone = 'Phone is required';
    if (!formData.address.trim()) newErrors.address = 'Address is required';

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (field: keyof PartnerApplicationRequest) => (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    setFormData(prev => ({ ...prev, [field]: e.target.value }));
    if (errors[field as keyof FormErrors]) setErrors((prev: FormErrors) => ({ ...prev, [field]: undefined }));
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setError('');
    setSubmitting(true);
    try {
      await applyAsPartner(formData);
      showSnackbar('Partner application submitted successfully', 'success');
      navigate('/partner/dashboard');
    } catch (err: any) {
      setError(err?.message || 'Failed to submit application. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <AppShell>
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress />
        </Box>
      </AppShell>
    );
  }

  if (existingApp) {
    const statusMsg = `Your application is currently: ${existingApp.status}`;
    return (
      <AppShell>
        <PageHeader title="Partner Application" />
        <Alert severity="info" sx={{ borderRadius: 2 }}>
          You already have a partner application. {statusMsg}
        </Alert>
        <Box sx={{ mt: 2 }}>
          <Button variant="contained" onClick={() => navigate('/partner/dashboard')}>
            Go to Dashboard
          </Button>
        </Box>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <PageHeader
        title="Partner Application"
        subtitle="Fill out the form below to become a partner"
        breadcrumbs={[
          { label: 'Partner', href: '/partner/dashboard' },
          { label: 'Apply' },
        ]}
      />
      <Paper elevation={0} sx={{ p: 4, borderRadius: 2, border: 1, borderColor: 'divider', maxWidth: 720 }}>
        {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}
        <Grid container spacing={2.5}>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              label="Business Name"
              value={formData.businessName}
              onChange={handleChange('businessName')}
              error={!!errors.businessName}
              helperText={errors.businessName}
              required
            />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              label="Contact Name"
              value={formData.name}
              onChange={handleChange('name')}
              error={!!errors.name}
              helperText={errors.name}
              required
            />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              label="Tax Code"
              value={formData.taxCode}
              onChange={handleChange('taxCode')}
              error={!!errors.taxCode}
              helperText={errors.taxCode || 'e.g. RO12345678'}
              required
            />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              label="Email"
              type="email"
              value={formData.email}
              onChange={handleChange('email')}
              error={!!errors.email}
              helperText={errors.email}
              required
            />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              label="Phone"
              value={formData.phone}
              onChange={handleChange('phone')}
              error={!!errors.phone}
              helperText={errors.phone}
              required
            />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              label="Address"
              value={formData.address}
              onChange={handleChange('address')}
              error={!!errors.address}
              helperText={errors.address}
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
            {submitting ? 'Submitting...' : 'Submit Application'}
          </Button>
          <Button variant="outlined" size="large" onClick={() => navigate(-1)}>
            Cancel
          </Button>
        </Box>
      </Paper>
    </AppShell>
  );
};

export default PartnerApplicationPage;
