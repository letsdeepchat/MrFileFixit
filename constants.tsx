import React from 'react';
import { Tool } from './types';

export const TOOLS: Tool[] = [
  {
    id: 'image-resize',
    name: 'Resize & Crop',
    description: 'Perfectly scale and crop your images with aspect ratio control.',
    icon: '📐',
    category: 'image',
    color: 'bg-blue-500'
  },
  {
    id: 'image-compress',
    name: 'Compress Image',
    description: 'Reduce file size without losing quality.',
    icon: '🗜️',
    category: 'image',
    color: 'bg-indigo-500'
  },
  {
    id: 'image-target-size',
    name: 'Compress to KB',
    description: 'Target a specific file size (e.g., 50KB) for strict upload limits.',
    icon: '⚖️',
    category: 'image',
    color: 'bg-cyan-500'
  },
  {
    id: 'image-bg-remove',
    name: 'Remove Background',
    description: 'Instantly strip backgrounds from photos locally in your browser.',
    icon: '👤',
    category: 'image',
    color: 'bg-purple-500'
  },
  {
    id: 'pdf-merge',
    name: 'Merge PDF',
    description: 'Combine multiple PDF files into one document.',
    icon: '📄',
    category: 'pdf',
    color: 'bg-red-500'
  },
  {
    id: 'pdf-split',
    name: 'Split PDF',
    description: 'Extract pages or separate a PDF into multiple files.',
    icon: '✂️',
    category: 'pdf',
    color: 'bg-orange-500'
  },
  {
    id: 'ocr-scanner',
    name: 'OCR Scanner',
    description: 'Extract text from images locally using the Tesseract engine.',
    icon: '🔍',
    category: 'advanced',
    color: 'bg-emerald-500'
  }
];