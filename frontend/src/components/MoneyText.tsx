interface Props {
  value?: number | string | null
  prefix?: string
}

/**
 * 金额展示：右对齐 + 等宽数字
 */
export function MoneyText({ value, prefix = '¥' }: Props) {
  const amount = Number(value ?? 0)
  const text = amount.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
  return <span className="money-text font-medium text-ink">{`${prefix}${text}`}</span>
}
