export const mapPage = (springPage: any) => ({
  data: springPage.content ?? [],
  currentPage: springPage.pageable?.pageNumber ?? 0,
  totalPages: springPage.totalPages ?? 0,
  totalItems: springPage.totalElements ?? 0,
  totalPrice: 0,
  pageSize: springPage.pageable?.pageSize ?? 0,
  hasNext: !springPage.last ?? false,
  hasPrevious: !springPage.first ?? false,
});
