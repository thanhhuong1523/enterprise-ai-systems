import React, { useState, useRef, useEffect } from 'react';
import { getAccessToken, getApiBaseUrl } from '@/api/client';
import { MessageSquare, X, Send, Bot, FileText, Sparkles, Loader2, CheckCircle2, AlertCircle, ChevronDown, ChevronRight } from 'lucide-react';

interface ReasoningStep {
  step: number;
  thought: string;
}

interface ActionStep {
  step: number;
  tool?: string;
  label: string;
  status: 'CALLING_TOOL' | 'SUCCESS' | 'ERROR';
}

interface Message {
  sender: 'user' | 'assistant';
  text: string;
  chunks?: Array<any>;
  steps?: ActionStep[];
  reasoningSteps?: ReasoningStep[];
  isThinking?: boolean;
  statusMessage?: string;
}

const ReasoningPanel: React.FC<{ steps?: ReasoningStep[] }> = ({ steps }) => {
  const [expanded, setExpanded] = useState(false);

  if (!steps || steps.length === 0) return null;

  return (
    <div className="mb-2.5 rounded-xl border border-indigo-200/60 dark:border-indigo-800/40 bg-indigo-50/50 dark:bg-indigo-950/20 overflow-hidden text-xs">
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className="w-full px-2.5 py-1.5 flex items-center justify-between text-indigo-700 dark:text-indigo-300 font-medium hover:bg-indigo-100/50 dark:hover:bg-indigo-900/30 transition-colors text-left cursor-pointer border-0 bg-transparent"
      >
        <span className="flex items-center gap-1.5">
          <span>🧠</span>
          <span>Quá trình suy luận ({steps.length} bước)</span>
        </span>
        {expanded ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
      </button>
      {expanded && (
        <div className="px-2.5 pb-2 pt-1 space-y-1.5 border-t border-indigo-200/40 dark:border-indigo-800/30 text-[11px] text-slate-600 dark:text-zinc-400">
          {steps.map((s, idx) => (
            <div key={idx} className="leading-relaxed bg-white/70 dark:bg-zinc-900/60 p-2 rounded-lg border border-indigo-100 dark:border-indigo-900/40">
              <div className="font-semibold text-indigo-600 dark:text-indigo-400 mb-0.5">Bước {s.step}:</div>
              <div className="italic whitespace-pre-wrap">{s.thought}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export const ChatWidget: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<Message[]>([
    { 
      sender: 'assistant', 
      text: 'Xin chào! Tôi là Trợ lý EAP AI. Tôi có thể hỗ trợ bạn tìm kiếm tài liệu, phòng ban, hoặc quản lý hệ thống. Bạn cần hỗ trợ gì hôm nay?' 
    }
  ]);
  const [loading, setLoading] = useState(false);
  const chatEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isOpen, loading]);

  const handleSend = async () => {
    if (!input.trim() || loading) return;
    const userMessage = input.trim();
    setInput('');

    // Append user message and placeholder assistant message
    setMessages(prev => [
      ...prev,
      { sender: 'user', text: userMessage },
      { 
        sender: 'assistant', 
        text: '', 
        isThinking: true, 
        statusMessage: 'Đang tiếp nhận yêu cầu...',
        steps: [] 
      }
    ]);
    setLoading(true);

    try {
      const token = getAccessToken();
      const baseUrl = getApiBaseUrl();
      const url = `${baseUrl}/api/v1/ai/assistant/chat/stream`;

      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {})
        },
        credentials: 'include',
        body: JSON.stringify({ message: userMessage })
      });

      if (!response.ok) {
        if (response.status === 401) {
          throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
        } else if (response.status === 403) {
          throw new Error('Bạn không có quyền thực hiện yêu cầu này.');
        } else {
          throw new Error('Không thể kết nối tới Trợ lý AI. Vui lòng thử lại sau.');
        }
      }

      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error('Không thể thiết lập luồng dữ liệu thời gian thực.');
      }

      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const blocks = buffer.split('\n\n');
        buffer = blocks.pop() || '';

        for (const block of blocks) {
          if (!block.trim()) continue;

          let eventName = 'message';
          let dataStr = '';

          for (const rawLine of block.split('\n')) {
            const line = rawLine.trim();
            if (line.startsWith('event:')) {
              eventName = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              dataStr = line.substring(5).trim();
            }
          }

          if (!dataStr) continue;

          try {
            const eventData = JSON.parse(dataStr);

            setMessages(prev => {
              const updated = [...prev];
              const lastMsg = { ...updated[updated.length - 1] };
              if (lastMsg.sender !== 'assistant') return prev;

              if (eventName === 'thinking') {
                lastMsg.statusMessage = eventData.message || 'Đang phân tích yêu cầu...';
                lastMsg.isThinking = true;
              } else if (eventName === 'reasoning') {
                const currentReasoning = [...(lastMsg.reasoningSteps || [])];
                const thoughtText = eventData.thought || eventData.text || '';
                if (thoughtText) {
                  currentReasoning.push({
                    step: eventData.step ?? (currentReasoning.length + 1),
                    thought: thoughtText
                  });
                  lastMsg.reasoningSteps = currentReasoning;
                }
                lastMsg.statusMessage = '🧠 Đang suy luận bước ' + (eventData.step ?? currentReasoning.length) + '...';
                lastMsg.isThinking = true;
              } else if (eventName === 'action_start') {
                lastMsg.statusMessage = eventData.label || 'Đang thực hiện công cụ...';
                const currentSteps = [...(lastMsg.steps || [])];
                currentSteps.push({
                  step: eventData.step ?? currentSteps.length + 1,
                  tool: eventData.tool,
                  label: eventData.label,
                  status: 'CALLING_TOOL'
                });
                lastMsg.steps = currentSteps;
              } else if (eventName === 'action_end') {
                const currentSteps = [...(lastMsg.steps || [])];
                const stepIdx = currentSteps.findLastIndex?.(s => s.tool === eventData.tool) ?? 
                                currentSteps.findIndex(s => s.tool === eventData.tool);
                if (stepIdx !== -1) {
                  currentSteps[stepIdx] = {
                    ...currentSteps[stepIdx],
                    label: eventData.label || currentSteps[stepIdx].label,
                    status: eventData.status === 'SUCCESS' ? 'SUCCESS' : 'ERROR'
                  };
                } else {
                  currentSteps.push({
                    step: eventData.step ?? currentSteps.length + 1,
                    tool: eventData.tool,
                    label: eventData.label,
                    status: 'SUCCESS'
                  });
                }
                lastMsg.steps = currentSteps;
                lastMsg.statusMessage = eventData.label;
              } else if (eventName === 'content') {
                lastMsg.text = eventData.text || '';
                if (eventData.chunks && Array.isArray(eventData.chunks)) {
                  lastMsg.chunks = eventData.chunks;
                }
                lastMsg.isThinking = false;
                lastMsg.statusMessage = undefined;
              } else if (eventName === 'error') {
                lastMsg.text = eventData.message ? `⚠️ ${eventData.message}` : '⚠️ Có lỗi xảy ra trong quá trình xử lý.';
                lastMsg.isThinking = false;
                lastMsg.statusMessage = undefined;
              } else if (eventName === 'done') {
                lastMsg.isThinking = false;
                lastMsg.statusMessage = undefined;
              }

              updated[updated.length - 1] = lastMsg;
              return updated;
            });
          } catch (err) {
            console.error('Lỗi khi đọc sự kiện SSE:', err, dataStr);
          }
        }
      }
    } catch (error: any) {
      setMessages(prev => {
        const updated = [...prev];
        const lastMsg = { ...updated[updated.length - 1] };
        if (lastMsg && lastMsg.sender === 'assistant') {
          lastMsg.text = error?.message || 'Hệ thống đang gặp sự cố kết nối, xin vui lòng thử lại sau.';
          lastMsg.isThinking = false;
          lastMsg.statusMessage = undefined;
          updated[updated.length - 1] = lastMsg;
          return updated;
        }
        return [...prev, {
          sender: 'assistant',
          text: error?.message || 'Hệ thống đang gặp sự cố kết nối, xin vui lòng thử lại sau.'
        }];
      });
    } finally {
      setLoading(false);
      setMessages(prev => {
        const updated = [...prev];
        const lastMsg = { ...updated[updated.length - 1] };
        if (lastMsg && lastMsg.sender === 'assistant') {
          lastMsg.isThinking = false;
          lastMsg.statusMessage = undefined;
          // Nếu không có cả text lẫn steps mà luồng đã kết thúc
          if (!lastMsg.text && (!lastMsg.steps || lastMsg.steps.length === 0)) {
            lastMsg.text = 'Đã hoàn tất xử lý yêu cầu.';
          }
          updated[updated.length - 1] = lastMsg;
        }
        return updated;
      });
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
        <div className="absolute bottom-18 right-0 w-[420px] h-[560px] bg-card text-foreground rounded-2xl shadow-2xl border border-border/80 flex flex-col overflow-hidden animate-chat-pop">
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
                  Tự chủ xâu chuỗi tác vụ
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
                <div className={`max-w-[85%] p-3.5 rounded-2xl shadow-sm ${
                  msg.sender === 'user' 
                    ? 'bg-primary text-primary-foreground rounded-tr-none' 
                    : 'bg-card text-foreground border border-border/80 rounded-tl-none'
                }`}>
                  {/* Reasoning Process (Collapsible Panel) */}
                  <ReasoningPanel steps={msg.reasoningSteps} />

                  {/* Action Steps Executed (Action Stepper) */}
                  {msg.steps && msg.steps.length > 0 && (
                    <div className="mb-2.5 space-y-1.5 pb-2 border-b border-border/60">
                      {msg.steps.map((step, sIdx) => (
                        <div key={sIdx} className="flex items-center gap-2 text-xs font-medium text-muted-foreground bg-secondary/40 dark:bg-secondary/20 px-2.5 py-1.5 rounded-lg border border-border/40">
                          {step.status === 'CALLING_TOOL' ? (
                            <Loader2 className="w-3.5 h-3.5 text-indigo-500 animate-spin shrink-0" />
                          ) : step.status === 'SUCCESS' ? (
                            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
                          ) : (
                            <AlertCircle className="w-3.5 h-3.5 text-rose-500 shrink-0" />
                          )}
                          <span className="truncate">{step.label}</span>
                        </div>
                      ))}
                    </div>
                  )}

                  {/* Message Content */}
                  {msg.text && (
                    <p className="text-sm whitespace-pre-line leading-relaxed">{msg.text}</p>
                  )}

                  {/* Thinking Status Indicator */}
                  {msg.isThinking && msg.statusMessage && (
                    <div className="flex items-center gap-2 text-xs text-indigo-600 dark:text-indigo-400 font-medium py-1">
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      <span>{msg.statusMessage}</span>
                    </div>
                  )}
                  
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
                            {chunk.score != null && (
                              <div className="mt-1 text-right">
                                <span className="inline-flex items-center px-1.5 py-0.5 rounded-full bg-primary/10 text-primary text-[9px] font-semibold">
                                  Độ khớp: {Math.max(0, Math.min(100, Math.round(chunk.score * 100)))}%
                                </span>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            ))}

            <div ref={chatEndRef} />
          </div>

          {/* Input Area */}
          <form onSubmit={handleSubmit} className="p-3 bg-card border-t border-border flex items-center gap-2">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Nhập yêu cầu hoặc câu hỏi cho Trợ lý..."
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
