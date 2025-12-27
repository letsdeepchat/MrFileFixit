
import React from 'react';
import { Tool } from '../types';

interface ToolCardProps {
  tool: Tool;
  onClick: (tool: Tool) => void;
}

const ToolCard: React.FC<ToolCardProps> = ({ tool, onClick }) => {
  return (
    <div 
      className="tool-card group cursor-pointer bg-white p-5 md:p-6 rounded-[1.5rem] border border-gray-100 transition-all duration-500 hover:border-indigo-100 hover:shadow-[0_20px_40px_rgba(79,70,229,0.06)] flex flex-col h-full"
      onClick={() => onClick(tool)}
    >
      <div className={`w-12 h-12 ${tool.color} rounded-2xl flex items-center justify-center text-xl shadow-lg mb-6 group-hover:rotate-6 transition-all duration-500 ease-out shrink-0`}>
        {tool.icon}
      </div>
      <h3 className="text-lg font-bold text-gray-900 mb-2 group-hover:text-indigo-600 transition-colors">
        {tool.name}
      </h3>
      <p className="text-gray-400 text-xs leading-relaxed mb-4 flex-grow">
        {tool.description}
      </p>
      <div className="flex items-center text-[10px] font-bold text-indigo-600 opacity-60 group-hover:opacity-100 transition-all translate-x-[-4px] group-hover:translate-x-0 duration-300 uppercase tracking-widest">
        Open Tool <span className="ml-2">→</span>
      </div>
    </div>
  );
};

export default ToolCard;
