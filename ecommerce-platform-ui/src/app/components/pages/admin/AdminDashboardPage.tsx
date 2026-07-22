import React from 'react';
import { useNavigate } from 'react-router-dom';
import Grid from '@mui/material/Grid';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import PeopleIcon from '@mui/icons-material/People';
import RateReviewIcon from '@mui/icons-material/RateReview';
import SellIcon from '@mui/icons-material/Sell';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import { AppShell, PageHeader, MetricCard, StatusBadge, ErrorState, EmptyState } from '../../shared';
import { getPartners, getOffers, getSettlements } from '../../../api/AdminRequest';
import { PartnerResponse, PartnerOfferResponse, SettlementResponse } from '../../../../types/partner';

const formatDate = (value?: string) => {
  if (!value) {
    return '-';
  }

  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(value));
};

const formatCurrency = (amount: number, currency = 'VND') => {
  const normalizedCurrency = currency === 'EUR' || currency === 'USD' ? currency : 'VND';

  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: normalizedCurrency,
    maximumFractionDigits: normalizedCurrency === 'VND' ? 0 : 2,
  }).format(amount);
};

const repairMojibake = (value?: string) => {
  if (!value || !/[ÃÄÅÂ]/.test(value)) {
    return value || '-';
  }

  const windows1252Bytes: Record<string, number> = {
    '€': 0x80,
    '‚': 0x82,
    'ƒ': 0x83,
    '„': 0x84,
    '…': 0x85,
    '†': 0x86,
    '‡': 0x87,
    'ˆ': 0x88,
    '‰': 0x89,
    'Š': 0x8a,
    '‹': 0x8b,
    'Œ': 0x8c,
    'Ž': 0x8e,
    '‘': 0x91,
    '’': 0x92,
    '“': 0x93,
    '”': 0x94,
    '•': 0x95,
    '–': 0x96,
    '—': 0x97,
    '˜': 0x98,
    '™': 0x99,
    'š': 0x9a,
    '›': 0x9b,
    'œ': 0x9c,
    'ž': 0x9e,
    'Ÿ': 0x9f,
  };

  try {
    const bytes = Uint8Array.from(Array.from(value).map(char => windows1252Bytes[char] ?? (char.charCodeAt(0) & 0xff)));
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch {
    return value;
  }
};

interface DashboardSectionProps {
  title: string;
  subtitle: string;
  actionLabel: string;
  onAction: () => void;
  children: React.ReactNode;
}

const DashboardSection = ({ title, subtitle, actionLabel, onAction, children }: DashboardSectionProps) => (
  <Paper
    elevation={0}
    sx={{
      height: '100%',
      border: 1,
      borderColor: 'divider',
      borderRadius: 3,
      overflow: 'hidden',
      bgcolor: 'background.paper',
    }}
  >
    <Box sx={{ px: 2.5, py: 2, display: 'flex', justifyContent: 'space-between', gap: 2, alignItems: 'flex-start' }}>
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
          {title}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          {subtitle}
        </Typography>
      </Box>
      <Button
        size="small"
        endIcon={<ArrowForwardIcon />}
        onClick={onAction}
        sx={{ whiteSpace: 'nowrap', flexShrink: 0, textTransform: 'none', fontWeight: 700 }}
      >
        {actionLabel}
      </Button>
    </Box>
    <Divider />
    {children}
  </Paper>
);

const TableSkeleton = () => (
  <Box sx={{ p: 2 }}>
    <Skeleton variant="rounded" height={36} sx={{ mb: 1 }} />
    <Skeleton variant="rounded" height={36} sx={{ mb: 1 }} />
    <Skeleton variant="rounded" height={36} />
  </Box>
);

const AdminDashboardPage = () => {
  const navigate = useNavigate();

  const [partnersLoading, setPartnersLoading] = React.useState(true);
  const [partnersError, setPartnersError] = React.useState<string | null>(null);
  const [partners, setPartners] = React.useState<PartnerResponse[]>([]);
  const [totalPartners, setTotalPartners] = React.useState(0);
  const [pendingPartners, setPendingPartners] = React.useState(0);

  const [offersLoading, setOffersLoading] = React.useState(true);
  const [offersError, setOffersError] = React.useState<string | null>(null);
  const [offers, setOffers] = React.useState<PartnerOfferResponse[]>([]);
  const [pendingOffers, setPendingOffers] = React.useState(0);

  const [settlementsLoading, setSettlementsLoading] = React.useState(true);
  const [settlementsError, setSettlementsError] = React.useState<string | null>(null);
  const [settlements, setSettlements] = React.useState<SettlementResponse[]>([]);
  const [dueSettlements, setDueSettlements] = React.useState(0);

  React.useEffect(() => {
    const fetchData = async () => {
      setPartnersLoading(true);
      setPartnersError(null);
      try {
        const result = await getPartners({ page: 0, size: 10, sort: 'createdAt,desc' });
        setPartners(result.data);
        setTotalPartners(result.totalItems);
        const pending = await getPartners({ status: 'PENDING_REVIEW', page: 0, size: 1 });
        setPendingPartners(pending.totalItems);
      } catch (err: any) {
        setPartnersError(err?.message || 'Không thể tải danh sách đối tác');
      } finally {
        setPartnersLoading(false);
      }
    };
    fetchData();
  }, []);

  React.useEffect(() => {
    const fetchData = async () => {
      setOffersLoading(true);
      setOffersError(null);
      try {
        const result = await getOffers({ page: 0, size: 5, sort: 'createdAt,desc' });
        setOffers(result.data);
        const pending = await getOffers({ status: 'PENDING_REVIEW', page: 0, size: 1 });
        setPendingOffers(pending.totalItems);
      } catch (err: any) {
        setOffersError(err?.message || 'Không thể tải danh sách ưu đãi');
      } finally {
        setOffersLoading(false);
      }
    };
    fetchData();
  }, []);

  React.useEffect(() => {
    const fetchData = async () => {
      setSettlementsLoading(true);
      setSettlementsError(null);
      try {
        const result = await getSettlements({ page: 0, size: 5, sort: 'createdAt,desc' });
        setSettlements(result.data);
        setDueSettlements(result.data.filter(s => s.status === 'APPROVED').length);
      } catch (err: any) {
        setSettlementsError(err?.message || 'Không thể tải danh sách thanh toán');
      } finally {
        setSettlementsLoading(false);
      }
    };
    fetchData();
  }, []);

  const approvedPartners = partners.filter(partner => partner.status === 'APPROVED').length;
  const activeOffers = offers.filter(offer => offer.status === 'APPROVED').length;
  const totalSettlementValue = settlements.reduce((sum, settlement) => sum + Number(settlement.payableAmount || 0), 0);
  const primaryCurrency = settlements[0]?.currency || 'VND';

  return (
    <AppShell>
      <PageHeader
        title="Bảng điều khiển quản trị"
        subtitle="Theo dõi đối tác, ưu đãi và thanh toán trên toàn nền tảng"
        actions={(
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <Button variant="outlined" onClick={() => navigate('/admin/offers')} sx={{ textTransform: 'none', fontWeight: 700 }}>
              Kiểm duyệt ưu đãi
            </Button>
            <Button variant="contained" onClick={() => navigate('/admin/partners')} sx={{ textTransform: 'none', fontWeight: 700 }}>
              Quản lý đối tác
            </Button>
          </Stack>
        )}
      />

      <Paper
        elevation={0}
        sx={{
          mb: 3,
          p: { xs: 2.5, md: 3 },
          borderRadius: 3,
          border: 1,
          borderColor: 'divider',
          bgcolor: '#f8fbff',
        }}
      >
        <Grid container spacing={2.5} alignItems="center">
          <Grid item xs={12} md={7}>
            <Stack direction="row" spacing={1} sx={{ mb: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
              <Chip icon={<TaskAltIcon />} label={`${approvedPartners} đối tác đã duyệt`} size="small" color="success" variant="outlined" />
              <Chip icon={<WarningAmberIcon />} label={`${pendingPartners + pendingOffers} mục cần xử lý`} size="small" color="warning" variant="outlined" />
            </Stack>
            <Typography variant="h5" sx={{ fontWeight: 800, mb: 1 }}>
              Hàng chờ vận hành hôm nay
            </Typography>
            <Typography color="text.secondary" sx={{ maxWidth: 720 }}>
              Theo dõi các hồ sơ và ưu đãi gần đây. Những mục chờ duyệt sẽ được đếm riêng ở các thẻ phía dưới.
            </Typography>
          </Grid>
          <Grid item xs={12} md={5}>
            <Paper elevation={0} sx={{ p: 2, borderRadius: 2, border: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
              <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2}>
                <Box>
                  <Typography variant="body2" color="text.secondary">Giá trị thanh toán gần đây</Typography>
                  <Typography variant="h5" sx={{ fontWeight: 800 }}>{formatCurrency(totalSettlementValue, primaryCurrency)}</Typography>
                </Box>
                <Box sx={{ width: 44, height: 44, borderRadius: 2, display: 'grid', placeItems: 'center', bgcolor: 'primary.main', color: 'primary.contrastText' }}>
                  <TrendingUpIcon />
                </Box>
              </Stack>
            </Paper>
          </Grid>
        </Grid>
      </Paper>

      <Grid container spacing={2.5} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} lg={3}>
          <MetricCard title="Tổng đối tác" value={totalPartners} icon={<PeopleIcon />} loading={partnersLoading} subtitle="đang quản lý" />
        </Grid>
        <Grid item xs={12} sm={6} lg={3}>
          <MetricCard title="Đối tác chờ duyệt" value={pendingPartners} icon={<RateReviewIcon />} loading={partnersLoading} subtitle="cần kiểm tra hồ sơ" />
        </Grid>
        <Grid item xs={12} sm={6} lg={3}>
          <MetricCard title="Ưu đãi chờ duyệt" value={pendingOffers} icon={<SellIcon />} loading={offersLoading} subtitle={`${activeOffers} ưu đãi đang hoạt động`} />
        </Grid>
        <Grid item xs={12} sm={6} lg={3}>
          <MetricCard title="Thanh toán đến hạn" value={dueSettlements} icon={<AccountBalanceIcon />} loading={settlementsLoading} subtitle="đợt đã được duyệt" />
        </Grid>
      </Grid>

      <Grid container spacing={2.5}>
        <Grid item xs={12} xl={4}>
          <DashboardSection title="Đối tác gần đây" subtitle="10 hồ sơ gần nhất" actionLabel="Xem tất cả" onAction={() => navigate('/admin/partners')}>
            {partnersLoading ? (
              <TableSkeleton />
            ) : partnersError ? (
              <ErrorState message={partnersError} />
            ) : partners.length === 0 ? (
              <EmptyState title="Chưa có đối tác" description="Các đơn đăng ký mới sẽ xuất hiện tại đây." />
            ) : (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Doanh nghiệp</TableCell>
                      <TableCell>Ngày tạo</TableCell>
                      <TableCell align="right">Trạng thái</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {partners.map(partner => (
                      <TableRow key={partner.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/admin/partners/${partner.id}`)}>
                        <TableCell sx={{ maxWidth: 260 }}>
                          <Typography fontWeight={700} noWrap title={repairMojibake(partner.businessName)}>
                            {repairMojibake(partner.businessName)}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" noWrap>
                            {partner.email}
                          </Typography>
                        </TableCell>
                        <TableCell>{formatDate(partner.createdAt)}</TableCell>
                        <TableCell align="right"><StatusBadge status={partner.status} size="small" /></TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </DashboardSection>
        </Grid>

        <Grid item xs={12} xl={4}>
          <DashboardSection title="Ưu đãi gần đây" subtitle="Sản phẩm đối tác gửi lên" actionLabel="Kiểm duyệt" onAction={() => navigate('/admin/offers')}>
            {offersLoading ? (
              <TableSkeleton />
            ) : offersError ? (
              <ErrorState message={offersError} />
            ) : offers.length === 0 ? (
              <EmptyState title="Chưa có ưu đãi" description="Các ưu đãi mới hoặc đang chờ duyệt sẽ hiển thị tại đây." />
            ) : (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Sản phẩm</TableCell>
                      <TableCell>Giá</TableCell>
                      <TableCell align="right">Trạng thái</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {offers.map(offer => (
                      <TableRow key={offer.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/admin/offers/${offer.id}`)}>
                        <TableCell sx={{ maxWidth: 280 }}>
                          <Typography fontWeight={700} noWrap title={repairMojibake(offer.productName)}>
                            {repairMojibake(offer.productName)}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            SKU: {offer.partnerSku || '-'}
                          </Typography>
                        </TableCell>
                        <TableCell>{formatCurrency(offer.price, offer.currency)}</TableCell>
                        <TableCell align="right"><StatusBadge status={offer.status} size="small" /></TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </DashboardSection>
        </Grid>

        <Grid item xs={12} xl={4}>
          <DashboardSection title="Thanh toán gần đây" subtitle="Đợt đối soát mới nhất" actionLabel="Xem thanh toán" onAction={() => navigate('/admin/settlements')}>
            {settlementsLoading ? (
              <TableSkeleton />
            ) : settlementsError ? (
              <ErrorState message={settlementsError} />
            ) : settlements.length === 0 ? (
              <EmptyState title="Chưa có thanh toán" description="Các đợt đối soát đối tác sẽ được ghi nhận tại đây." />
            ) : (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Đối tác</TableCell>
                      <TableCell>Phải trả</TableCell>
                      <TableCell align="right">Trạng thái</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {settlements.map(settlement => (
                      <TableRow key={settlement.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/admin/settlements/${settlement.id}`)}>
                        <TableCell>
                          <Typography fontWeight={700}>Đối tác #{settlement.partnerId}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {formatDate(settlement.periodStart)} - {formatDate(settlement.periodEnd)}
                          </Typography>
                        </TableCell>
                        <TableCell>{formatCurrency(settlement.payableAmount, settlement.currency)}</TableCell>
                        <TableCell align="right"><StatusBadge status={settlement.status} size="small" /></TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </DashboardSection>
        </Grid>
      </Grid>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mt: 3, flexWrap: 'wrap' }}>
        <Button variant="outlined" onClick={() => navigate('/admin/partners')} sx={{ textTransform: 'none', fontWeight: 700 }}>
          Quản lý đối tác
        </Button>
        <Button variant="outlined" onClick={() => navigate('/admin/offers')} sx={{ textTransform: 'none', fontWeight: 700 }}>
          Duyệt ưu đãi
        </Button>
        <Button variant="outlined" onClick={() => navigate('/admin/settlements')} sx={{ textTransform: 'none', fontWeight: 700 }}>
          Xem thanh toán
        </Button>
        <Button variant="outlined" onClick={() => navigate('/admin/commission-rules')} sx={{ textTransform: 'none', fontWeight: 700 }}>
          Quy tắc hoa hồng
        </Button>
      </Stack>
    </AppShell>
  );
};

export default AdminDashboardPage;
