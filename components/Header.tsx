import React from 'react';

interface HeaderProps {
  onGoHome: () => void;
}

const Header: React.FC<HeaderProps> = ({ onGoHome }) => {
  return (
    <header className="bg-white/80 backdrop-blur-md border-b border-gray-100 sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          <div 
            className="flex items-center cursor-pointer group"
            onClick={onGoHome}
          >
            <div className="w-9 h-9 md:w-10 md:h-10 bg-indigo-600 rounded-xl flex items-center justify-center text-white font-bold text-lg md:text-xl mr-3 group-hover:rotate-6 transition-all duration-300 shadow-lg shadow-indigo-100">
              📄
            </div>
            <span className="text-lg md:text-xl font-bold text-gray-900 tracking-tight">
              MrFile<span className="text-indigo-600">Fixit</span>
            </span>
          </div>
          
          <div className="hidden md:flex items-center space-x-4 lg:space-x-6">
            <div className="flex items-center space-x-2 px-3 py-1.5 bg-blue-50 rounded-full border border-blue-100 shadow-sm">
              <span className="text-[10px] font-bold text-blue-600 uppercase tracking-tighter">🖼️ Image Tools</span>
            </div>
            <div className="flex items-center space-x-2 px-3 py-1.5 bg-red-50 rounded-full border border-red-100 shadow-sm">
              <span className="text-[10px] font-bold text-red-600 uppercase tracking-tighter">📄 PDF Suite</span>
            </div>
            <div className="flex items-center space-x-2 px-3 py-1.5 bg-emerald-50 rounded-full border border-emerald-100 shadow-sm">
              <span className="text-[10px] font-bold text-emerald-600 uppercase tracking-tighter">🔍 OCR Scan</span>
            </div>
          </div>

          <div className="flex items-center">
            {/* Right side spacer for layout balance */}
            <div className="w-10 h-10 lg:hidden"></div>
            <div className="hidden lg:block text-[10px] font-bold text-gray-400 uppercase tracking-widest">
              Professional Grade • Local Engine
            </div>
          </div>
        </div>
      </div>
    </header>
  );
};

export default Header;