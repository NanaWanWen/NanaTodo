import { useState, useEffect, useMemo, useRef } from 'react'
import { Select, Spin } from 'antd'
import type { SelectProps } from 'antd'
import { useQuery } from '@tanstack/react-query'

export interface SearchSelectProps extends Omit<SelectProps, 'options' | 'loading' | 'filterOption'> {
  /** 远程搜索函数 */
  fetchOptions: (keyword: string) => Promise<{ total: number; records: Array<{ id: number; no?: string; label: string; subLabel?: string; status?: string; statusName?: string; amount?: number; date?: string }> }>
  /** 防抖延迟(ms) */
  debounceWait?: number
}

/**
 * 远程搜索选择器（支持防抖、分页、关键字搜索）
 */
export function SearchSelect({ fetchOptions, debounceWait = 300, ...restProps }: SearchSelectProps) {
  const [keyword, setKeyword] = useState('')
  const [searchValue, setSearchValue] = useState('')
  const timerRef = useRef<number | null>(null)

  const { data, isFetching } = useQuery({
    queryKey: ['search-select', searchValue],
    queryFn: () => fetchOptions(searchValue),
    enabled: true
  })

  const options = useMemo(() => {
    return (data?.records ?? []).map((item) => ({
      value: item.id,
      label: item.no || item.label,
      raw: item
    }))
  }, [data])

  const handleSearch = (value: string) => {
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = window.setTimeout(() => {
      setSearchValue(value)
    }, debounceWait)
    setKeyword(value)
  }

  return (
    <Select
      {...restProps}
      showSearch
      filterOption={false}
      options={options}
      loading={isFetching}
      onSearch={handleSearch}
      notFoundContent={isFetching ? <Spin size="small" /> : '无匹配数据'}
      searchValue={keyword}
      allowClear
      onClear={() => {
        setKeyword('')
        setSearchValue('')
      }}
      optionRender={(option) => {
        const item = (option.data as { raw?: { no?: string; label?: string; status?: string; statusName?: string; subLabel?: string; amount?: number; date?: string } }).raw
        if (!item) return <span>{option.label as string}</span>
        return (
          <div className="flex flex-col gap-0.5 py-1">
            <div className="flex items-center gap-2">
              <span className="font-medium text-gray-900">{item.no || item.label}</span>
              {item.status && (
                <span className="text-xs px-1.5 py-0.5 rounded bg-blue-50 text-blue-600">
                  {item.statusName || item.status}
                </span>
              )}
            </div>
            <div className="text-xs text-gray-500">
              {item.subLabel && <span>{item.subLabel}</span>}
              {item.amount !== undefined && (
                <span className="ml-2">¥{item.amount.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</span>
              )}
              {item.date && <span className="ml-2">{item.date}</span>}
            </div>
          </div>
        )
      }}
      popupMatchSelectWidth={true}
    />
  )
}