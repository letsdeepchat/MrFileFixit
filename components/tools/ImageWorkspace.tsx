
import React, { useState, useRef, useEffect } from 'react';
import { Tool, FileData, ProcessingState, ExportFormat } from '../../types';
import { jsPDF } from 'jspdf';
import { removeBackground, Config } from '@imgly/background-removal';

interface ImageWorkspaceProps {
  tool: Tool;
  fileData: FileData;
  onClose: () => void;
}

const ImageWorkspace: React.FC<ImageWorkspaceProps> = ({ tool, fileData, onClose }) => {
  const [format, setFormat] = useState<ExportFormat>('image/jpeg');
  const [quality, setQuality] = useState<number>(tool.id === 'image-compress' ? 50 : 85);
  const [targetSizeKb, setTargetSizeKb] = useState<number>(100);
  const [processing, setProcessing] = useState<ProcessingState>({ status: 'idle', progress: 0 });
  const [isImageReady, setIsImageReady] = useState<boolean>(false);

  const imgRef = useRef<HTMLImageElement>(null);
  const currentResultUrl = useRef<string | null>(null);

  const isBgRemove = tool.id === 'image-bg-remove';
  const isTargetSize = tool.id === 'image-target-size';

  useEffect(() => {
    return () => {
      if (currentResultUrl.current) URL.revokeObjectURL(currentResultUrl.current);
    };
  }, []);

  const handleImageLoad = () => {
    if (imgRef.current?.naturalWidth) {
      setIsImageReady(true);
    }
  };

  const findBestQuality = async (canvas: HTMLCanvasElement, targetBytes: number): Promise<Blob> => {
    let low = 0.05, high = 0.95, bestBlob: Blob | null = null;
    for (let i = 0; i < 8; i++) {
      const mid = (low + high) / 2;
      const blob: Blob | null = await new Promise(res => canvas.toBlob(res, 'image/jpeg', mid));
      if (!blob) break;
      if (blob.size <= targetBytes) { bestBlob = blob; low = mid; } else { high = mid; }
    }
    if (!bestBlob) {
      const fallback: Blob | null = await new Promise(res => canvas.toBlob(res, 'image/jpeg', 0.1));
      if (!fallback) throw new Error("Processing error");
      return fallback;
    }
    return bestBlob;
  };

  const handleProcess = async () => {
    setProcessing({ status: 'processing', progress: 0, message: 'Initializing...' });
    if (currentResultUrl.current) URL.revokeObjectURL(currentResultUrl.current);

    try {
      let finalUrl = "";
      if (isBgRemove) {
        // Fix for WASM multi-threading warnings:
        // Explicitly check for crossOriginIsolated. If false, we must restrict threads to 1
        // to avoid the "numThreads is set to 4 but will not work" and fallback warnings.
        const config: Config = {
          progress: (step, progress) => {
            setProcessing(prev => ({ 
              ...prev, progress: Math.floor(progress * 100), 
              message: step.includes('compute') ? 'Removing Background...' : 'Warming Up...' 
            }));
          }
        };

        // Inject environment settings into the library's underlying engine (ONNX Runtime Web)
        // This suppresses the specific warning mentioned by the user.
        (config as any).model = 'medium';
        // We set the numThreads to 1 if we are not in a cross-origin isolated context.
        // This is a common requirement for high-performance WASM libraries.
        if (!(window as any).crossOriginIsolated) {
          (config as any).env = {
            wasm: {
              numThreads: 1
            }
          };
        }

        const resultBlob = await removeBackground(fileData.file, config);
        finalUrl = URL.createObjectURL(resultBlob);
      } else {
        const canvas = document.createElement('canvas');
        const img = imgRef.current;
        if (!img) throw new Error("Source unavailable");
        canvas.width = img.naturalWidth;
        canvas.height = img.naturalHeight;
        const ctx = canvas.getContext('2d');
        if (!ctx) throw new Error("Canvas failure");
        ctx.drawImage(img, 0, 0);
        
        if (isTargetSize) {
          const blob = await findBestQuality(canvas, targetSizeKb * 1024);
          finalUrl = URL.createObjectURL(blob);
        } else if (format === 'application/pdf') {
          const pdf = new jsPDF({ 
            orientation: canvas.width > canvas.height ? 'l' : 'p', 
            unit: 'px', format: [canvas.width, canvas.height] 
          });
          pdf.addImage(canvas.toDataURL('image/jpeg', 0.9), 'JPEG', 0, 0, canvas.width, canvas.height);
          finalUrl = URL.createObjectURL(pdf.output('blob'));
        } else {
          const blob: Blob | null = await new Promise(res => canvas.toBlob(res, format, quality / 100));
          if (!blob) throw new Error("Export failure");
          finalUrl = URL.createObjectURL(blob);
        }
      }
      currentResultUrl.current = finalUrl;
      setProcessing({ status: 'completed', progress: 100, message: 'Done!', resultUrl: finalUrl });
    } catch (err: any) {
      console.error(err);
      setProcessing({ status: 'error', progress: 0, message: 'Operation failed' });
    }
  };

  const handleDownload = () => {
    if (processing.resultUrl) {
      const a = document.createElement('a');
      a.href = processing.resultUrl;
      const base = fileData.file.name.split('.')[0].replace(/[^a-z0-9]/gi, '-').toLowerCase();
      const ext = format === 'application/pdf' ? 'pdf' : (format === 'image/png' ? 'png' : 'jpg');
      a.download = `${base}-fixit.${ext}`;
      a.click();
    }
  };

  return (
    <div className="flex flex-col lg:flex-row h-full overflow-hidden">
      <div className="flex-1 bg-gray-50/40 p-4 md:p-8 flex items-center justify-center overflow-hidden min-h-[300px]">
        <div className="relative w-full h-full flex items-center justify-center">
          <div className="relative bg-white p-3 md:p-4 rounded-2xl md:rounded-[2.5rem] shadow-2xl border border-gray-100 max-w-full max-h-full flex items-center justify-center overflow-hidden">
             <img 
              ref={imgRef}
              src={processing.resultUrl || fileData.previewUrl} 
              className={`max-w-full max-h-[60vh] lg:max-h-full object-contain rounded-xl md:rounded-[2rem] transition-all duration-500 ${processing.status === 'processing' ? 'opacity-20 blur-xl scale-95' : 'opacity-100 scale-100'}`} 
              onLoad={handleImageLoad}
              alt="Workspace" 
            />
            {processing.status === 'processing' && (
              <div className="absolute inset-0 flex items-center justify-center z-50">
                <div className="bg-white/95 backdrop-blur-xl px-8 py-8 rounded-[2rem] shadow-[0_32px_64px_-16px_rgba(0,0,0,0.1)] border border-gray-100 flex flex-col items-center w-64 text-center">
                  <div className="relative w-16 h-16 mb-6">
                    <div className="absolute inset-0 border-4 border-indigo-100 rounded-full"></div>
                    <div className="absolute inset-0 border-4 border-indigo-600 border-t-transparent rounded-full animate-spin"></div>
                  </div>
                  <p className="text-gray-900 font-bold text-base mb-4">{processing.message}</p>
                  <div className="w-full bg-gray-100 rounded-full h-2 overflow-hidden">
                    <div 
                      className="bg-indigo-600 h-full transition-all duration-300 ease-out rounded-full" 
                      style={{ width: `${Math.max(5, processing.progress)}%` }} 
                    />
                  </div>
                  <span className="mt-3 text-[10px] font-bold text-indigo-600 uppercase tracking-widest">{processing.progress}% Complete</span>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="w-full lg:w-96 bg-white border-t lg:border-t-0 lg:border-l border-gray-100 flex flex-col shadow-2xl shrink-0 z-20">
        <div className="p-5 md:p-8 border-b border-gray-50 bg-white/80 backdrop-blur-md sticky top-0 lg:relative">
          <h2 className="text-lg md:text-xl font-bold text-gray-900 flex items-center">
            <span className="mr-3" aria-hidden="true">{tool.icon}</span> {tool.name}
          </h2>
          <p className="text-xs text-gray-400 mt-1 uppercase tracking-widest font-bold">Local Engine</p>
        </div>

        <div className="flex-1 overflow-y-auto p-6 md:p-8 space-y-8">
          {!isBgRemove && (
            <div className="space-y-6">
              <div className="space-y-3">
                <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">Output Format</label>
                <div className="grid grid-cols-3 gap-2">
                  {['JPG', 'PNG', 'PDF'].map(ext => {
                    const mime = ext === 'JPG' ? 'image/jpeg' : (ext === 'PNG' ? 'image/png' : 'application/pdf');
                    return (
                      <button 
                        key={ext}
                        onClick={() => setFormat(mime as ExportFormat)}
                        className={`py-3 text-xs font-bold rounded-xl border transition-all ${format === mime ? 'bg-indigo-600 text-white border-indigo-600 shadow-lg' : 'bg-white text-gray-400 border-gray-100 hover:border-indigo-200'}`}
                      >
                        {ext}
                      </button>
                    );
                  })}
                </div>
              </div>

              {isTargetSize ? (
                <div className="p-6 bg-cyan-50/50 rounded-2xl border border-cyan-100">
                  <label className="text-[10px] font-bold text-cyan-600 uppercase tracking-widest block mb-2">Max File Size (KB)</label>
                  <input 
                    type="number" 
                    value={targetSizeKb} 
                    onChange={(e) => setTargetSizeKb(Math.max(1, parseInt(e.target.value) || 1))}
                    className="w-full px-4 py-3 bg-white border border-cyan-200 rounded-xl outline-none font-bold text-cyan-900 text-lg" 
                  />
                  <p className="mt-2 text-[10px] text-cyan-500 font-medium">We'll auto-adjust quality to meet this limit.</p>
                </div>
              ) : (
                format !== 'image/png' && (
                  <div className="space-y-4">
                    <div className="flex justify-between items-center">
                      <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">Output Quality</label>
                      <span className="text-sm font-bold text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded-lg">{quality}%</span>
                    </div>
                    <input 
                      type="range" min="10" max="100" 
                      value={quality} onChange={(e) => setQuality(parseInt(e.target.value))}
                      className="w-full h-1.5 bg-gray-100 rounded-lg appearance-none cursor-pointer accent-indigo-600" 
                    />
                  </div>
                )
              )}
            </div>
          )}

          {isBgRemove && (
            <div className="space-y-4">
              <div className="bg-purple-50 p-6 rounded-[2rem] border border-purple-100">
                <p className="text-xs text-purple-700 leading-relaxed font-bold italic">
                  Note: Large files ({'>'} 10MB) may take significant memory. Ensure other apps are closed for best performance.
                </p>
              </div>
              <div className="flex items-center p-4 bg-gray-50 rounded-2xl">
                 <div className="w-10 h-10 bg-white rounded-xl shadow-sm flex items-center justify-center mr-4 text-purple-500">🔒</div>
                 <p className="text-[10px] text-gray-400 font-bold uppercase">100% Secure Private Processing</p>
              </div>
            </div>
          )}
        </div>

        <div className="p-6 md:p-8 bg-gray-50/30 border-t border-gray-100 sticky bottom-0 lg:relative">
          {processing.status === 'completed' ? (
            <button 
              onClick={handleDownload}
              className="w-full bg-green-600 text-white font-bold py-4.5 rounded-2xl hover:bg-green-700 transition-all shadow-xl flex items-center justify-center space-x-2 active:scale-95"
            >
              <span className="text-xl">💾</span> <span className="text-sm md:text-base">Download File</span>
            </button>
          ) : (
            <button 
              disabled={processing.status === 'processing' || !isImageReady}
              onClick={handleProcess}
              className={`w-full text-white font-bold py-4.5 rounded-2xl shadow-xl transition-all active:scale-95 disabled:opacity-50 text-sm md:text-base ${isBgRemove ? 'bg-purple-600 hover:bg-purple-700' : 'bg-indigo-600 hover:bg-indigo-700'}`}
            >
              {processing.status === 'processing' ? 'Processing...' : 'Generate New File'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default ImageWorkspace;
