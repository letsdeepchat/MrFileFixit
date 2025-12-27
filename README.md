# 📄 MrFileFixit

**MrFileFixit** is a professional-grade, local-first file utility workshop. It provides a suite of high-performance tools for images, PDFs, and document processing—all running entirely in your browser with 100% privacy.

![GitHub License](https://img.shields.io/github/license/letsdeepchat/mr-file-fixit?style=flat-square)
![Privacy First](https://img.shields.io/badge/Privacy-100%25%20Local-emerald?style=flat-square)

## 🌟 Features

### 🖼️ Image Workshop
- **Resize & Crop**: Scale images with aspect ratio preservation.
- **Compression**: Reduce file size with lossless and lossy modes.
- **Target Size (KB)**: Auto-optimize images to hit a specific file size limit (e.g., exactly 50KB).
- **Background Removal**: AI-powered background stripping running locally via WASM.

### 📄 PDF Suite
- **Merge PDF**: Combine multiple documents into a single professional file.
- **Split PDF**: Extract specific page ranges or split a document into individual pages.

### 🔍 OCR Scanner
- **Optical Character Recognition**: Extract text from images and scans using the Tesseract engine.
- **Local Processing**: No data is sent to external servers for analysis.

## 🛡️ Privacy First
MrFileFixit uses **Local-First Architecture**. Your files never leave your computer. 
- All processing happens in your browser's RAM/GPU.
- No database, no uploads, no cloud logging.
- Offline-ready functionality.

## 🛠️ Tech Stack
- **Framework**: React 19 + TypeScript
- **Bundler**: Vite
- **Styling**: Tailwind CSS
- **Core Libraries**:
  - `pdf-lib` & `pdfjs-dist`: Advanced PDF manipulation.
  - `tesseract.js`: Browser-side OCR engine.
  - `@imgly/background-removal`: WASM-based background removal.
  - `jspdf`: PDF generation from images.

## 🚀 Getting Started

### Prerequisites
- Node.js (v18 or higher)
- npm or yarn

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/mr-file-fixit.git
   ```
2. Navigate to the project folder:
   ```bash
   cd mr-file-fixit
   ```
3. Install dependencies:
   ```bash
   npm install
   ```
4. Start the development server:
   ```bash
   npm run dev
   ```

## ⚖️ License
Distributed under the MIT License. See `LICENSE` for more information.

---
Built with ❤️ for privacy by [@letsdeepchat](https://github.com/letsdeepchat)