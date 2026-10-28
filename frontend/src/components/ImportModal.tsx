import { useState } from 'react'
import { Modal, Steps, Upload, Button, Table, Tag, Space, Alert, Statistic, Row, Col, Spin } from 'antd'
import type { UploadFile } from 'antd/es/upload/interface'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { importApi, ImportError, ImportUploadResult } from '../api/imports'
import { App as AntdApp } from 'antd'

const money = (value?: number) =>
  value ? `¥${value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '-'

export interface ImportModalProps {
  open: boolean
  onClose: () => void
  bizType: 'ORDER' | 'RECEIPT' | 'INVOICE' | 'PAYMENT'
  bizLabel: string
  onSuccess?: () => void
}

/**
 * Excel 导入模态框（上传 → 校验 → 预览 → 确认 → 完成）
 */
export function ImportModal({ open, onClose, bizType, bizLabel, onSuccess }: ImportModalProps) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()

  const [current, setCurrent] = useState(0)
  const [file, setFile] = useState<UploadFile | null>(null)
  const [batchNo, setBatchNo] = useState<string>('')
  const [uploadResult, setUploadResult] = useState<ImportUploadResult | null>(null)
  const [downloading, setDownloading] = useState(false)

  const { data: errors = [], isFetching: loadingErrors } = useQuery({
    queryKey: ['import-errors', batchNo],
    queryFn: () => importApi.errors(batchNo),
    enabled: !!batchNo && current >= 2
  })

  const uploadMutation = useMutation({
    mutationFn: (file: File) => importApi.upload(file, bizType),
    onSuccess: (result) => {
      setBatchNo(result.batchNo)
      setUploadResult(result)
      setCurrent(1)
      setTimeout(() => {
        setCurrent(2)
      }, 500)
    },
    onError: (error: Error) => {
      message.error('上传失败：' + error.message)
    }
  })

  const confirmMutation = useMutation({
    mutationFn: () => importApi.confirm(batchNo),
    onSuccess: (result) => {
      message.success(`成功导入 ${result.importedRows} 条${bizLabel}`)
      setCurrent(4)
      onSuccess?.()
      queryClient.invalidateQueries({ queryKey: ['receipts', 'invoices', 'payments'] })
    },
    onError: (error: Error) => {
      message.error('导入失败：' + error.message)
    }
  })

  const handleUpload = () => {
    if (!file || !file.originFileObj) {
      message.warning('请先选择文件')
      return
    }
    setCurrent(1)
    uploadMutation.mutate(file.originFileObj)
  }

  const handleConfirm = () => {
    if (!uploadResult?.canConfirm) {
      message.warning('存在阻断性错误，请修正后重新上传')
      return
    }
    setCurrent(3)
    confirmMutation.mutate()
  }

  const handleReset = () => {
    setCurrent(0)
    setFile(null)
    setBatchNo('')
    setUploadResult(null)
  }

  const handleDownloadTemplate = async () => {
    setDownloading(true)
    try {
      const blob = await importApi.downloadTemplate(bizType)
      importApi.saveBlob(blob, `${bizLabel}导入模板.xlsx`)
    } catch (error) {
      message.error('下载失败：' + (error as Error).message)
    } finally {
      setDownloading(false)
    }
  }

  const handleClose = () => {
    handleReset()
    onClose()
  }

  const errorColumns = [
    { title: '行号', dataIndex: 'rowIndex', width: 80, render: (v: number | null) => v ?? '-' },
    { title: '字段', dataIndex: 'fieldName', width: 120 },
    { title: '原始值', dataIndex: 'rawValue', width: 150, ellipsis: true },
    {
      title: '级别',
      dataIndex: 'errorLevel',
      width: 80,
      render: (v: string) => (
        <Tag color={v === 'ERROR' ? 'error' : 'warning'}>{v === 'ERROR' ? '阻断' : '警告'}</Tag>
      )
    },
    { title: '错误描述', dataIndex: 'errorMessage', width: 200, ellipsis: true },
    { title: '修复建议', dataIndex: 'suggestion', width: 200, ellipsis: true }
  ]

  return (
    <Modal
      title={`${bizLabel}导入`}
      open={open}
      onCancel={handleClose}
      width={820}
      footer={null}
      destroyOnClose
    >
      <Steps
        current={current}
        items={[
          { title: '上传文件', status: current >= 0 ? 'process' : 'wait' },
          { title: '解析校验', status: current >= 1 ? 'process' : 'wait' },
          { title: '预览结果', status: current >= 2 ? 'process' : 'wait' },
          { title: '确认导入', status: current >= 3 ? 'process' : 'wait' },
          { title: '完成', status: current >= 4 ? 'finish' : 'wait' }
        ]}
        className="mb-6"
      />

      {current === 0 && (
        <div className="flex flex-col gap-4">
          <Alert
            message="请下载标准模板填写数据，确保列标题和字段顺序一致"
            type="info"
            showIcon
            action={
              <Button loading={downloading} onClick={handleDownloadTemplate}>
                下载模板
              </Button>
            }
          />
          <Upload.Dragger
            accept=".xlsx,.xls"
            beforeUpload={(file) => {
              setFile(file as unknown as UploadFile)
              return false
            }}
            onRemove={() => setFile(null)}
            fileList={file ? [file] : []}
            maxCount={1}
          >
            <p className="ant-upload-drag-icon">
              <svg
                className="mx-auto h-12 w-12 text-primary"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="17 8 12 3 7 8" />
                <line x1="12" y1="3" x2="12" y2="15" />
              </svg>
            </p>
            <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
            <p className="ant-upload-hint">支持 .xlsx 或 .xls 格式</p>
          </Upload.Dragger>
          <div className="flex justify-end gap-2">
            <Button onClick={handleClose}>取消</Button>
            <Button type="primary" onClick={handleUpload} disabled={!file} loading={uploadMutation.isPending}>
              开始上传
            </Button>
          </div>
        </div>
      )}

      {current === 1 && (
        <div className="flex justify-center py-12">
          <Spin size="large" tip="正在解析文件...">
            <div className="py-8" />
          </Spin>
        </div>
      )}

      {current === 2 && uploadResult && (
        <div className="flex flex-col gap-4">
          <Row gutter={16}>
            <Col span={6}>
              <Statistic title="总行数" value={uploadResult.totalRows} />
            </Col>
            <Col span={6}>
              <Statistic title="有效行" value={uploadResult.validRows} valueStyle={{ color: '#3f8600' }} />
            </Col>
            <Col span={6}>
              <Statistic title="警告" value={uploadResult.warningRows} valueStyle={{ color: '#faad14' }} />
            </Col>
            <Col span={6}>
              <Statistic title="阻断错误" value={uploadResult.errorRows} valueStyle={{ color: '#cf1322' }} />
            </Col>
          </Row>

          {errors.length > 0 && (
            <>
              <Alert
                message={`发现 ${uploadResult.errorRows} 条阻断性错误，必须修正后才能导入`}
                type="error"
                showIcon
              />
              <Table<ImportError>
                size="small"
                columns={errorColumns}
                dataSource={errors.filter((e) => e.errorLevel === 'ERROR')}
                rowKey="id"
                loading={loadingErrors}
                pagination={{ pageSize: 5 }}
                scroll={{ x: 800 }}
              />
            </>
          )}

          <div className="flex justify-end gap-2">
            <Button onClick={handleReset}>重新上传</Button>
            <Button
              type="primary"
              onClick={handleConfirm}
              disabled={!uploadResult.canConfirm}
              loading={confirmMutation.isPending}
            >
              确认导入
            </Button>
          </div>
        </div>
      )}

      {current === 3 && (
        <div className="flex justify-center py-12">
          <Spin size="large" tip="正在导入数据...">
            <div className="py-8" />
          </Spin>
        </div>
      )}

      {current === 4 && (
        <div className="flex flex-col items-center gap-4 py-8">
          <svg
            className="h-16 w-16 text-green-500"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <circle cx="12" cy="12" r="10" />
            <path d="m8 12 3 3 5-6" />
          </svg>
          <div className="text-lg font-medium">导入成功</div>
          <div className="text-gray-500">已成功导入 {uploadResult?.validRows ?? 0} 条{bizLabel}</div>
          <Space>
            <Button type="primary" onClick={handleClose}>
              完成
            </Button>
            <Button onClick={handleReset}>继续导入</Button>
          </Space>
        </div>
      )}
    </Modal>
  )
}