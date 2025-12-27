
import React, { useState, useEffect, useCallback } from 'react';
import { Tool, FileData, ProcessingState } from '../../types';
import { PDFDocument } from 'pdf-lib';
import * as pdfjsLib from 'pdfjs-dist';

pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://esm.sh/pdfjs-dist@4.10.38/build/pdf.worker.mjs';

interface PdfExtendedData extends FileData {
  pageCount: number;
  thumbnails: string[];
}

interface SplitRange {
  id: string;
  from: number;
  to: number;
}

interface PdfWorkspaceProps {
  tool: Tool;
  files: FileData[];
  onClose: () => void;
  onAddFiles?: (files: File[]) => void;
  onRemoveFile: (id: string) => void;
}

const PdfWorkspace: React.FC<PdfWorkspaceProps> = ({ tool, files, onClose, onAddFiles, onRemoveFile }) => {
  const [extendedFiles, setExtendedFiles] = useState<PdfExtendedData[]>([]);
  const [processing, setProcessing] = useState<ProcessingState>({ status: 'idle', progress: 0 });
  const [ranges, setRanges] = useState<SplitRange[]>([{ id: crypto.randomUUID(), from: 1, to: 1 }]);
  
  const isMerge = tool.id === 'pdf-merge';
  const isSplit = tool.id === 'pdf-split';

  const enrichFiles = useCallback(async () => {
    const enriched = await Promise.all(
      files.map(async (f) => {
        const existing = extendedFiles.find(ex => ex.id === f.id);
        if (existing) return existing;
        try {
          const arrayBuffer = await f.file.arrayBuffer();
          const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;
          const pageCount = pdf.numPages;
          
          // Generate thumbnails for all pages if it's a split tool, otherwise just first
          const thumbsCount = isSplit ? pageCount : 1;
          const thumbnails: string[] = [];
          
          for (let i = 1; i <= Math.min(thumbsCount, 20); i++) { // Limit thumbnails for performance
            const page = await pdf.getPage(i);
            const viewport = page.getViewport({ scale: 0.3 });
            const canvas = document.createElement('canvas');
            canvas.height = viewport.height;
            canvas.width = viewport.width;
            await page.render({ canvasContext: canvas.getContext('2d')!, viewport }).promise;
            thumbnails.push(canvas.toDataURL());
          }
          
          return { ...f, pageCount, thumbnails };
        } catch (e) {
          return { ...f, pageCount: 0, thumbnails: [] };
        }
      })
    );
    setExtendedFiles(enriched);
    
    // Default range for split
    if (isSplit && enriched[0] && ranges.length === 1 && ranges[0].to === 1) {
      setRanges([{ ...ranges[0], to: enriched[0].pageCount }]);
    }
  }, [files, isSplit]);

  useEffect(() => { enrichFiles(); }, [files]);

  const handleMerge = async () => {
    setProcessing({ status: 'processing', progress: 10, message: 'Merging PDFs...' });
    try {
      const mergedPdf = await PDFDocument.create();
      for (let i = 0; i < extendedFiles.length; i++) {
        const fileData = extendedFiles[i];
        const arrayBuffer = await fileData.file.arrayBuffer();
        const pdf = await PDFDocument.load(arrayBuffer);
        const copiedPages = await mergedPdf.copyPages(pdf, pdf.getPageIndices());
        copiedPages.forEach(p => mergedPdf.addPage(p));
        setProcessing(prev => ({ ...prev, progress: Math.floor(((i + 1) / extendedFiles.length) * 100) }));
      }
      const pdfBytes = await mergedPdf.save();
      const arrayBuffer = pdfBytes.buffer.slice(0, pdfBytes.byteLength) as ArrayBuffer;
      const url = URL.createObjectURL(new Blob([arrayBuffer], { type: 'application/pdf' }));
      setProcessing({ status: 'completed', progress: 100, resultUrl: url });
    } catch (e) {
      setProcessing({ status: 'error', progress: 0, message: 'Merge failed' });
    }
  };

  const handleSplit = async (range: SplitRange) => {
    if (!extendedFiles[0]) return;
    setProcessing({ status: 'processing', progress: 20, message: 'Extracting Pages...' });
    
    try {
      const sourceBuffer = await extendedFiles[0].file.arrayBuffer();
      const sourcePdf = await PDFDocument.load(sourceBuffer);
      const splitPdf = await PDFDocument.create();
      
      // pdf-lib is 0-indexed, UI is 1-indexed
      const indices = [];
      for (let i = range.from - 1; i <= range.to - 1; i++) {
        if (i >= 0 && i < sourcePdf.getPageCount()) {
          indices.push(i);
        }
      }
      
      const copiedPages = await splitPdf.copyPages(sourcePdf, indices);
      copiedPages.forEach(p => splitPdf.addPage(p));
      
      const pdfBytes = await splitPdf.save();
      const arrayBuffer = pdfBytes.buffer.slice(0, pdfBytes.byteLength) as ArrayBuffer;
      const url = URL.createObjectURL(new Blob([arrayBuffer], { type: 'application/pdf' }));
      
      const a = document.createElement('a');
      a.href = url;
      a.download = `${extendedFiles[0].file.name.replace('.pdf', '')}-pages-${range.from}-${range.to}.pdf`;
      a.click();
      
      setProcessing({ status: 'completed', progress: 100, resultUrl: url });
    } catch (e) {
      setProcessing({ status: 'error', progress: 0, message: 'Split failed' });
    }
  };

  const addRange = () => {
    const last = ranges[ranges.length - 1];
    const max = extendedFiles[0]?.pageCount || 1;
    setRanges([...ranges, { id: crypto.randomUUID(), from: Math.min(last.to + 1, max), to: max }]);
  };

  const removeRange = (id: string) => {
    if (ranges.length > 1) {
      setRanges(ranges.filter(r => r.id !== id));
    }
  };

  const updateRange = (id: string, field: 'from' | 'to', val: number) => {
    const max = extendedFiles[0]?.pageCount || 1;
    setRanges(ranges.map(r => r.id === id ? { ...r, [field]: Math.min(Math.max(1, val), max) } : r));
  };

  const moveFile = (idx: number, dir: 'up' | 'down') => {
    const newFiles = [...extendedFiles];
    const target = dir === 'up' ? idx - 1 : idx + 1;
    if (target < 0 || target >= newFiles.length) return;
    [newFiles[idx], newFiles[target]] = [newFiles[target], newFiles[idx]];
    setExtendedFiles(newFiles);
  };

  if (isSplit && extendedFiles[0]) {
    return (
      <div className="flex flex-col lg:flex-row h-full overflow-hidden bg-white">
        {/* Left Side: Page Preview Grid */}
        <div className="flex-1 overflow-y-auto p-4 md:p-8 bg-gray-50/40">
          <div className="max-w-5xl mx-auto">
            <div className="mb-8 flex justify-between items-center">
              <div>
                <h3 className="text-xl font-bold text-gray-900">Document Pages</h3>
                <p className="text-xs text-gray-400 font-bold uppercase tracking-widest mt-1">
                  {extendedFiles[0].file.name} • {extendedFiles[0].pageCount} Total Pages
                </p>
              </div>
            </div>
            
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4 md:gap-6">
              {Array.from({ length: extendedFiles[0].pageCount }).map((_, i) => {
                const pageNum = i + 1;
                const isInRange = ranges.some(r => pageNum >= r.from && pageNum <= r.to);
                return (
                  <div key={i} className={`relative bg-white p-3 rounded-2xl border transition-all ${isInRange ? 'border-orange-400 shadow-lg scale-105' : 'border-gray-100 opacity-60'}`}>
                    <div className="aspect-[3/4] bg-gray-50 rounded-xl overflow-hidden flex items-center justify-center mb-2 border border-gray-50">
                      {extendedFiles[0].thumbnails[i] ? (
                        <img src={extendedFiles[0].thumbnails[i]} alt={`page ${pageNum}`} className="w-full h-full object-contain p-1" />
                      ) : (
                        <div className="text-[10px] text-gray-300 font-bold">PAGE {pageNum}</div>
                      )}
                    </div>
                    <div className="text-center">
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${isInRange ? 'bg-orange-500 text-white' : 'bg-gray-100 text-gray-400'}`}>
                        {pageNum}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Right Side: Range Controls */}
        <div className="w-full lg:w-96 bg-white border-t lg:border-t-0 lg:border-l border-gray-100 flex flex-col shrink-0 shadow-2xl z-20">
          <div className="p-6 md:p-8 border-b border-gray-50">
            <h2 className="text-lg md:text-xl font-bold text-gray-900 mb-1">Split Settings</h2>
            <p className="text-[10px] text-gray-400 font-bold uppercase tracking-widest">Define custom ranges</p>
          </div>

          <div className="flex-1 overflow-y-auto p-6 md:p-8 space-y-6">
            <div className="space-y-4">
              {ranges.map((range, idx) => (
                <div key={range.id} className="p-4 bg-orange-50/50 rounded-2xl border border-orange-100 relative group">
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-[10px] font-bold text-orange-600 uppercase">Range {idx + 1}</span>
                    {ranges.length > 1 && (
                      <button onClick={() => removeRange(range.id)} className="text-orange-300 hover:text-red-500 transition-colors">✕</button>
                    )}
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="text-[9px] font-bold text-orange-400 uppercase block mb-1">From Page</label>
                      <input 
                        type="number" 
                        value={range.from} 
                        onChange={(e) => updateRange(range.id, 'from', parseInt(e.target.value))}
                        className="w-full px-3 py-2 bg-white border border-orange-200 rounded-xl outline-none font-bold text-orange-900 text-sm"
                      />
                    </div>
                    <div>
                      <label className="text-[9px] font-bold text-orange-400 uppercase block mb-1">To Page</label>
                      <input 
                        type="number" 
                        value={range.to} 
                        onChange={(e) => updateRange(range.id, 'to', parseInt(e.target.value))}
                        className="w-full px-3 py-2 bg-white border border-orange-200 rounded-xl outline-none font-bold text-orange-900 text-sm"
                      />
                    </div>
                  </div>
                  <button 
                    onClick={() => handleSplit(range)}
                    className="w-full mt-4 py-2 bg-orange-500 text-white text-xs font-bold rounded-xl hover:bg-orange-600 transition-all shadow-md active:scale-95"
                  >
                    Download this Range
                  </button>
                </div>
              ))}

              <button 
                onClick={addRange}
                className="w-full py-4 border-2 border-dashed border-gray-200 rounded-2xl text-xs font-bold text-gray-400 hover:border-orange-300 hover:text-orange-500 hover:bg-orange-50 transition-all"
              >
                + Add Another Range
              </button>
            </div>
          </div>

          <div className="p-6 md:p-8 bg-gray-50/50 border-t border-gray-100">
            <p className="text-[10px] text-gray-400 mb-4 text-center leading-relaxed">
              Splitting will create separate PDF files for each range defined above.
            </p>
            <button 
              onClick={onClose}
              className="w-full py-4 bg-gray-900 text-white font-bold rounded-2xl shadow-xl hover:bg-black transition-all active:scale-95 text-sm"
            >
              Done & Return
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Fallback / Merge View
  return (
    <div className="flex flex-col lg:flex-row h-full overflow-hidden bg-white">
      <div className="flex-1 overflow-y-auto p-4 md:p-8 bg-gray-50/40">
        <div className="max-w-5xl mx-auto">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
            <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest">Merge Queue ({extendedFiles.length})</h3>
            {onAddFiles && (
              <label className="cursor-pointer px-4 py-2 bg-white border border-gray-200 rounded-xl text-xs font-bold text-indigo-600 hover:border-indigo-300 shadow-sm transition-all text-center">
                + Add Documents
                <input type="file" multiple className="hidden" accept=".pdf" onChange={(e) => onAddFiles(Array.from(e.target.files || []))} />
              </label>
            )}
          </div>
          
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-6">
            {extendedFiles.map((file, idx) => (
              <div key={file.id} className="bg-white p-4 rounded-[2rem] border border-gray-100 shadow-sm relative group hover:shadow-xl transition-all">
                <div className="aspect-[3/4] bg-gray-50 rounded-[1.5rem] mb-4 overflow-hidden flex items-center justify-center border border-gray-100 group-hover:bg-indigo-50/20 transition-colors">
                  {file.thumbnails[0] ? (
                    <img src={file.thumbnails[0]} alt="preview" className="w-full h-full object-contain p-4" />
                  ) : (
                    <div className="animate-pulse bg-gray-100 w-full h-full" />
                  )}
                </div>
                <div className="space-y-1 px-1">
                  <p className="text-xs font-bold truncate text-gray-900">{file.file.name}</p>
                  <p className="text-[10px] text-gray-400 font-bold uppercase">{file.pageCount} Pages</p>
                </div>
                <div className="mt-4 flex items-center justify-between gap-2">
                  <div className="flex bg-gray-100 p-1 rounded-xl">
                    <button disabled={idx === 0} onClick={() => moveFile(idx, 'up')} className="w-8 h-8 flex items-center justify-center text-xs hover:bg-white rounded-lg disabled:opacity-20 transition-all">↑</button>
                    <button disabled={idx === extendedFiles.length - 1} onClick={() => moveFile(idx, 'down')} className="w-8 h-8 flex items-center justify-center text-xs hover:bg-white rounded-lg disabled:opacity-20 transition-all">↓</button>
                  </div>
                  <button onClick={() => onRemoveFile(file.id)} className="w-8 h-8 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-xl transition-all flex items-center justify-center">✕</button>
                </div>
                <div className="absolute top-4 left-4 bg-indigo-600 text-white text-[10px] font-bold px-2 py-0.5 rounded-full shadow-lg">#{idx + 1}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="w-full lg:w-96 bg-white border-t lg:border-t-0 lg:border-l border-gray-100 flex flex-col p-6 md:p-8 shrink-0 shadow-2xl z-20">
        <div className="flex-1">
          <h2 className="text-lg md:text-xl font-bold text-gray-900 mb-2">Configure Merge</h2>
          <p className="text-xs text-gray-400 mb-8 uppercase tracking-widest font-bold">Document Order</p>
          
          <div className="p-6 bg-indigo-50/50 rounded-[2rem] border border-indigo-100 space-y-4">
             <div className="flex items-center space-x-3">
               <div className="w-10 h-10 bg-white rounded-xl flex items-center justify-center text-indigo-600 font-bold shadow-sm">{extendedFiles.length}</div>
               <span className="text-xs font-bold text-indigo-900 uppercase">Total Files</span>
             </div>
             <p className="text-[11px] text-indigo-700 leading-relaxed font-medium"> Documents will be combined exactly as they appear in the queue. You can use arrows to reorder. </p>
          </div>
        </div>

        <div className="mt-8">
          {processing.status === 'completed' ? (
            <button 
              onClick={() => { window.open(processing.resultUrl); onClose(); }} 
              className="w-full bg-green-600 text-white font-bold py-4.5 rounded-2xl hover:bg-green-700 transition-all shadow-xl flex items-center justify-center space-x-2 active:scale-95"
            >
              <span className="text-xl">💾</span> <span>Download Combined PDF</span>
            </button>
          ) : (
            <button 
              disabled={processing.status === 'processing' || extendedFiles.length < 2}
              onClick={handleMerge}
              className="w-full bg-indigo-600 text-white font-bold py-4.5 rounded-2xl shadow-xl transition-all hover:bg-indigo-700 disabled:opacity-30 active:scale-95 text-sm"
            >
              {processing.status === 'processing' ? 'Processing...' : `Merge ${extendedFiles.length} PDF Documents`}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default PdfWorkspace;
