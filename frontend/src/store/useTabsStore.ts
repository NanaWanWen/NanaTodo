import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface TabItem {
  path: string;
  title: string;
  closable: boolean;
  icon?: string;
}

interface TabsState {
  openTabs: TabItem[];
  activePath: string;
  openTab: (tab: TabItem) => void;
  setActive: (path: string) => void;
  /** 关闭页签，返回应跳转的下一个 path */
  closeTab: (path: string) => string;
  closeOthers: (path: string) => void;
  closeRight: (path: string) => void;
  closeAll: () => void;
}

const MAX_TABS = 15;
const HOME: TabItem = { path: '/workbench', title: '工作台', closable: false, icon: 'home' };

export const useTabsStore = create<TabsState>()(
  persist(
    (set, get) => ({
      openTabs: [HOME],
      activePath: '/workbench',

      openTab: (tab) =>
        set((s) => {
          const exists = s.openTabs.some((t) => t.path === tab.path);
          let tabs = exists ? s.openTabs : [...s.openTabs, tab];
          if (tabs.length > MAX_TABS) {
            const idx = tabs.findIndex((t) => t.closable);
            if (idx >= 0) tabs = tabs.filter((_, i) => i !== idx);
          }
          return { openTabs: tabs, activePath: tab.path };
        }),

      setActive: (path) => set({ activePath: path }),

      closeTab: (path) => {
        const { openTabs, activePath } = get();
        const idx = openTabs.findIndex((t) => t.path === path);
        if (idx < 0) return activePath;
        if (!openTabs[idx].closable) return activePath;
        const tabs = openTabs.filter((t) => t.path !== path);
        let next = activePath;
        if (activePath === path) {
          const neighbor = tabs[idx] || tabs[idx - 1] || tabs[0] || HOME;
          next = neighbor.path;
        }
        set({ openTabs: tabs, activePath: next });
        return next;
      },

      closeOthers: (path) =>
        set((s) => ({
          openTabs: s.openTabs.filter((t) => !t.closable || t.path === path),
          activePath: path,
        })),

      closeRight: (path) =>
        set((s) => {
          const idx = s.openTabs.findIndex((t) => t.path === path);
          if (idx < 0) return {};
          return {
            openTabs: s.openTabs.filter((t, i) => i <= idx || !t.closable),
            activePath: path,
          };
        }),

      closeAll: () => set({ openTabs: [HOME], activePath: '/workbench' }),
    }),
    {
      name: 'yonerp-tabs',
      version: 1,
      migrate: (persistedState: unknown) => {
        // 旧版本数据格式不对，直接清除
        return { openTabs: [HOME], activePath: '/workbench' } as TabsState
      }
    }
  )
);
