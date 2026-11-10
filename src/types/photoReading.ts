export type PhotoRotation = 0 | 90 | 180 | 270
export interface PhotoCrop { x: number; y: number; width: number; height: number }
export interface PhotoReading {
  id: number
  meterId: string
  meterUserName: string
  area: string
  status: 'DRAFT' | 'CONFIRMED'
  createdAt: string
  uploadedBy: string
  imageWidth: number
  imageHeight: number
  imageMime: 'image/png'
  previousReading: string
  rawText: string
  candidates: string[]
  suggestedReading: string | null
  confidence: number | null
  engine: string
  ocrError: string | null
  rotation: PhotoRotation
  crop: PhotoCrop | null
  recognizedAt: string | null
  confirmedReading: string | null
  confirmedPreviousReading: string | null
  confirmedBy: string | null
  confirmedAt: string | null
  manuallyEdited: boolean
  note: string | null
  readingId: number | null
}
