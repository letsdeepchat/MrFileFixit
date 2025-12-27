import React, { useState } from 'react';
import Header from './components/Header';
import Dashboard from './components/Dashboard';
import Workspace from './components/Workspace';
import ErrorBoundary from './components/ErrorBoundary';
import { Tool } from './types';

const App: React.FC = () => {
  const [selectedTool, setSelectedTool] = useState<Tool | null>(null);

  const handleSelectTool = (tool: Tool) => {
    setSelectedTool(tool);
  };

  const handleGoHome = () => {
    setSelectedTool(null);
  };

  return (
    <ErrorBoundary>
      <div className="min-h-screen flex flex-col selection:bg-indigo-100 selection:text-indigo-900">
        <Header onGoHome={handleGoHome} />
        
        <main className="flex-grow">
          <Dashboard onSelectTool={handleSelectTool} />
        </main>

        {selectedTool && (
          <Workspace 
            tool={selectedTool} 
            onBack={handleGoHome} 
          />
        )}

        <footer className="bg-white border-t border-gray-100 py-12 md:py-16 px-4 sm:px-6 lg:px-8 mt-auto">
          <div className="max-w-7xl mx-auto">
            <div className="flex flex-col md:flex-row justify-between items-center space-y-8 md:space-y-0">
              <div className="flex flex-col items-center md:items-start space-y-4">
                <div className="flex items-center">
                  <div className="w-8 h-8 bg-gray-900 rounded-lg flex items-center justify-center text-white font-bold text-sm mr-3 shadow-lg">
                    📄
                  </div>
                  <span className="text-lg font-bold text-gray-900 tracking-tight">
                    MrFileFixit
                  </span>
                </div>
                <p className="text-gray-400 text-xs md:text-sm text-center md:text-left max-w-xs leading-relaxed font-medium">
                  The Swiss Army Knife of file manipulation. Built for speed, designed for privacy.
                </p>
              </div>
              
              <div className="flex flex-col items-center md:items-end space-y-6">
                <div className="flex space-x-8 text-xs font-bold text-gray-400 uppercase tracking-widest">
                  <a href="https://github.com/letsdeepchat" target="_blank" rel="noopener noreferrer" className="hover:text-indigo-600 transition-colors">Github</a>
                  <a href="https://x.com/letsdeepchat" target="_blank" rel="noopener noreferrer" className="hover:text-indigo-600 transition-colors">X / Twitter</a>
                  <a href="https://linkedin.com/in/letsdeepchat" target="_blank" rel="noopener noreferrer" className="hover:text-indigo-600 transition-colors">LinkedIn</a>
                  <a href="https://instagram.com/letsdeepchat" target="_blank" rel="noopener noreferrer" className="hover:text-indigo-600 transition-colors">Instagram</a>
                </div>
                <div className="text-gray-300 text-[10px] md:text-xs font-bold uppercase tracking-widest bg-gray-50 px-3 py-1 rounded-full border border-gray-100">
                  &copy; {new Date().getFullYear()} MrFileFixit • Powered by @letsdeepchat
                </div>
              </div>
            </div>
          </div>
        </footer>
      </div>
    </ErrorBoundary>
  );
};

export default App;