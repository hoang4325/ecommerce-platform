import React from 'react';

import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './components/pages/auth/LoginPage';
import RegisterPage from './components/pages/auth/RegisterPage';
import ProductsContainer from './components/pages/products/ProductsContainer';
import CartContainer from './components/pages/cart/CartContainer';
import AddProductPage from './components/pages/products/AddProductPage';
import MyProductsPage from './components/pages/products/MyProductsPage';
import MyProfilePage from './components/pages/user/MyProfilePage';
import EditProductPage from './components/pages/products/EditProductPage';
import MyOrdersPage from './components/pages/orders/MyOrdersPage';
import { useAppSelector } from './hooks';
import SearchPage from './components/pages/search/SearchPage';
import AdminDashboardPage from './components/pages/admin/AdminDashboardPage';
import AdminPartnersPage from './components/pages/admin/AdminPartnersPage';
import AdminPartnerDetailPage from './components/pages/admin/AdminPartnerDetailPage';
import AdminOffersPage from './components/pages/admin/AdminOffersPage';
import AdminOfferDetailPage from './components/pages/admin/AdminOfferDetailPage';
import AdminSettlementsPage from './components/pages/admin/AdminSettlementsPage';
import AdminSettlementDetailPage from './components/pages/admin/AdminSettlementDetailPage';
import AdminCommissionRulesPage from './components/pages/admin/AdminCommissionRulesPage';
import AdminCommissionRuleEditPage from './components/pages/admin/AdminCommissionRuleEditPage';
import AdminCommissionRuleNewPage from './components/pages/admin/AdminCommissionRuleNewPage';
import PartnerDashboardPage from './components/pages/partner/PartnerDashboardPage';
import PartnerOffersPage from './components/pages/partner/PartnerOffersPage';
import PartnerOfferNewPage from './components/pages/partner/PartnerOfferNewPage';
import PartnerOfferEditPage from './components/pages/partner/PartnerOfferEditPage';
import PartnerOrdersPage from './components/pages/partner/PartnerOrdersPage';
import PartnerOrderDetailPage from './components/pages/partner/PartnerOrderDetailPage';
import PartnerSettlementsPage from './components/pages/partner/PartnerSettlementsPage';
import PartnerSettlementDetailPage from './components/pages/partner/PartnerSettlementDetailPage';
import PartnerMembersPage from './components/pages/partner/PartnerMembersPage';
import PartnerDocumentsPage from './components/pages/partner/PartnerDocumentsPage';
import PartnerBankAccountsPage from './components/pages/partner/PartnerBankAccountsPage';
import PartnerProfilePage from './components/pages/partner/PartnerProfilePage';
import PartnerApplicationPage from './components/pages/partner/PartnerApplicationPage';

