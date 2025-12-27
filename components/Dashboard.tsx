
import React, { useState } from 'react';
import { TOOLS } from '../constants';
import { Tool, ToolCategory } from '../types';
import ToolCard from './ToolCard';

interface DashboardProps {
  onSelectTool: (tool: Tool) => void;
}

const Dashboard: React.FC<DashboardProps> = ({ onSelectTool }) => {
  const [activeCategory, setActiveCategory] = useState<ToolCategory | 'all'>('all');

  const filteredTools = activeCategory === 'all' 
    ? TOOLS 
    : TOOLS.filter(t => t.category === activeCategory);

  const categories: { label: string; value: ToolCategory | 'all' }[] = [
    { label: 'All Tools', value: 'all' },
    { label: 'Images', value: 'image' },
    { label: 'PDFs', value: 'pdf' },
    { label: 'Advanced', value: 'advanced' },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 md:py-10">
      <div className="max-w-4xl mx-auto text-center mb-8 md:mb-12">
        <div className="inline-flex items-center space-x-2 px-3 py-1 bg-indigo-50 text-indigo-700 rounded-full text-[10px] font-bold uppercase tracking-widest mb-4 border border-indigo-100">
          <span className="animate-pulse">●</span>
          <span>Zero Server Uploads • Infinite Privacy</span>
        </div>
        <h1 className="text-3xl md:text-5xl lg:text-6xl font-extrabold text-gray-900 mb-4 tracking-tighter leading-[1.1]">
          The professional <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-600 to-blue-500">file workshop.</span>
        </h1>
        <p className="text-sm md:text-lg text-gray-500 leading-relaxed px-4 max-w-2xl mx-auto">
          High-performance tools for images, PDFs, and OCR. 
          Processed locally on your device for unmatched speed and security.
        </p>
      </div>

      <div className="sticky top-16 z-40 bg-gray-50/80 backdrop-blur-sm py-3 mb-8 -mx-4 px-4 overflow-x-auto no-scrollbar flex justify-center border-b border-gray-200/50 md:border-none md:bg-transparent md:static">
        <div className="flex space-x-2">
          {categories.map((cat) => (
            <button
              key={cat.value}
              onClick={() => setActiveCategory(cat.value)}
              className={`whitespace-nowrap px-5 py-2 rounded-xl text-xs font-bold transition-all duration-300 ${
                activeCategory === cat.value
                  ? 'bg-gray-900 text-white shadow-xl shadow-gray-200 scale-105'
                  : 'bg-white text-gray-500 border border-gray-100 hover:border-indigo-300 hover:text-indigo-600 shadow-sm'
              }`}
            >
              {cat.label}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 md:gap-5">
        {filteredTools.map((tool) => (
          <ToolCard 
            key={tool.id} 
            tool={tool} 
            onClick={onSelectTool} 
          />
        ))}
      </div>
      
      <div className="mt-16 md:mt-24 border-t border-gray-100 pt-12 md:pt-16">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 md:gap-12 text-center md:text-left">
          <div className="group">
            <div className="w-10 h-10 bg-blue-50 rounded-xl flex items-center justify-center text-xl mb-4 group-hover:scale-110 transition-transform shadow-sm">🖼️</div>
            <h4 className="font-bold text-gray-900 mb-2 text-base">Image Engine</h4>
            <p className="text-xs text-gray-500 leading-relaxed">
              Native resizing, lossless compression, and background removal using your device's GPU.
            </p>
          </div>
          <div className="group">
            <div className="w-10 h-10 bg-red-50 rounded-xl flex items-center justify-center text-xl mb-4 group-hover:scale-110 transition-transform shadow-sm">📄</div>
            <h4 className="font-bold text-gray-900 mb-2 text-base">PDF Suite</h4>
            <p className="text-xs text-gray-500 leading-relaxed">
              Fast merging and splitting. We handle your documents entirely in RAM, so nothing ever hits a disk.
            </p>
          </div>
          <div className="group">
            <div className="w-10 h-10 bg-emerald-50 rounded-xl flex items-center justify-center text-xl mb-4 group-hover:scale-110 transition-transform shadow-sm">🔍</div>
            <h4 className="font-bold text-gray-900 mb-2 text-base">OCR & Advanced</h4>
            <p className="text-xs text-gray-500 leading-relaxed">
              Extract text from scans using the industry-leading Tesseract engine compiled for browsers.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
