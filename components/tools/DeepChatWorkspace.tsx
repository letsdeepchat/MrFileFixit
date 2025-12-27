
import React, { useState, useRef, useEffect } from 'react';
import { Tool, FileData } from '../../types';
import { chatWithFile } from '../../services/geminiService';

interface Message {
  role: 'user' | 'assistant';
  content: string;
}

interface DeepChatWorkspaceProps {
  tool: Tool;
  fileData: FileData;
  onClose: () => void;
}

const DeepChatWorkspace: React.FC<DeepChatWorkspaceProps> = ({ tool, fileData, onClose }) => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, loading]);

  // Fix: Helper to convert File to base64 string for API consumption.
  const getFileBase64 = (file: File): Promise<string> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = error => reject(error);
    });
  };

  const handleSend = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!input.trim() || loading) return;

    const userMessage = input.trim();
    setInput('');
    setMessages(prev => [...prev, { role: 'user', content: userMessage }]);
    setLoading(true);

    try {
      // Fix: Prepare file as base64 only on the first turn of the conversation.
      let filePayload = undefined;
      const isFirst = messages.length === 0;
      if (isFirst) {
        try {
          const base64 = await getFileBase64(fileData.file);
          filePayload = { data: base64, mimeType: fileData.file.type };
        } catch (err) {
          console.error("Error processing file for AI:", err);
        }
      }

      const responseText = await chatWithFile(
        userMessage, 
        filePayload,
        messages
      );
      
      setMessages(prev => [...prev, { role: 'assistant', content: responseText || "I'm sorry, I couldn't process that." }]);
    } catch (err) {
      setMessages(prev => [...prev, { role: 'assistant', content: "Error connecting to AI engine. Please check your connection." }]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col lg:flex-row h-full overflow-hidden bg-white">
      {/* File Preview Sidebar */}
      <div className="w-full lg:w-80 bg-gray-50/50 border-r border-gray-100 flex flex-col p-6 overflow-hidden shrink-0">
        <h3 className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-4">Analyzing File</h3>
        <div className="bg-white p-2 rounded-2xl shadow-sm border border-gray-100 mb-6">
          {fileData.file.type.startsWith('image/') ? (
            <img src={fileData.previewUrl} className="w-full h-auto rounded-xl object-contain max-h-48" alt="Preview" />
          ) : (
            <div className="aspect-square bg-indigo-50 rounded-xl flex items-center justify-center text-3xl">📄</div>
          )}
          <div className="mt-2 px-2 py-1">
            <p className="text-xs font-bold text-gray-900 truncate">{fileData.file.name}</p>
            <p className="text-[10px] text-gray-400 uppercase mt-0.5">{(fileData.file.size / 1024).toFixed(1)} KB</p>
          </div>
        </div>
        <div className="space-y-4">
          <p className="text-[11px] text-gray-500 leading-relaxed font-medium italic">
I'm currently analyzing this file using local AI. You can ask for summaries, data extraction, or general insights.
          </p>
          <div className="flex items-center p-3 bg-indigo-50 rounded-xl text-indigo-600 text-[10px] font-bold uppercase tracking-tight">
            <span className="mr-2">✨</span> Powered by Local AI Engine
          </div>
        </div>
      </div>

      {/* Chat Area */}
      <div className="flex-1 flex flex-col min-w-0">
        <div 
          ref={scrollRef}
          className="flex-1 overflow-y-auto p-6 md:p-10 space-y-6"
        >
          {messages.length === 0 && (
            <div className="h-full flex flex-col items-center justify-center text-center opacity-40">
              <div className="text-5xl mb-6">🤖</div>
              <h4 className="text-lg font-bold text-gray-900 mb-2">Deep Chat is ready</h4>
              <p className="text-sm text-gray-500 max-w-xs">Ask me anything about your document or image.</p>
            </div>
          )}
          
          {messages.map((msg, i) => (
            <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[85%] rounded-[1.5rem] px-5 py-3 text-sm leading-relaxed ${
                msg.role === 'user' 
                  ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-100' 
                  : 'bg-gray-100 text-gray-800 border border-gray-200'
              }`}>
                {msg.content}
              </div>
            </div>
          ))}

          {loading && (
            <div className="flex justify-start">
              <div className="bg-gray-100 border border-gray-200 rounded-[1.5rem] px-5 py-3 flex items-center space-x-2">
                <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce"></div>
                <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce delay-75"></div>
                <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce delay-150"></div>
              </div>
            </div>
          )}
        </div>

        <div className="p-6 md:p-8 bg-white border-t border-gray-100">
          <form onSubmit={handleSend} className="relative max-w-4xl mx-auto">
            <input 
              type="text" 
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Type your question about the file..."
              className="w-full pl-6 pr-24 py-5 bg-gray-50 border border-gray-200 rounded-[2rem] text-sm focus:bg-white focus:outline-none focus:ring-4 focus:ring-indigo-500/10 focus:border-indigo-500 transition-all placeholder:text-gray-400"
            />
            <button 
              type="submit"
              disabled={loading || !input.trim()}
              className="absolute right-3 top-3 bottom-3 px-6 bg-indigo-600 text-white font-bold rounded-[1.5rem] hover:bg-indigo-700 disabled:opacity-30 transition-all text-xs"
            >
              Send
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default DeepChatWorkspace;
