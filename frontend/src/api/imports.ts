import { request, unwrap } from './request'
import type { Result } from '../types'

export interface ImportUploadResult {
  batchNo: string
  totalRows: number
  validRows: number
  warningRows: number
  errorRows: number
  canConfirm: boolean
}

export interface ImportError {
  id: number
  rowIndex: number | null
  fieldName: string | null
  rawValue: string | null
  errorLevel: 'ERROR' | 'WARNING'
  errorCode: string | null
  errorMessage: string
  suggestion: string | null
}

export interface ImportBatch {
  id: number
  batchNo: string
  bizType: string
  templateVersion: string
  fileName: string
  status: string
  totalRows: number
  validRows: number
  warningRows: number
  errorRows: number
  summary: string
}

export const importApi = {
  /**
   * 下载模板
   *
   * 必须走 axios 以携带 X-User-Id / X-Role-Code 鉴权头，
   * 直接使用 <a href> 会被后端 AuthInterceptor 拦截为"需要授权"。
   */
  downloadTemplate: async (bizType: 'ORDER' | 'RECEIPT' | 'INVOICE' | 'PAYMENT'): Promise<Blob> => {
    const response = await request.get(`/imports/templates/${bizType}`, {
      responseType: 'blob'
    })
    return response.data as Blob
  },

  /** 保存 Blob 到本地文件 */
  saveBlob: (blob: Blob, fileName: string) => {
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  },

  /** 上传并解析 */
  upload: (file: File, bizType: string): Promise<ImportUploadResult> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('bizType', bizType)
    return unwrap(request.post<Result<ImportUploadResult>>('/imports/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }))
  },

  /** 批次详情 */
  detail: (batchNo: string): Promise<ImportBatch> =>
    unwrap(request.get<Result<ImportBatch>>(`/imports/batches/${batchNo}`)),

  /** 错误列表 */
  errors: (batchNo: string): Promise<ImportError[]> =>
    unwrap(request.get<Result<ImportError[]>>(`/imports/batches/${batchNo}/errors`)),

  /** 确认导入 */
  confirm: (batchNo: string): Promise<{ batchNo: string; importedRows: number }> =>
    unwrap(request.post<Result<{ batchNo: string; importedRows: number }>>(`/imports/batches/${batchNo}/confirm`))
}