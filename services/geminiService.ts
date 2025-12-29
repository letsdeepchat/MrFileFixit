
import nlp from 'compromise';
import natural from 'natural';
import Sentiment from 'sentiment';
import { extract } from 'keywords-extractor';
import { config } from '../config';

// Initialize sentiment analyzer
const sentiment = new Sentiment();

// Initialize natural language processor
const TfIdf = natural.TfIdf;
const tfidf = new TfIdf();

// Initialize keyword extractor configuration
const extractionConfig = {
  language: "english",
  remove_digits: true,
  return_changed_case: true,
  remove_duplicates: true
};

// Local file analysis using NLP
export async function chatWithFile(
  message: string, 
  fileData?: { data: string, mimeType: string }, 
  history: any[] = []
): Promise<string> {
  try {
    // If no file data, just process the conversation
    if (!fileData) {
      return processConversation(message, history);
    }

    // Extract text content from different file types
    const fileText = await extractTextFromFile(fileData);
    
    if (!fileText) {
      return "I'm sorry, I couldn't extract readable text from this file. I can work with text documents, PDFs with text content, and other readable file formats.";
    }

    // Build conversation context
    const conversationContext = buildConversationContext(history);
    
    // Analyze the file
    const fileAnalysis = analyzeFileContent(fileText, fileData.mimeType);
    
    // Process the user's question about the file
    const response = processFileQuery(message, fileText, fileAnalysis, conversationContext);
    
    return response;
    
  } catch (error) {
    console.error('Local AI processing error:', error);
    return "I encountered an error while processing your request. Let me try a different approach.";
  }
}

// Extract text content from file data
async function extractTextFromFile(fileData: { data: string, mimeType: string }): Promise<string | null> {
  const { data, mimeType } = fileData;
  
  try {
    // Handle different file types
    if (mimeType.startsWith('image/')) {
      // For images, we can only provide basic analysis
      return "This is an image file. I can provide basic image analysis but cannot read text content from images without OCR processing.";
    }
    
    // For text files, decode the base64
    if (mimeType.startsWith('text/') || mimeType.includes('json') || mimeType.includes('xml')) {
      const base64Data = data.includes(',') ? data.split(',')[1] : data;
      const text = Buffer.from(base64Data, 'base64').toString('utf-8');
      return text;
    }
    
    // For PDFs and other documents, we'd need more complex parsing
    // For now, return a placeholder
    if (mimeType.includes('pdf')) {
      return "PDF content analysis requires additional processing. I can see this is a PDF document.";
    }
    
    // Default: try to decode as text
    const base64Data = data.includes(',') ? data.split(',')[1] : data;
    return Buffer.from(base64Data, 'base64').toString('utf-8');
    
  } catch (error) {
    console.error('Error extracting text from file:', error);
    return null;
  }
}

// Analyze file content using NLP
function analyzeFileContent(text: string, mimeType: string) {
  const doc = nlp(text);
  const tokens = doc.terms().out('array');
  
  // Extract key information
  const analysis = {
    wordCount: tokens.length,
    sentences: doc.sentences().out('array'),
    nouns: doc.nouns().out('array'),
    verbs: doc.verbs().out('array'),
    people: doc.people().out('array'),
    places: doc.places().out('array'),
    organizations: doc.organizations().out('array'),
    topics: extractTopics(text),
    sentiment: sentiment.analyze(text),
    keywords: extractKeywords(text),
    mimeType: mimeType
  };
  
  return analysis;
}

// Extract topics from text
function extractTopics(text: string): string[] {
  try {
    const doc = nlp(text);
    const topics = doc.topics().out('array');
    return topics.slice(0, 10); // Limit to top 10 topics
  } catch {
    return [];
  }
}

