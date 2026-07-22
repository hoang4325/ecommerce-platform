import React from 'react';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Box from '@mui/material/Box';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import { NavLink } from 'react-router-dom';
import { useAppSelector } from '../../hooks';

import DashboardIcon from '@mui/icons-material/Dashboard';
import InventoryIcon from '@mui/icons-material/Inventory';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import ReceiptIcon from '@mui/icons-material/Receipt';
import SearchIcon from '@mui/icons-material/Search';
import PersonIcon from '@mui/icons-material/Person';
import HowToRegIcon from '@mui/icons-material/HowToReg';
import SellIcon from '@mui/icons-material/Sell';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import GroupIcon from '@mui/icons-material/Group';
import DescriptionIcon from '@mui/icons-material/Description';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import BarChartIcon from '@mui/icons-material/BarChart';
import BusinessIcon from '@mui/icons-material/Business';
import RateReviewIcon from '@mui/icons-material/RateReview';
import GavelIcon from '@mui/icons-material/Gavel';
import AssignmentReturnIcon from '@mui/icons-material/AssignmentReturn';
import AssessmentIcon from '@mui/icons-material/Assessment';
import LocalMallIcon from '@mui/icons-material/LocalMall';

interface NavItem {
  label: string;
  path: string;
  icon: React.ReactElement;
}

interface SidebarProps {
  open: boolean;
  onClose: () => void;
  variant: 'permanent' | 'temporary';
}

const DRAWER_WIDTH = 240;

const customerNavItems: NavItem[] = [
  { label: 'Bảng điều khiển', path: '/dashboard', icon: <DashboardIcon /> },
  { label: 'Sản phẩm', path: '/products', icon: <InventoryIcon /> },
  { label: 'Giỏ hàng', path: '/cart', icon: <ShoppingCartIcon /> },
  { label: 'Đơn hàng', path: '/orders', icon: <ReceiptIcon /> },
  { label: 'Tìm kiếm', path: '/search', icon: <SearchIcon /> },
  { label: 'Hồ sơ', path: '/profile', icon: <PersonIcon /> },
];

const partnerNavItems: NavItem[] = [
  { label: 'Bảng điều khiển Đối tác', path: '/partner/dashboard', icon: <DashboardIcon /> },
  { label: 'Sản phẩm', path: '/partner/offers', icon: <SellIcon /> },
  { label: 'Đơn hàng', path: '/partner/orders', icon: <ReceiptIcon /> },
  { label: 'Thanh toán', path: '/partner/settlements', icon: <AccountBalanceWalletIcon /> },
  { label: 'Thành viên', path: '/partner/members', icon: <GroupIcon /> },
  { label: 'Tài liệu', path: '/partner/documents', icon: <DescriptionIcon /> },
  { label: 'Tài khoản ngân hàng', path: '/partner/bank-accounts', icon: <AccountBalanceIcon /> },
  { label: 'Báo cáo', path: '/partner/reports', icon: <BarChartIcon /> },
];

const adminNavItems: NavItem[] = [
  { label: 'Tổng quan', path: '/admin/dashboard', icon: <DashboardIcon /> },
  { label: 'Đối tác', path: '/admin/partners', icon: <BusinessIcon /> },
  { label: 'Kiểm duyệt ưu đãi', path: '/admin/offers', icon: <RateReviewIcon /> },
  { label: 'Thanh toán', path: '/admin/settlements', icon: <AccountBalanceIcon /> },
  { label: 'Hoa hồng', path: '/admin/commission-rules', icon: <GavelIcon /> },
  { label: 'Trả hàng', path: '/admin/returns', icon: <AssignmentReturnIcon /> },
  { label: 'Báo cáo', path: '/admin/reports', icon: <AssessmentIcon /> },
];

const becomePartnerItem: NavItem = {
  label: 'Trở thành Đối tác',
  path: '/partner/apply',
  icon: <HowToRegIcon />,
};

const drawerContent = (
  <Box>
    <Toolbar sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 2 }}>
      <LocalMallIcon sx={{ color: 'primary.main', fontSize: 28 }} />
      <Typography variant="h6" sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
        TMĐT
      </Typography>
    </Toolbar>
    <Divider />
    <SidebarNav />
  </Box>
);

function SidebarNav() {
  const roles = useAppSelector(state => state.info.info.roles);
  const hasRole = (role: string) => roles.some(r => r.name?.replace(/^ROLE_/, '') === role);
  const isAdmin = hasRole('ADMIN');
  const isPartner = hasRole('PARTNER');

  const items = isAdmin ? adminNavItems : isPartner ? partnerNavItems : customerNavItems;

  return (
    <List sx={{ px: 1 }}>
      {items.map(item => (
        <ListItem key={item.path} disablePadding sx={{ mb: 0.5 }}>
          <ListItemButton
            component={NavLink}
            to={item.path}
            sx={{
              borderRadius: 2,
              minHeight: 48,
              '&.active': {
                bgcolor: 'primary.main',
                color: 'primary.contrastText',
                '& .MuiListItemIcon-root': {
                  color: 'primary.contrastText',
                },
              },
            }}
          >
            <ListItemIcon sx={{ minWidth: 40 }}>
              {item.icon}
            </ListItemIcon>
            <ListItemText
              primary={item.label}
              primaryTypographyProps={{
                noWrap: true,
                fontSize: 14,
                fontWeight: 600,
              }}
            />
          </ListItemButton>
        </ListItem>
      ))}
      {!isAdmin && !isPartner && (
        <>
          <Divider sx={{ my: 1 }} />
          <ListItem disablePadding sx={{ mb: 0.5 }}>
            <ListItemButton
              component={NavLink}
              to={becomePartnerItem.path}
              sx={{
                borderRadius: 2,
                '&.active': {
                  bgcolor: 'primary.main',
                  color: 'primary.contrastText',
                  '& .MuiListItemIcon-root': {
                    color: 'primary.contrastText',
                  },
                },
              }}
            >
              <ListItemIcon sx={{ minWidth: 40 }}>
                {becomePartnerItem.icon}
              </ListItemIcon>
              <ListItemText primary={becomePartnerItem.label} />
            </ListItemButton>
          </ListItem>
        </>
      )}
    </List>
  );
}

const Sidebar = ({ open, onClose, variant }: SidebarProps) => {
  return (
    <Drawer
      variant={variant}
      open={open}
      onClose={onClose}
      ModalProps={{ keepMounted: true }}
      sx={{
        width: DRAWER_WIDTH,
        flexShrink: 0,
        '& .MuiDrawer-paper': {
          width: DRAWER_WIDTH,
          boxSizing: 'border-box',
        },
      }}
    >
      {drawerContent}
    </Drawer>
  );
};

export default Sidebar;
