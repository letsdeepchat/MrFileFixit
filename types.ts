
export type ToolCategory = 'image' | 'pdf' | 'advanced';

export type ImageUnit = 'px' | 'cm' | 'inch';
export type ExportFormat = 'image/jpeg' | 'image/png' | 'application/pdf';

export interface Tool {
  id: string;
  name: string;
  description: string;
  icon: string;
  category: ToolCategory;
  color: string;
}

export interface FileData {
  file: File;
  previewUrl: string;
  id: string;
}

export interface ProcessingState {
  status: 'idle' | 'processing' | 'completed' | 'error';
  progress: number;
  message?: string;
  resultUrl?: string;
}