// Extract keywords using multiple methods
function extractKeywords(text: string): string[] {
  try {
    // Method 1: Using compromise
    const doc = nlp(text);
    const nouns = doc.nouns().out('array');
    const adjectives = doc.adjectives().out('array');
    
    // Method 2: Using keywords-extractor library
    const extracted = extract(text, extractionConfig);
    
    // Combine and deduplicate
    const allKeywords = [...nouns, ...adjectives, ...extracted];
    const uniqueKeywords = [...new Set(allKeywords)];
    
    return uniqueKeywords.slice(0, 15); // Return top 15 keywords
  } catch {
    return [];
  }
}

// Process conversation without file
function processConversation(message: string, history: any[]): string {
  const doc = nlp(message);
  const intent = detectIntent(message);
  
  switch (intent) {
    case 'greeting':
      return "Hello! I'm your local AI assistant. I can help you analyze files, extract information, and answer questions. What would you like to do?";
      
    case 'question':
      return "I'd be happy to help answer your question. However, I work best when you provide a file to analyze. Upload a document, image, or other file and ask me specific questions about it.";
      
    case 'request':
      return "I understand you're looking for something. To provide the most helpful response, please share a file or document you'd like me to analyze.";
      
    default:
      return "I'm here to help you analyze files and documents. You can ask me to summarize content, extract key information, find specific details, or answer questions about your uploaded files.";
  }
}

// Process queries about files
function processFileQuery(
  message: string, 
  fileText: string, 
  analysis: any, 
  conversationContext: string
): string {
  const intent = detectIntent(message);
  const doc = nlp(message);
  
  // Build context-aware response
  const context = `File Analysis: ${analysis.wordCount} words, ${analysis.sentences.length} sentences. Key topics: ${analysis.topics.join(', ')}. Keywords: ${analysis.keywords.slice(0, 5).join(', ')}.`;
  
  switch (intent) {
    case 'summary':
      return generateSummary(analysis);
      
    case 'keywords':
      return `Key terms in this document: ${analysis.keywords.slice(0, 10).join(', ')}`;
      
    case 'sentiment':
      const sentimentScore = analysis.sentiment.score;
      const sentimentLabel = sentimentScore > 0 ? 'positive' : sentimentScore < 0 ? 'negative' : 'neutral';
      return `The overall sentiment of this document is ${sentimentLabel} (score: ${sentimentScore}).`;
      
    case 'questions':
      return generateQuestions(analysis);
      
    case 'facts':
      return extractFacts(fileText, message);
      
    case 'translation':
      return "I can help with basic text analysis, but I don't currently support translation. The document appears to be in English.";
      
    case 'statistics':
      return generateStatistics(analysis);
      
    default:
      // Default response based on content analysis
      return generateContextualResponse(message, analysis, fileText);
  }
}

// Generate document summary
function generateSummary(analysis: any): string {
  const { sentences, topics, wordCount } = analysis;
  
  if (wordCount < 50) {
    return `This is a short document with ${wordCount} words. It appears to discuss: ${topics.slice(0, 3).join(', ')}.`;
  }
  
  // Extract first few sentences as summary
  const summarySentences = sentences.slice(0, Math.min(3, Math.ceil(sentences.length * 0.3)));
  const summary = summarySentences.join(' ');
  
  return `This document contains ${wordCount} words and appears to focus on: ${topics.slice(0, 3).join(', ')}. Here's a summary based on the opening content:\n\n${summary}`;
}

// Generate questions about the document
function generateQuestions(analysis: any): string {
  const questions = [
    `What are the main topics discussed in this document?`,
    `What is the overall sentiment or tone of the content?`,
    `Who are the key people or entities mentioned?`,
    `What are the most important keywords?`,
    `What action items or conclusions can be drawn?`
  ];
  
  return `Here are some questions this document might help answer:\n${questions.map(q => `• ${q}`).join('\n')}`;
}

