import React from 'react';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import Skeleton from '@mui/material/Skeleton';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import RemoveIcon from '@mui/icons-material/Remove';

interface MetricCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  trend?: 'up' | 'down' | 'neutral';
  trendValue?: string;
  icon?: React.ReactNode;
  loading?: boolean;
}

const MetricCard = ({ title, value, subtitle, trend, trendValue, icon, loading = false }: MetricCardProps) => {
  const trendIcon = trend === 'up' ? <ArrowUpwardIcon /> : trend === 'down' ? <ArrowDownwardIcon /> : <RemoveIcon />;
  const trendColor = trend === 'up' ? 'success.main' : trend === 'down' ? 'error.main' : 'text.disabled';

  return (
    <Card
      elevation={0}
      sx={{
        height: '100%',
        borderRadius: 3,
        border: 1,
        borderColor: 'divider',
        bgcolor: 'background.paper',
      }}
    >
      <CardContent sx={{ p: 2.5, '&:last-child': { pb: 2.5 } }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
          <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 700 }}>
            {title}
          </Typography>
          {icon && (
            <Box
              sx={{
                width: 36,
                height: 36,
                borderRadius: 2,
                display: 'grid',
                placeItems: 'center',
                color: 'primary.main',
                bgcolor: 'rgba(25, 118, 210, 0.08)',
              }}
            >
              {icon}
            </Box>
          )}
        </Box>
        {loading ? (
          <Box>
            <Skeleton variant="text" width="60%" height={40} />
            {subtitle && <Skeleton variant="text" width="40%" />}
          </Box>
        ) : (
          <>
            <Typography variant="h4" sx={{ fontWeight: 800, mb: 0.5, letterSpacing: 0 }}>
              {value}
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              {trend && (
                <Box sx={{ display: 'flex', alignItems: 'center', color: trendColor }}>
                  {trendIcon}
                </Box>
              )}
              {trendValue && (
                <Typography variant="body2" sx={{ color: trendColor, fontWeight: 500 }}>
                  {trendValue}
                </Typography>
              )}
              {subtitle && (
                <Typography variant="body2" color="text.secondary">
                  {subtitle}
                </Typography>
              )}
            </Box>
          </>
        )}
      </CardContent>
    </Card>
  );
};

export default MetricCard;
