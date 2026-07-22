import React from 'react';
import {
  Box,
  Paper,
  Grid,
  Typography,
  Button,
  TextField,
  Alert,
  CircularProgress,
  Stack,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import SaveIcon from '@mui/icons-material/Save';
import CancelIcon from '@mui/icons-material/Cancel';
import {
  AppShell,
  PageHeader,
  StatusBadge,
  SkeletonTable,
  ErrorState,
} from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import { getMyApplication, updatePartnerProfile } from '../../../api/PartnerRequest';
import type { PartnerResponse } from '../../../../types/partner';

const PartnerProfilePage = () => {
  const { showSnackbar } = useSnackbar();

  const [profile, setProfile] = React.useState<PartnerResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState('');
  const [editing, setEditing] = React.useState(false);
  const [saving, setSaving] = React.useState(false);

  const [editForm, setEditForm] = React.useState({
    name: '',
    businessName: '',
    email: '',
    phone: '',
    address: '',
  });

  const fetchProfile = React.useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await getMyApplication();
      setProfile(res);
      setEditForm({
        name: res.name,
        businessName: res.businessName,
        email: res.email,
        phone: res.phone,
        address: res.address,
      });
    } catch {
      setError('Failed to load profile');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => { fetchProfile(); }, [fetchProfile]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const updated = await updatePartnerProfile(editForm);
      setProfile(updated);
      setEditing(false);
      showSnackbar('Profile updated successfully', 'success');
    } catch {
      showSnackbar('Failed to update profile', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    if (!profile) return;
    setEditForm({
      name: profile.name,
      businessName: profile.businessName,
      email: profile.email,
      phone: profile.phone,
      address: profile.address,
    });
    setEditing(false);
  };

  const formatDate = (dateStr?: string) =>
    dateStr ? new Date(dateStr).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' }) : '-';

  if (loading) {
    return (
      <AppShell>
        <PageHeader title="Partner Profile" />
        <SkeletonTable rows={6} columns={2} />
      </AppShell>
    );
  }

  if (error || !profile) {
    return (
      <AppShell>
        <PageHeader title="Partner Profile" />
        <ErrorState message={error || 'Profile not found'} onRetry={fetchProfile} />
      </AppShell>
    );
  }

  return (
    <AppShell>
      <PageHeader
        title="Partner Profile"
        subtitle={`Code: ${profile.code}`}
        breadcrumbs={[{ label: 'Partner', href: '/partner/dashboard' }, { label: 'Profile' }]}
        actions={
          !editing ? (
            <Button variant="contained" startIcon={<EditIcon />} onClick={() => setEditing(true)}>
              Edit
            </Button>
          ) : (
            <Stack direction="row" spacing={1}>
              <Button variant="outlined" startIcon={<CancelIcon />} onClick={handleCancel}>
                Cancel
              </Button>
              <Button
                variant="contained"
                color="success"
                startIcon={saving ? <CircularProgress size={20} /> : <SaveIcon />}
                onClick={handleSave}
                disabled={saving}
              >
                {saving ? 'Saving...' : 'Save'}
              </Button>
            </Stack>
          )
        }
      />

      <Paper elevation={0} sx={{ p: 4, borderRadius: 2, border: 1, borderColor: 'divider', maxWidth: 720 }}>
        <Grid container spacing={2.5}>
          <Grid item xs={12}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
              <Typography variant="h6" sx={{ fontWeight: 600 }}>{profile.businessName}</Typography>
              <StatusBadge status={profile.status} size="small" />
            </Box>
          </Grid>
          <Grid item xs={6}>
            <Typography variant="body2" color="text.secondary">Partner Code</Typography>
            <Typography>{profile.code}</Typography>
          </Grid>
          <Grid item xs={6}>
            <Typography variant="body2" color="text.secondary">Tax Code</Typography>
            <Typography>{profile.taxCode}</Typography>
          </Grid>
          <Grid item xs={6}>
            {editing ? (
              <TextField
                fullWidth
                label="Contact Name"
                value={editForm.name}
                onChange={e => setEditForm(prev => ({ ...prev, name: e.target.value }))}
              />
            ) : (
              <>
                <Typography variant="body2" color="text.secondary">Contact Name</Typography>
                <Typography>{profile.name}</Typography>
              </>
            )}
          </Grid>
          <Grid item xs={6}>
            {editing ? (
              <TextField
                fullWidth
                label="Business Name"
                value={editForm.businessName}
                onChange={e => setEditForm(prev => ({ ...prev, businessName: e.target.value }))}
              />
            ) : (
              <>
                <Typography variant="body2" color="text.secondary">Business Name</Typography>
                <Typography>{profile.businessName}</Typography>
              </>
            )}
          </Grid>
          <Grid item xs={6}>
            {editing ? (
              <TextField
                fullWidth
                label="Email"
                type="email"
                value={editForm.email}
                onChange={e => setEditForm(prev => ({ ...prev, email: e.target.value }))}
              />
            ) : (
              <>
                <Typography variant="body2" color="text.secondary">Email</Typography>
                <Typography>{profile.email}</Typography>
              </>
            )}
          </Grid>
          <Grid item xs={6}>
            {editing ? (
              <TextField
                fullWidth
                label="Phone"
                value={editForm.phone}
                onChange={e => setEditForm(prev => ({ ...prev, phone: e.target.value }))}
              />
            ) : (
              <>
                <Typography variant="body2" color="text.secondary">Phone</Typography>
                <Typography>{profile.phone}</Typography>
              </>
            )}
          </Grid>
          <Grid item xs={12}>
            {editing ? (
              <TextField
                fullWidth
                label="Address"
                multiline
                rows={2}
                value={editForm.address}
                onChange={e => setEditForm(prev => ({ ...prev, address: e.target.value }))}
              />
            ) : (
              <>
                <Typography variant="body2" color="text.secondary">Address</Typography>
                <Typography>{profile.address}</Typography>
              </>
            )}
          </Grid>
          <Grid item xs={6}>
            <Typography variant="body2" color="text.secondary">Created</Typography>
            <Typography>{formatDate(profile.createdAt)}</Typography>
          </Grid>
          <Grid item xs={6}>
            <Typography variant="body2" color="text.secondary">Updated</Typography>
            <Typography>{formatDate(profile.updatedAt)}</Typography>
          </Grid>
          {profile.rejectionReason && (
            <Grid item xs={12}>
              <Alert severity="error">{profile.rejectionReason}</Alert>
            </Grid>
          )}
        </Grid>
      </Paper>
    </AppShell>
  );
};

export default PartnerProfilePage;
