
import React, { useState, useEffect } from 'react';
import { Tool, FileData, ProcessingState } from '../../types';
import Tesseract from 'tesseract.js';

interface OcrWorkspaceProps {
  tool: Tool;
  fileData: FileData;
  onClose: () => void;
}

const OcrWorkspace: React.FC<OcrWorkspaceProps> = ({ tool, fileData, onClose }) => {
  const [extractedText, setExtractedText] = useState<string>("");
  const [processing, setProcessing] = useState<ProcessingState>({ status: 'idle', progress: 0 });

  // Reset text when a new file is loaded
  useEffect(() => {
    setExtractedText("");
    setProcessing({ status: 'idle', progress: 0 });
  }, [fileData.id]);

  const handleScan = async () => {
    if (processing.status === 'processing') return;

    setProcessing({ status: 'processing', progress: 0, message: 'Initializing OCR engine...' });
    
    try {
      const result = await Tesseract.recognize(
        fileData.file,
        'eng',
        {
          logger: (m: any) => {
            if (m.status === 'recognizing text') {
              setProcessing(prev => ({
                ...prev,
                progress: Math.floor(m.progress * 100),
                message: 'Analyzing characters...'
              }));
            } else if (m.status === 'loading tesseract core') {
              setProcessing(prev => ({ ...prev, message: 'Loading core engine...' }));
            }
          }
        }
      );
      
      if (result && result.data && result.data.text) {
        setExtractedText(result.data.text);
        setProcessing({ status: 'completed', progress: 100, message: 'Scan complete!' });
      } else {
        throw new Error("No text found in image.");
      }
    } catch (e: any) {
      console.error("OCR Error:", e);
      setProcessing({ 
        status: 'error', 
        progress: 0, 
        message: e.message || 'Error analyzing image. Please try a clearer photo.' 
      });
    }
  };

  const copyToClipboard = () => {
    if (!extractedText) return;
    navigator.clipboard.writeText(extractedText);
    alert("Copied to clipboard!");
  };

  const downloadTxt = () => {
    if (!extractedText) return;
    const blob = new Blob([extractedText], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ocr-extract-${Date.now()}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="flex flex-col lg:flex-row h-full overflow-hidden bg-white">
      {/* Image Preview Side */}
      <div className="flex-1 bg-gray-50/50 p-4 md:p-8 flex items-center justify-center overflow-hidden min-h-[300px]">
        <div className="w-full h-full flex items-center justify-center relative">
          <div className="relative bg-white p-2 md:p-4 rounded-[2rem] shadow-2xl border border-gray-100 max-w-full max-h-full flex items-center justify-center">
            <img 
              src={fileData.previewUrl} 
              className={`max-w-full max-h-[60vh] lg:max-h-[80vh] object-contain rounded-xl md:rounded-[1.5rem] transition-all duration-500 ${processing.status === 'processing' ? 'opacity-30 blur-sm' : 'opacity-100'}`} 
              alt="OCR Source" 
            />
            
            <div className="absolute -top-3 -left-3 bg-emerald-600 text-white text-[10px] font-extrabold px-4 py-1.5 rounded-full uppercase tracking-[0.2em] shadow-lg z-10">
              Ready to Scan
            </div>

            {processing.status === 'processing' && (
              <div className="absolute inset-0 flex flex-col items-center justify-center z-20">
                <div className="bg-white/95 backdrop-blur-xl px-10 py-8 rounded-[2.5rem] shadow-2xl border border-gray-100 flex flex-col items-center max-w-xs w-full text-center">
                  <div className="relative w-16 h-16 mb-6">
                    <div className="absolute inset-0 border-4 border-emerald-100 rounded-full"></div>
                    <div className="absolute inset-0 border-4 border-emerald-500 border-t-transparent rounded-full animate-spin"></div>
                  </div>
                  <p className="text-gray-900 font-bold text-base mb-4">{processing.message}</p>
                  <div className="w-full bg-gray-100 rounded-full h-2.5 overflow-hidden">
                    <div 
                      className="bg-emerald-500 h-full transition-all duration-500 ease-out" 
                      style={{ width: `${Math.max(5, processing.progress)}%` }} 
                    />
                  </div>
                  <span className="mt-4 text-[10px] font-black text-emerald-600 uppercase tracking-widest">{processing.progress}% Complete</span>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Results Side */}
      <div className="w-full lg:w-[480px] bg-white border-t lg:border-t-0 lg:border-l border-gray-100 flex flex-col shadow-2xl z-10 shrink-0">
        <div className="p-6 md:p-8 border-b border-gray-50 flex items-center justify-between bg-white/80 backdrop-blur sticky top-0">
          <div>
            <h2 className="text-xl md:text-2xl font-black text-gray-900 flex items-center">
              <span className="mr-3" aria-hidden="true">{tool.icon}</span> OCR Results
            </h2>
            <p className="text-[10px] text-gray-400 font-bold uppercase tracking-widest mt-1">Processed locally on device</p>
          </div>
          {extractedText && (
            <div className="flex space-x-2">
              <button onClick={copyToClipboard} className="w-10 h-10 bg-emerald-50 text-emerald-600 rounded-xl flex items-center justify-center hover:bg-emerald-100 transition-colors border border-emerald-100 shadow-sm" title="Copy Text">
                📋
              </button>
              <button onClick={downloadTxt} className="w-10 h-10 bg-emerald-50 text-emerald-600 rounded-xl flex items-center justify-center hover:bg-emerald-100 transition-colors border border-emerald-100 shadow-sm" title="Download TXT">
                💾
              </button>
            </div>
          )}
        </div>

        <div className="flex-1 overflow-hidden p-6 md:p-8">
          <div className="h-full bg-gray-50/50 rounded-[2rem] border border-gray-100 overflow-hidden flex flex-col shadow-inner">
            <div className="px-5 py-3 bg-gray-100/50 border-b border-gray-200/50 flex items-center justify-between">
              <span className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">Extracted Content</span>
              {extractedText && (
                <span className="text-[9px] font-black text-white bg-emerald-500 px-2 py-0.5 rounded-md uppercase">
                  {extractedText.length} Characters
                </span>
              )}
            </div>
            
            <div className="flex-1 relative overflow-hidden">
              {!extractedText && processing.status !== 'processing' ? (
                <div className="absolute inset-0 flex flex-col items-center justify-center p-12 text-center">
                  <div className="w-20 h-20 bg-white rounded-3xl shadow-sm flex items-center justify-center text-3xl mb-6 border border-gray-100 opacity-60">
                    📄
                  </div>
                  <h4 className="text-sm font-bold text-gray-400 uppercase tracking-widest mb-2">No Text Extracted Yet</h4>
                  <p className="text-xs text-gray-300 max-w-[200px] leading-relaxed">Click the button below to start scanning this document.</p>
                </div>
              ) : (
                <textarea
                  readOnly
                  value={extractedText}
                  className="w-full h-full bg-transparent p-6 md:p-8 font-mono text-sm leading-relaxed resize-none focus:outline-none text-gray-700 selection:bg-emerald-100 selection:text-emerald-900"
                  placeholder="Scanning results will appear here..."
                />
              )}
            </div>
          </div>
        </div>

        <div className="p-6 md:p-8 bg-gray-50/50 border-t border-gray-100">
          <button 
            disabled={processing.status === 'processing'}
            onClick={handleScan}
            className={`w-full font-bold py-5 rounded-2xl shadow-xl flex items-center justify-center space-x-3 transition-all active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed ${
              extractedText 
                ? 'bg-white border-2 border-emerald-600 text-emerald-600 hover:bg-emerald-50' 
                : 'bg-emerald-600 text-white hover:bg-emerald-700 shadow-emerald-100'
            }`}
          >
            {processing.status === 'processing' ? (
              <>
                <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                <span>Extracting Text...</span>
              </>
            ) : (
              <span>{extractedText ? 'Re-scan Image' : 'Start OCR Scan'}</span>
            )}
          </button>
          
          {processing.status === 'error' && (
            <p className="mt-4 text-center text-[10px] font-bold text-red-500 uppercase tracking-widest bg-red-50 py-2 rounded-lg border border-red-100 animate-shake">
              ⚠️ {processing.message}
            </p>
          )}
        </div>
      </div>
    </div>
  );
};

export default OcrWorkspace;
