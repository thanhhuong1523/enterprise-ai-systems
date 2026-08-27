import React, { useState, useRef, useEffect } from 'react';
import { apiClient } from '@/api/client';
import { MessageSquare, X, Send, Bot, FileText, Sparkles } from 'lucide-react';

interface Message {
  sender: 'user' | 'assistant';
  text: string;
  chunks?: Array<{ content: string; score: number }>;
}

export const ChatWidget: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<Message[]>([
    { 
      sender: 'assistant', 
      text: 'Xin chào! Tôi là Trợ lý EAP AI. Tôi có thể hỗ trợ bạn tìm kiếm tài liệu, phòng ban, hoặc thông tin nhân sự trên hệ thống. Bạn cần tìm thông tin gì hôm nay?' 
    }
  ]);
  const [loading, setLoading] = useState(false);
  const chatEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isOpen]);

  const handleSend = async () => {
    if (!input.trim() || loading) return;
    const userMessage = input.trim();
    setInput('');
    setMessages(prev => [...prev, { sender: 'user', text: userMessage }]);
    setLoading(true);

    try {
      const response = await apiClient.post('/api/v1/search', { message: userMessage });
      const apiResponse = response.data;
      
      if (apiResponse && apiResponse.success && apiResponse.data) {
        const chatData = apiResponse.data;
        setMessages(prev => [...prev, {
          sender: 'assistant',
          text: chatData.response === 'không có' 
            ? 'Tôi chưa tìm thấy tài liệu nào phù hợp với yêu cầu của bạn.' 
            : chatData.response,
          chunks: chatData.chunks || []
        }]);
      } else {
        setMessages(prev => [...prev, {
          sender: 'assistant',
          text: 'Rất tiếc, tôi chưa tìm thấy tài liệu phù hợp lúc này.'
        }]);
      }
    } catch {
      setMessages(prev => [...prev, {
        sender: 'assistant',
        text: 'Hệ thống đang gặp sự cố kết nối, xin vui lòng thử lại sau.'
      }]);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    handleSend();
  };

  return (
    <div className="fixed bottom-6 right-6 z-50 font-sans">
      <style>{`
        @keyframes chat-pop {
          0% { transform: scale(0.9) translateY(15px); opacity: 0; }
          100% { transform: scale(1) translateY(0); opacity: 1; }
        }
        .animate-chat-pop {
          animation: chat-pop 0.28s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
        }
        @keyframes pulse-dot {
          0%, 100% { opacity: 1; transform: scale(1); }
          50% { opacity: 0.4; transform: scale(0.9); }
        }
        .animate-pulse-dot {
          animation: pulse-dot 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
        }
      `}</style>

      {/* 1. Sleek Floating Trigger Icon */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="w-14 h-14 rounded-full bg-gradient-to-tr from-primary to-indigo-600 hover:from-primary/90 hover:to-indigo-700 text-white shadow-lg hover:shadow-xl shadow-indigo-500/20 flex items-center justify-center transition-all transform hover:scale-105 active:scale-95 cursor-pointer relative group border border-white/10"
        title="Trò chuyện hỗ trợ"
      >
        <div className="flex items-center justify-center">
          {isOpen ? (
            <X className="w-6 h-6 transition-transform duration-200" />
          ) : (
            <MessageSquare className="w-6 h-6 transition-transform duration-200" />
          )}
        </div>
      </button>

      {/* 2. Professional Styled Chat Window */}
      {isOpen && (
        <div className="absolute bottom-18 right-0 w-96 h-[530px] bg-card text-foreground rounded-2xl shadow-2xl border border-border/80 flex flex-col overflow-hidden animate-chat-pop">
          {/* Header */}
          <div className="bg-slate-900 dark:bg-zinc-950 text-white p-4 flex items-center justify-between relative select-none border-b border-white/5">
            <div className="flex items-center space-x-3">
              {/* Bot Avatar */}
              <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center shadow-md">
                <Bot className="w-5 h-5 text-white" />
              </div>
              <div>
                <h3 className="font-semibold text-sm leading-none">Trợ lý EAP AI</h3>
                <span className="flex items-center text-[10px] text-emerald-400 font-medium mt-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 mr-1 animate-pulse-dot"></span>
                  Đang trực tuyến
                </span>
              </div>
            </div>
            <button 
              onClick={() => setIsOpen(false)} 
              className="text-slate-400 hover:text-white transition-colors cursor-pointer bg-transparent border-0 outline-none p-1 flex items-center justify-center"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Messages Area */}
          <div className="flex-1 p-4 overflow-y-auto bg-slate-50/50 dark:bg-zinc-900/40 space-y-4">
            {messages.map((msg, index) => (
              <div key={index} className={`flex ${msg.sender === 'user' ? 'justify-end' : 'justify-start items-start gap-2.5'}`}>
                {msg.sender === 'assistant' && (
                  <div className="w-7 h-7 rounded-lg bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center text-white flex-shrink-0 shadow-sm mt-0.5">
                    <Sparkles className="w-4 h-4" />
                  </div>
                )}
                <div className={`max-w-[80%] p-3 rounded-2xl shadow-sm ${
                  msg.sender === 'user' 
                    ? 'bg-primary text-primary-foreground rounded-tr-none' 
                    : 'bg-card text-foreground border border-border/80 rounded-tl-none'
                }`}>
                  <p className="text-sm whitespace-pre-line leading-relaxed">{msg.text}</p>
                  
                  {/* Documents Found (Chunks) */}
                  {msg.chunks && msg.chunks.length > 0 && (
                    <div className="mt-3 pt-3 border-t border-border/70 space-y-2">
                      <span className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1">
                        <FileText className="w-3.5 h-3.5 text-primary" /> Tài liệu đối chiếu:
                      </span>
                      {msg.chunks.map((chunk: any, cIdx: number) => {
                        const rawHeadings = chunk.metadata?.citation_headings || chunk.metadata?.citationHeadings;
                        let headingsText = '';
                        if (Array.isArray(rawHeadings) && rawHeadings.length > 0) {
                          headingsText = rawHeadings.join(' > ');
                        } else if (typeof rawHeadings === 'string' && rawHeadings.trim()) {
                          headingsText = rawHeadings.trim();
                        }

                        const pageNum = chunk.pageNumber ?? chunk.metadata?.page_number ?? chunk.metadata?.page ?? chunk.metadata?.page_no;

                        return (
                          <div key={cIdx} className="bg-secondary/30 dark:bg-secondary/10 p-2.5 rounded-xl border border-border/50 text-xs text-foreground/90 shadow-sm hover:border-primary/30 dark:hover:border-primary/50 transition-colors relative overflow-hidden space-y-1.5">
                            <div className="absolute top-0 left-0 w-1 h-full bg-primary/40"></div>
                            
                            {/* Document Title & Page Number */}
                            <div className="flex items-center justify-between gap-2 text-[10px] text-muted-foreground font-medium pl-1 border-b border-border/40 pb-1">
                              <span className="truncate font-semibold text-primary/90 flex items-center gap-1" title={chunk.citation || chunk.documentTitle}>
                                📜 {chunk.documentTitle || chunk.businessCode || `Tài liệu ${cIdx + 1}`}
                              </span>
                              {pageNum != null && pageNum > 0 && (
                                <span className="px-1.5 py-0.5 rounded bg-primary/10 text-primary font-semibold shrink-0">
                                  Trang {pageNum}
                                </span>
                              )}
                            </div>

                            {/* Citation Headings (Trích dẫn Đề mục) */}
                            {headingsText && (
                              <div className="pl-1 text-[10.5px] font-semibold text-indigo-600 dark:text-indigo-400 flex items-start gap-1 leading-snug">
                                <span className="shrink-0">📌 Đề mục:</span>
                                <span className="italic">{headingsText}</span>
                              </div>
                            )}

                            {/* Content */}
                            <p className="font-medium italic leading-relaxed pl-1 text-foreground/90">"{chunk.content}"</p>
                            
                            {/* Similarity Score */}
                            <div className="mt-1 text-right">
                              <span className="inline-flex items-center px-1.5 py-0.5 rounded-full bg-primary/10 text-primary text-[9px] font-semibold">
                                Độ khớp: {Math.max(0, Math.min(100, Math.round(chunk.score * 100)))}%
                              </span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            ))}
            {loading && (
              <div className="flex justify-start items-start gap-2.5">
                <div className="w-7 h-7 rounded-lg bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center text-white flex-shrink-0 shadow-sm mt-0.5 animate-pulse">
                  <Sparkles className="w-4 h-4" />
                </div>
                <div className="bg-card text-muted-foreground p-3.5 rounded-2xl shadow-sm border border-border/80 rounded-tl-none flex items-center space-x-1.5">
                  <div className="w-1.5 h-1.5 bg-primary/80 rounded-full animate-bounce" style={{ animationDelay: '0ms' }}></div>
                  <div className="w-1.5 h-1.5 bg-primary/80 rounded-full animate-bounce" style={{ animationDelay: '150ms' }}></div>
                  <div className="w-1.5 h-1.5 bg-primary/80 rounded-full animate-bounce" style={{ animationDelay: '300ms' }}></div>
                </div>
              </div>
            )}
            <div ref={chatEndRef} />
          </div>

          {/* Input Area */}
          <form onSubmit={handleSubmit} className="p-3 bg-card border-t border-border flex items-center gap-2">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Nhập câu hỏi hoặc tài liệu cần tìm..."
              className="flex-1 px-3.5 py-2 border border-border focus:ring-1 focus:ring-primary focus:border-primary outline-none rounded-xl text-sm transition-all text-foreground bg-secondary/30 dark:bg-secondary/10 placeholder:text-muted-foreground/60"
            />
            <button
              type="submit"
              disabled={loading}
              className="p-2 bg-primary hover:bg-primary/90 text-primary-foreground rounded-xl transition-all shadow-md active:scale-95 cursor-pointer disabled:opacity-50 border-0 flex items-center justify-center"
            >
              <Send className="w-4 h-4" />
            </button>
          </form>
        </div>
      )}
    </div>
  );
};
