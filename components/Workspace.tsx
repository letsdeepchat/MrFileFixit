import React, { useState, useEffect, useCallback } from 'react';
import { Tool, FileData } from '../types';
import ImageWorkspace from './tools/ImageWorkspace';
import OcrWorkspace from './tools/OcrWorkspace';
import PdfWorkspace from './tools/PdfWorkspace';

interface WorkspaceProps {
  tool: Tool;
  onBack: () => void;
}

const Workspace: React.FC<WorkspaceProps> = ({ tool, onBack }) => {
  const [fileDataList, setFileDataList] = useState<FileData[]>([]);
  const isMultiFile = tool.id === 'pdf-merge';

  useEffect(() => {
    return () => {
      fileDataList.forEach(f => {
        if (f.previewUrl.startsWith('blob:')) {
          URL.revokeObjectURL(f.previewUrl);
        }
      });
    };
  }, [fileDataList]);

  const onFilesSelect = useCallback((e: React.ChangeEvent<HTMLInputElement> | File[]) => {
    const rawFiles = Array.isArray(e) ? e : Array.from(e.target.files || []);
    if (rawFiles.length === 0) return;

    const newData = rawFiles.map(file => ({
      file,
      previewUrl: URL.createObjectURL(file),
      id: crypto.randomUUID()
    }));

    setFileDataList(prev => {
      if (!isMultiFile) {
        prev.forEach(f => URL.revokeObjectURL(f.previewUrl));
        return newData.slice(0, 1);
      }
      return [...prev, ...newData];
    });
  }, [isMultiFile]);

  const removeFile = useCallback((id: string) => {
    setFileDataList(prev => {
      const file = prev.find(f => f.id === id);
      if (file) URL.revokeObjectURL(file.previewUrl);
      return prev.filter(f => f.id !== id);
    });
  }, []);

  const clearAll = useCallback(() => {
    fileDataList.forEach(f => URL.revokeObjectURL(f.previewUrl));
    setFileDataList([]);
  }, [fileDataList]);

  const renderTool = () => {
    if (fileDataList.length === 0) return null;

    if (tool.category === 'image') {
      return <ImageWorkspace tool={tool} fileData={fileDataList[0]} onClose={onBack} />;
    }

    if (tool.category === 'pdf') {
      return (
        <PdfWorkspace 
          tool={tool} 
          files={fileDataList} 
          onClose={onBack} 
          onAddFiles={onFilesSelect}
          onRemoveFile={removeFile}
        />
      );
    }

    if (tool.id === 'ocr-scanner') {
      return <OcrWorkspace tool={tool} fileData={fileDataList[0]} onClose={onBack} />;
    }

    return (
      <div className="flex flex-col items-center justify-center h-full space-y-4 p-8 text-center" role="alert">
        <div className="text-6xl mb-4 animate-bounce">🚧</div>
        <h2 className="text-2xl font-bold text-gray-900">Module Under Optimization</h2>
        <p className="text-gray-500 max-w-sm">The "{tool.name}" module is currently being optimized.</p>
        <button onClick={onBack} className="mt-4 px-6 py-2 bg-indigo-600 text-white font-bold rounded-xl shadow-lg">Return to Dashboard</button>
      </div>
    );
  };

  const getAcceptAttribute = () => {
    if (tool.category === 'image' || tool.id === 'ocr-scanner') return 'image/*';
    if (tool.category === 'pdf') return '.pdf';
    return '*';
  };

  return (
    <div className="fixed inset-0 bg-white z-[60] flex flex-col overflow-hidden animate-in fade-in slide-in-from-bottom-4 duration-300" role="dialog" aria-modal="true">
      <div className="bg-white border-b border-gray-100 px-4 md:px-6 h-16 flex items-center justify-between shrink-0">
        <div className="flex items-center space-x-3 overflow-hidden">
          <button 
            onClick={onBack} 
            className="w-9 h-9 md:w-10 md:h-10 rounded-xl hover:bg-gray-100 flex items-center justify-center text-xl transition-colors shrink-0"
            aria-label="Back"
          >
            ←
          </button>
          <div className="flex items-center space-x-2 truncate">
            <span className="text-xl md:text-2xl shrink-0" aria-hidden="true">{tool.icon}</span>
            <span className="font-bold text-gray-900 truncate text-sm md:base">{tool.name}</span>
          </div>
        </div>
        {fileDataList.length > 0 && (
          <button 
            onClick={clearAll} 
            className="text-xs font-bold text-gray-400 hover:text-red-600 transition-colors px-3 py-2 border border-transparent hover:border-red-100 rounded-lg shrink-0"
          >
            {isMultiFile ? 'Reset' : 'Discard'}
          </button>
        )}
      </div>

      <div className="flex-1 overflow-y-auto bg-[#fdfdfe]">
        {fileDataList.length > 0 ? (
          renderTool()
        ) : (
          <div className="h-full flex items-center justify-center p-4 md:p-8">
            <div className="w-full max-w-2xl aspect-square md:aspect-[16/10] bg-white border-2 border-dashed border-gray-200 rounded-[2rem] flex flex-col items-center justify-center p-6 text-center group hover:border-indigo-300 hover:bg-indigo-50/20 transition-all cursor-pointer relative shadow-sm">
              <input 
                type="file" 
                multiple={isMultiFile} 
                className="absolute inset-0 opacity-0 cursor-pointer z-10" 
                onChange={onFilesSelect}
                accept={getAcceptAttribute()} 
              />
              <div className="w-16 h-16 md:w-20 md:h-20 bg-indigo-50 rounded-3xl flex items-center justify-center mb-6 group-hover:scale-110 group-hover:bg-indigo-100 transition-all duration-300">
                <span className="text-3xl" aria-hidden="true">📤</span>
              </div>
              <h3 className="text-lg md:text-xl font-bold text-gray-900 mb-2">
                Click or drop {isMultiFile ? 'files' : 'file'}
              </h3>
              <p className="text-gray-400 text-xs md:text-sm max-w-xs">
                Supports {tool.category === 'pdf' ? 'PDF documents' : 'JPG, PNG, WebP images'}.
              </p>
              <div className="mt-8 px-6 py-3 bg-indigo-600 text-white font-bold rounded-xl shadow-xl shadow-indigo-100 group-hover:translate-y-[-2px] transition-transform">
                Choose File
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default Workspace;