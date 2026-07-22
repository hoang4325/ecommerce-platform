import React from 'react';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Avatar from '@mui/material/Avatar';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Divider from '@mui/material/Divider';
import MenuIcon from '@mui/icons-material/Menu';
import AccountCircleIcon from '@mui/icons-material/AccountCircle';
import LogoutIcon from '@mui/icons-material/Logout';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../hooks';
import { clearTokens } from '../../slices/jwtSlice';
import * as AuthRequest from '../../api/AuthRequest';

const DRAWER_WIDTH = 240;

interface TopBarProps {
  onMenuToggle: () => void;
}

const TopBar = ({ onMenuToggle }: TopBarProps) => {
  const username = useAppSelector(state => state.username.sub);
  const jwt = useAppSelector(state => state.jwt.token);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);

  const handleOpenMenu = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleCloseMenu = () => {
    setAnchorEl(null);
  };

  const handleProfile = () => {
    handleCloseMenu();
    navigate('/profile');
  };

  const handleLogout = async () => {
    handleCloseMenu();
    try {
      await AuthRequest.logout(jwt);
    } catch {
      // proceed with cleanup regardless
    } finally {
      dispatch(clearTokens());
      navigate('/login');
    }
  };

  return (
    <AppBar
      position="fixed"
      elevation={0}
      sx={{
        width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
        ml: { md: `${DRAWER_WIDTH}px` },
        backdropFilter: 'blur(6px)',
        backgroundColor: 'rgba(255, 255, 255, 0.6)',
        borderBottom: 1,
        borderColor: 'divider',
        color: 'text.primary',
      }}
    >
      <Toolbar>
        <IconButton
          color="inherit"
          edge="start"
          onClick={onMenuToggle}
          sx={{ mr: 2, display: { md: 'none' } }}
        >
          <MenuIcon />
        </IconButton>
        <Typography variant="h6" noWrap sx={{ flexGrow: 1, fontWeight: 700 }}>
          Nền tảng Thương mại Điện tử
        </Typography>
        <IconButton
          onClick={handleOpenMenu}
          sx={{
            p: 0.5,
            border: 2,
            borderColor: 'transparent',
            '&:hover': { borderColor: 'primary.main' },
          }}
        >
          <Avatar
            sx={{
              width: 36,
              height: 36,
              bgcolor: 'primary.main',
              fontSize: 16,
              fontWeight: 700,
            }}
          >
            {username ? username.charAt(0).toUpperCase() : 'U'}
          </Avatar>
        </IconButton>
        <Menu
          anchorEl={anchorEl}
          open={Boolean(anchorEl)}
          onClose={handleCloseMenu}
          PaperProps={{
            elevation: 0,
            sx: {
              mt: 1.5,
              borderRadius: 2,
              border: 1,
              borderColor: 'divider',
              minWidth: 200,
            },
          }}
        >
          <MenuItem onClick={handleProfile}>
            <AccountCircleIcon sx={{ mr: 1.5 }} />
            <Typography>Hồ sơ</Typography>
          </MenuItem>
          <Divider />
          <MenuItem onClick={handleLogout} sx={{ color: 'error.main' }}>
            <LogoutIcon sx={{ mr: 1.5 }} />
            <Typography>Đăng xuất</Typography>
          </MenuItem>
        </Menu>
      </Toolbar>
    </AppBar>
  );
};

export default TopBar;
