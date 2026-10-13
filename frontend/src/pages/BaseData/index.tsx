import { Tabs } from 'antd'
import { useAuthStore } from '../../store/useAuthStore'
import { SupplierPanel } from './SupplierPanel'
import { MaterialPanel } from './MaterialPanel'
import { UserRolePanel } from './UserRolePanel'

/**
 * 基础数据页：供应商 / 物料 / 用户与角色
 */
export function BaseDataPage() {
  const currentRole = useAuthStore((state) => state.currentRole)
  const readOnly = currentRole !== 'ADMIN'

  return (
    <div className="page-card p-5">
      <Tabs
        defaultActiveKey="supplier"
        items={[
          {
            key: 'supplier',
            label: '供应商',
            children: <SupplierPanel readOnly={readOnly} />
          },
          {
            key: 'material',
            label: '物料',
            children: <MaterialPanel readOnly={readOnly} />
          },
          {
            key: 'user',
            label: '用户与角色',
            children: <UserRolePanel readOnly={readOnly} />
          }
        ]}
      />
    </div>
  )
}