const Main = () => {
    const jwt = useAppSelector(state => state.jwt.token);
    const roles = useAppSelector(state => state.info.info.roles);
    const hasRole = (role: string) => roles.some(r => r.name?.replace(/^ROLE_/, '') === role);
    const isAdmin = hasRole('ADMIN');
    const isPartner = hasRole('PARTNER');

    const RequireAuth = ({ children }: { children: React.ReactNode }) =>
        jwt.length > 0 ? <>{children}</> : <Navigate to='/login' />;

    const RequireAdmin = ({ children }: { children: React.ReactNode }) =>
        jwt.length > 0 && isAdmin ? <>{children}</> : <Navigate to='/login' />;

    const RequirePartner = ({ children }: { children: React.ReactNode }) =>
        jwt.length > 0 && isPartner ? <>{children}</> : <Navigate to='/login' />;

    return (
        <Routes>
            <Route path='/' element={<LoginPage />} />
            <Route path='/login' element={<LoginPage />} />
            <Route path='/register' element={<RegisterPage />} />
            <Route path='/products' element={<RequireAuth><ProductsContainer /></RequireAuth>} />
            <Route path='/cart' element={<RequireAuth><CartContainer /></RequireAuth>} />
            <Route path='/product/add' element={<RequireAuth><AddProductPage /></RequireAuth>} />
            <Route path='/profile/products' element={<RequireAuth><MyProductsPage /></RequireAuth>} />
            <Route path='/profile' element={<RequireAuth><MyProfilePage /></RequireAuth>} />
            <Route path='/orders' element={<RequireAuth><MyOrdersPage /></RequireAuth>} />
            <Route path='/product/edit' element={<RequireAuth><EditProductPage /></RequireAuth>} />
            <Route path='/search' element={<RequireAuth><SearchPage /></RequireAuth>} />
            <Route path='/admin/dashboard' element={<RequireAdmin><AdminDashboardPage /></RequireAdmin>} />
            <Route path='/admin/partners' element={<RequireAdmin><AdminPartnersPage /></RequireAdmin>} />
            <Route path='/admin/partners/:id' element={<RequireAdmin><AdminPartnerDetailPage /></RequireAdmin>} />
            <Route path='/admin/offers' element={<RequireAdmin><AdminOffersPage /></RequireAdmin>} />
            <Route path='/admin/offers/:id' element={<RequireAdmin><AdminOfferDetailPage /></RequireAdmin>} />
            <Route path='/admin/settlements' element={<RequireAdmin><AdminSettlementsPage /></RequireAdmin>} />
            <Route path='/admin/settlements/:id' element={<RequireAdmin><AdminSettlementDetailPage /></RequireAdmin>} />
            <Route path='/admin/commission-rules' element={<RequireAdmin><AdminCommissionRulesPage /></RequireAdmin>} />
            <Route path='/admin/commission-rules/edit/:id' element={<RequireAdmin><AdminCommissionRuleEditPage /></RequireAdmin>} />
            <Route path='/admin/commission-rules/new' element={<RequireAdmin><AdminCommissionRuleNewPage /></RequireAdmin>} />
            <Route path='/admin/commission-rules/:id' element={<RequireAdmin><AdminCommissionRuleEditPage /></RequireAdmin>} />
            <Route path='/partner/dashboard' element={<RequirePartner><PartnerDashboardPage /></RequirePartner>} />
            <Route path='/partner/offers' element={<RequirePartner><PartnerOffersPage /></RequirePartner>} />
            <Route path='/partner/offers/new' element={<RequirePartner><PartnerOfferNewPage /></RequirePartner>} />
            <Route path='/partner/offers/edit/:id' element={<RequirePartner><PartnerOfferEditPage /></RequirePartner>} />
            <Route path='/partner/offers/:id' element={<RequirePartner><PartnerOfferEditPage /></RequirePartner>} />
            <Route path='/partner/orders' element={<RequirePartner><PartnerOrdersPage /></RequirePartner>} />
            <Route path='/partner/orders/:id' element={<RequirePartner><PartnerOrderDetailPage /></RequirePartner>} />
            <Route path='/partner/settlements' element={<RequirePartner><PartnerSettlementsPage /></RequirePartner>} />
            <Route path='/partner/settlements/:id' element={<RequirePartner><PartnerSettlementDetailPage /></RequirePartner>} />
            <Route path='/partner/members' element={<RequirePartner><PartnerMembersPage /></RequirePartner>} />
            <Route path='/partner/documents' element={<RequirePartner><PartnerDocumentsPage /></RequirePartner>} />
            <Route path='/partner/bank-accounts' element={<RequirePartner><PartnerBankAccountsPage /></RequirePartner>} />
            <Route path='/partner/profile' element={<RequirePartner><PartnerProfilePage /></RequirePartner>} />
            <Route path='/partner/application' element={<RequirePartner><PartnerApplicationPage /></RequirePartner>} />
            <Route path='/partner/*' element={<RequirePartner><PartnerDashboardPage /></RequirePartner>} />
            <Route path='/admin/returns' element={<RequireAdmin><AdminDashboardPage /></RequireAdmin>} />
            <Route path='/admin/reports' element={<RequireAdmin><AdminDashboardPage /></RequireAdmin>} />
            <Route path='/admin/*' element={<RequireAdmin><AdminDashboardPage /></RequireAdmin>} />
        </Routes>
    );
}
export default Main;
