import { apiClient } from './client'
import type { PageResult } from '../types/domain'
import type { PhotoCrop, PhotoReading, PhotoRotation } from '../types/photoReading'

export const photoReadingsApi = {
  upload: (meterId: string, file: File) => {
    const form = new FormData()
    form.append('meterId', meterId)
    form.append('file', file)
    return apiClient.post<PhotoReading>('/readings/photos', form)
  },
  list: (params: { meterId?: string; page: number; pageSize: number }) => apiClient.get<PageResult<PhotoReading>>('/readings/photos', { params }),
  get: (id: number) => apiClient.get<PhotoReading>(`/readings/photos/${id}`),
  image: (id: number) => apiClient.get<Blob>(`/readings/photos/${id}/image`, { responseType: 'blob' }),
  recognize: (id: number, rotation: PhotoRotation, crop: PhotoCrop | null) => apiClient.post<PhotoReading>(`/readings/photos/${id}/recognize`, { rotation, crop }),
  confirm: (id: number, reading: string, expectedPreviousReading: string, note: string) => apiClient.post<PhotoReading>(`/readings/photos/${id}/confirm`, { reading, expectedPreviousReading, note: note || undefined }),
}
