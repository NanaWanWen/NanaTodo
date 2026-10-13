import type { ReactNode } from 'react'

interface Props {
  title?: string
  extra?: ReactNode
  children: ReactNode
  className?: string
}

/**
 * 统一卡片容器：白色圆角 + 柔和阴影
 */
export function PageCard({ title, extra, children, className = '' }: Props) {
  return (
    <section className={`page-card p-5 ${className}`}>
      {(title || extra) && (
        <header className="mb-4 flex items-center justify-between">
          {title && <h3 className="text-base font-medium text-ink">{title}</h3>}
          {extra && <div className="flex items-center gap-2">{extra}</div>}
        </header>
      )}
      {children}
    </section>
  )
}
