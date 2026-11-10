import { apiClient } from './client'
import type { AxiosProgressEvent } from 'axios'
import type { PageResult } from '../types/domain'
import type { PhotoCrop, PhotoReading, PhotoRotation } from '../types/photoReading'

export const photoReadingsApi = {
  upload: (meterId: string, file: File, options: { signal?: AbortSignal; onUploadProgress?: (event: AxiosProgressEvent) => void } = {}) => {
    const form = new FormData()
    form.append('meterId', meterId)
    form.append('file', file)
    // Persist first: a slow native OCR call must not hold the upload response open.
    form.append('recognize', 'false')
    return apiClient.post<PhotoReading>('/readings/photos', form, { ...options, timeout: 30000 })
  },
  list: (params: { meterId?: string; page: number; pageSize: number }) => apiClient.get<PageResult<PhotoReading>>('/readings/photos', { params, timeout: 15000 }),
  get: (id: number) => apiClient.get<PhotoReading>(`/readings/photos/${id}`, { timeout: 15000 }),
  image: (id: number, signal?: AbortSignal) => apiClient.get<Blob>(`/readings/photos/${id}/image`, { responseType: 'blob', timeout: 15000, signal }),
  recognize: (id: number, rotation: PhotoRotation, crop: PhotoCrop | null, signal?: AbortSignal) => apiClient.post<PhotoReading>(`/readings/photos/${id}/recognize`, { rotation, crop }, { timeout: 55000, signal }),
  confirm: (id: number, reading: string, expectedPreviousReading: string, note: string) => apiClient.post<PhotoReading>(`/readings/photos/${id}/confirm`, { reading, expectedPreviousReading, note: note || undefined }, { timeout: 30000 }),
}
