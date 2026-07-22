# Shopee VN Scraper (Playwright)

Công cụ cào dữ liệu từ Shopee.vn sử dụng thư viện Playwright để tự động hóa trình duyệt, cuộn trang tải dữ liệu và đánh chặn (intercept) các API phản hồi chính thức của Shopee để lấy thông tin sản phẩm và thông tin nhà cung cấp (Shop).

## Cấu trúc đầu ra
Dữ liệu cào được sẽ lưu vào thư mục `output/` dưới các định dạng:
1. `shopee_products.json`: Danh sách chi tiết các sản phẩm thu thập được.
2. `shopee_shops.json`: Thông tin chi tiết các nhà cung cấp (Shop) tương ứng.
3. `shopee_combined_data.csv`: Dữ liệu tổng hợp sản phẩm & tên nhà cung cấp dưới dạng CSV (được mã hóa UTF-8 BOM hiển thị chuẩn tiếng Việt trên Microsoft Excel).

## Cài đặt

Yêu cầu máy cài sẵn **Node.js** (khuyên dùng v18+).

1. Truy cập thư mục crawler:
```bash
cd scripts/shopee-crawler
```

2. Cài đặt các gói phụ thuộc và tải trình duyệt Chromium:
```bash
npm install
npx playwright install chromium
```

## Cách chạy

Cào dữ liệu mặc định với từ khóa `bàn phím cơ`:
```bash
npm start
```

### Chạy tùy chỉnh với biến môi trường:
Bạn có thể thay đổi từ khóa (`KEYWORD`) và số lượng trang kết quả tìm kiếm cần cuộn xuống (`LIMIT_PAGES`, mặc định là 1 trang ~ 60 sản phẩm):

```bash
# Trên Linux/macOS:
KEYWORD="kem chống nắng" LIMIT_PAGES=2 npm start

# Trên Windows (PowerShell):
$env:KEYWORD="kem chống nắng"; $env:LIMIT_PAGES="2"; npm start
```
