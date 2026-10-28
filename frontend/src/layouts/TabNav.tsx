import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Dropdown, Tabs } from 'antd'
import { useTabsStore } from '../store/useTabsStore'
import { getTabMeta } from '../router/routeMeta'

/**
 * 全局多页签导航：点菜单开页签、可切换、可关闭（首页固定），
 * 配合 useTabsStore 持久化，刷新浏览器后还原。
 */
export function TabNav() {
  const location = useLocation()
  const navigate = useNavigate()
  const { openTabs, openTab, closeTab, closeOthers, closeAll } = useTabsStore()
  const pathname = location.pathname

  useEffect(() => {
    const meta = getTabMeta(pathname)
    openTab({ path: pathname, title: meta.title, closable: pathname !== '/workbench' })
  }, [pathname, openTab])

  const handleEdit = (targetKey: unknown, action: 'add' | 'remove') => {
    if (action === 'remove') {
      const next = closeTab(targetKey as string)
      navigate(next)
    }
  }

  const moreMenu = {
    items: [
      { key: 'others', label: '关闭其他页签' },
      { key: 'all', label: '关闭全部页签' }
    ],
    onClick: ({ key }: { key: string }) => {
      if (key === 'others') {
        closeOthers(pathname)
      } else if (key === 'all') {
        closeAll()
        navigate('/workbench')
      }
    }
  }

  return (
    <div className="bip-tabnav">
      <Tabs
        type="editable-card"
        hideAdd
        size="small"
        activeKey={pathname}
        items={openTabs.map((t) => ({
          key: t.path,
          label: t.title,
          closable: t.closable
        }))}
        onChange={(key) => navigate(key)}
        onEdit={handleEdit}
        tabBarExtraContent={{
          right: (
            <Dropdown menu={moreMenu} trigger={['click']}>
              <span className="bip-tabnav-more" title="更多操作">
                ⋯
              </span>
            </Dropdown>
          )
        }}
      />
    </div>
  )
}