// Extract facts from text
function extractFacts(text: string, query: string): string {
  const doc = nlp(text);
  const sentences = doc.sentences().out('array');
  
  // Look for factual statements
  const facts = sentences.filter((s: string) => 
    s.toLowerCase().includes('is') || 
    s.toLowerCase().includes('are') || 
    s.toLowerCase().includes('was') ||
    s.toLowerCase().includes('were')
  );
  
  if (facts.length === 0) {
    return "I couldn't identify specific factual statements in this document.";
  }
  
  return `Here are some key facts I found:\n${facts.slice(0, 5).map((f: string) => `• ${f}`).join('\n')}`;
}

// Generate statistics
function generateStatistics(analysis: any): string {
  const { wordCount, sentences, nouns, verbs, sentiment } = analysis;
  const avgWordsPerSentence = Math.round(wordCount / sentences.length);
  
  return `Document Statistics:
• Word count: ${wordCount}
• Sentences: ${sentences.length}
• Average words per sentence: ${avgWordsPerSentence}
• Nouns identified: ${nouns.length}
• Verbs identified: ${verbs.length}
• Sentiment score: ${sentiment.score} (${sentiment.score > 0 ? 'positive' : sentiment.score < 0 ? 'negative' : 'neutral'})`;
}

// Generate contextual response
function generateContextualResponse(message: string, analysis: any, fileText: string): string {
  const keywords = analysis.keywords.slice(0, 5);
  const topics = analysis.topics.slice(0, 3);
  
  let response = `Based on my analysis of your document, `;
  
  if (keywords.length > 0) {
    response += `the key terms appear to be: ${keywords.join(', ')}. `;
  }
  
  if (topics.length > 0) {
    response += `The main topics are: ${topics.join(', ')}. `;
  }
  
  response += `\n\nRegarding your question: "${message}"\n`;
  
  // Try to find relevant content
  const doc = nlp(fileText);
  const relevantSentences = doc.sentences().out('array').filter((sentence: string) => {
    const lowerMessage = message.toLowerCase();
    return keywords.some((keyword: string) => sentence.toLowerCase().includes(keyword.toLowerCase())) ||
           topics.some((topic: string) => sentence.toLowerCase().includes(topic.toLowerCase()));
  });
  
  if (relevantSentences.length > 0) {
    response += `I found these relevant passages:\n${relevantSentences.slice(0, 3).map((s: string) => `• ${s}`).join('\n')}`;
  } else {
    response += "I can provide general analysis but couldn't find specific content directly related to your question.";
  }
  
  return response;
}

// Build conversation context
function buildConversationContext(history: any[]): string {
  if (history.length === 0) return "";
  
  const context = history.map(msg => `${msg.role}: ${msg.content}`).join('\n');
  return `Previous conversation:\n${context}`;
}

// Detect user intent
function detectIntent(message: string): string {
  const lowerMessage = message.toLowerCase();
  
  if (lowerMessage.includes('hello') || lowerMessage.includes('hi') || lowerMessage.includes('hey')) {
    return 'greeting';
  }
  
  if (lowerMessage.includes('summarize') || lowerMessage.includes('summary') || lowerMessage.includes('overview')) {
    return 'summary';
  }
  
  if (lowerMessage.includes('keyword') || lowerMessage.includes('key term') || lowerMessage.includes('important words')) {
    return 'keywords';
  }
  
  if (lowerMessage.includes('sentiment') || lowerMessage.includes('tone') || lowerMessage.includes('mood')) {
    return 'sentiment';
  }
  
  if (lowerMessage.includes('question') || lowerMessage.includes('ask') || lowerMessage.includes('what')) {
    return 'questions';
  }
  
  if (lowerMessage.includes('fact') || lowerMessage.includes('information') || lowerMessage.includes('data')) {
    return 'facts';
  }
  
  if (lowerMessage.includes('translate') || lowerMessage.includes('translation')) {
    return 'translation';
  }
  
  if (lowerMessage.includes('statistic') || lowerMessage.includes('analysis') || lowerMessage.includes('count')) {
    return 'statistics';
  }
  
  return 'general';
}
