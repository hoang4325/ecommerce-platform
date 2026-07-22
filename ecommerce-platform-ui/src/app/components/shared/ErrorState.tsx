import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import RefreshIcon from '@mui/icons-material/Refresh';

interface ErrorStateProps {
  message: string;
  onRetry?: () => void;
}

const ErrorState = ({ message, onRetry }: ErrorStateProps) => {
  return (
    <Box sx={{ py: 4 }}>
      <Alert severity="error" sx={{ borderRadius: 2 }}>
        <AlertTitle>Lỗi</AlertTitle>
        {message}
      </Alert>
      {onRetry && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
          <Button
            variant="outlined"
            color="error"
            startIcon={<RefreshIcon />}
            onClick={onRetry}
          >
            Thử lại
          </Button>
        </Box>
      )}
    </Box>
  );
};

export default ErrorState;
