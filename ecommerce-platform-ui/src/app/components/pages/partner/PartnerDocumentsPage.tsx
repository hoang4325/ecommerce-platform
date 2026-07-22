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
  Typography,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  CircularProgress,
} from '@mui/material';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import {
  AppShell,
  PageHeader,
  StatusBadge,
  SkeletonTable,
  EmptyState,
  ErrorState,
} from '../../shared';
import { useSnackbar } from '../../SnackbarProvider';
import { getPartnerDocuments, uploadDocument } from '../../../api/PartnerRequest';
import type { PartnerDocumentResponse } from '../../../../types/partner';

const DOCUMENT_TYPE_LABELS: Record<string, string> = {
  BUSINESS_REGISTRATION: 'Giấy đăng ký kinh doanh',
  TAX_CERTIFICATE: 'Giấy chứng nhận thuế',
  IDENTIFICATION: 'Giấy tờ định danh',
  BANK_STATEMENT: 'Sao kê ngân hàng',
  OTHER: 'Khác',
};

const PartnerDocumentsPage = () => {
  const { showSnackbar } = useSnackbar();

  const [documents, setDocuments] = React.useState<PartnerDocumentResponse[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState('');

  const [uploadDialogOpen, setUploadDialogOpen] = React.useState(false);
  const [docType, setDocType] = React.useState('');
  const [docFile, setDocFile] = React.useState<File | null>(null);
  const [uploading, setUploading] = React.useState(false);

  const fetchDocuments = React.useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await getPartnerDocuments();
      setDocuments(res);
    } catch {
      setError('Không tải được danh sách tài liệu');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => { fetchDocuments(); }, [fetchDocuments]);

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const formatDate = (dateStr: string) =>
    new Date(dateStr).toLocaleDateString('vi-VN', { year: 'numeric', month: '2-digit', day: '2-digit' });

  const handleUpload = async () => {
    if (!docFile || !docType) {
      showSnackbar('Vui lòng chọn loại tài liệu và tệp tải lên', 'error');
      return;
    }
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', docFile);
      formData.append('documentType', docType);
      await uploadDocument(formData);
      showSnackbar('Đã tải tài liệu lên', 'success');
      setUploadDialogOpen(false);
      setDocFile(null);
      setDocType('');
      fetchDocuments();
    } catch {
      showSnackbar('Không thể tải tài liệu lên', 'error');
    } finally {
      setUploading(false);
    }
  };

  return (
    <AppShell>
      <PageHeader
        title="Tài liệu"
        subtitle="Quản lý hồ sơ xác minh của gian hàng"
        breadcrumbs={[{ label: 'Đối tác', href: '/partner/dashboard' }, { label: 'Tài liệu' }]}
        actions={
          <Button variant="contained" startIcon={<CloudUploadIcon />} onClick={() => setUploadDialogOpen(true)}>
            Tải tài liệu
          </Button>
        }
      />

      {loading ? (
        <SkeletonTable rows={6} columns={5} />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchDocuments} />
      ) : documents.length === 0 ? (
        <EmptyState
          title="Chưa có tài liệu"
          description="Tải giấy tờ kinh doanh lên để admin xác minh hồ sơ shop"
          action={
            <Button variant="contained" startIcon={<CloudUploadIcon />} onClick={() => setUploadDialogOpen(true)}>
              Tải tài liệu
            </Button>
          }
        />
      ) : (
        <TableContainer component={Paper} elevation={0} sx={{ borderRadius: 2, border: 1, borderColor: 'divider' }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>Loại tài liệu</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Tên tệp</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Dung lượng</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Trạng thái</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Ngày tải lên</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {documents.map(doc => (
                <TableRow key={doc.id} hover>
                  <TableCell>{DOCUMENT_TYPE_LABELS[doc.documentType] || doc.documentType}</TableCell>
                  <TableCell>{doc.originalFileName}</TableCell>
                  <TableCell>{formatFileSize(doc.fileSize)}</TableCell>
                  <TableCell>
                    <StatusBadge status={doc.status} size="small" />
                    {doc.status === 'REJECTED' && doc.rejectionReason && (
                      <Typography variant="caption" color="error" sx={{ display: 'block', mt: 0.5 }}>
                        {doc.rejectionReason}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{formatDate(doc.uploadedAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={uploadDialogOpen} onClose={() => setUploadDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Tải tài liệu</DialogTitle>
        <DialogContent>
          <FormControl fullWidth sx={{ mb: 2, mt: 1 }}>
            <InputLabel>Loại tài liệu</InputLabel>
            <Select
              value={docType}
              label="Loại tài liệu"
              onChange={e => setDocType(e.target.value)}
            >
              {Object.entries(DOCUMENT_TYPE_LABELS).map(([value, label]) => (
                <MenuItem key={value} value={value}>{label}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <Button variant="outlined" component="label" fullWidth sx={{ py: 3, borderStyle: 'dashed' }}>
            {docFile ? docFile.name : 'Bấm để chọn tệp'}
            <input
              type="file"
              hidden
              onChange={e => setDocFile(e.target.files?.[0] || null)}
            />
          </Button>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setUploadDialogOpen(false)}>Hủy</Button>
          <Button
            variant="contained"
            onClick={handleUpload}
            disabled={uploading || !docFile || !docType}
            startIcon={uploading ? <CircularProgress size={20} /> : null}
          >
            {uploading ? 'Đang tải...' : 'Tải lên'}
          </Button>
        </DialogActions>
      </Dialog>
    </AppShell>
  );
};

export default PartnerDocumentsPage;
