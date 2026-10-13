import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { MainLayout } from './layouts/MainLayout'
import { RoleGuard } from './router/RoleGuard'
import { LoginPage } from './pages/Login'
import { OrderListPage } from './pages/OrderList'
import { OrderEditPage } from './pages/OrderEdit'
import { OrderDetailPage } from './pages/OrderDetail'
import { ApprovalsPage } from './pages/Approvals'
import { BaseDataPage } from './pages/BaseData'
import { ReportsPage } from './pages/Reports'
import { ForbiddenPage } from './pages/Forbidden'
import { ReceiptListPage } from './pages/ReceiptList'
import { InvoiceListPage } from './pages/InvoiceList'
import { PaymentListPage } from './pages/PaymentList'
import { StockListPage } from './pages/StockList'
import { ApSettlementListPage } from './pages/ApSettlementList'
import { AgentChatPage } from './pages/AgentChat'
import { AgentAlertsPage } from './pages/AgentAlerts'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/403" element={<ForbiddenPage />} />

        <Route
          element={
            <RoleGuard>
              <MainLayout />
            </RoleGuard>
          }
        >
          <Route index element={<Navigate to="/orders" replace />} />
          <Route path="orders" element={<OrderListPage />} />
          <Route
            path="orders/create"
            element={
              <RoleGuard roles={['BUYER', 'ADMIN']}>
                <OrderEditPage />
              </RoleGuard>
            }
          />
          <Route
            path="orders/:id/edit"
            element={
              <RoleGuard roles={['BUYER', 'ADMIN']}>
                <OrderEditPage />
              </RoleGuard>
            }
          />
          <Route path="orders/:id" element={<OrderDetailPage />} />
          <Route
            path="approvals"
            element={
              <RoleGuard roles={['PURCHASE_MANAGER', 'DIRECTOR']}>
                <ApprovalsPage />
              </RoleGuard>
            }
          />
          <Route path="base" element={<BaseDataPage />} />
          <Route path="reports" element={<ReportsPage />} />
          <Route
            path="receipts"
            element={
              <RoleGuard roles={['BUYER', 'ADMIN']}>
                <ReceiptListPage />
              </RoleGuard>
            }
          />
          <Route
            path="invoices"
            element={
              <RoleGuard roles={['BUYER', 'ADMIN']}>
                <InvoiceListPage />
              </RoleGuard>
            }
          />
          <Route
            path="payments"
            element={
              <RoleGuard roles={['BUYER', 'ADMIN']}>
                <PaymentListPage />
              </RoleGuard>
            }
          />
          <Route
            path="inventory"
            element={
              <RoleGuard roles={['BUYER', 'ADMIN']}>
                <StockListPage />
              </RoleGuard>
            }
          />
          <Route
            path="ap-settlements"
            element={
              <RoleGuard roles={['BUYER', 'ADMIN']}>
                <ApSettlementListPage />
              </RoleGuard>
            }
          />
          <Route path="agent-chat" element={<AgentChatPage />} />
          <Route path="agent-alerts" element={<AgentAlertsPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/orders" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
