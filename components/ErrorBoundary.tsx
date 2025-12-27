import React, { ErrorInfo, ReactNode } from 'react';

interface Props {
  // Fix: Making children optional resolves errors where JSX usage doesn't explicitly match the props object
  children?: ReactNode;
}

interface State {
  hasError: boolean;
  error?: Error;
}

// Fix: Explicitly declaring 'state' and 'props' properties ensures that 
// inherited members are correctly resolved and typed by the compiler in this environment.
class ErrorBoundary extends React.Component<Props, State> {
  public state: State;

  // Fix: Explicitly defining the constructor and calling super(props) ensures 
  // that 'this.props' is correctly initialized, typed, and accessible on the class instance.
  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false
    };
  }

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Uncaught error:', error, errorInfo);
  }

  public render(): ReactNode {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center p-8 text-center">
          <div className="w-20 h-20 bg-red-100 rounded-3xl flex items-center justify-center text-3xl mb-6">⚠️</div>
          <h2 className="text-2xl font-bold text-gray-900 mb-2">Something went wrong</h2>
          <p className="text-gray-500 max-w-md mb-8">
            We encountered an unexpected error while processing your request. Your files are safe and were not uploaded.
          </p>
          <button 
            onClick={() => window.location.reload()}
            className="px-8 py-4 bg-indigo-600 text-white font-bold rounded-2xl shadow-xl hover:bg-indigo-700 transition-all"
          >
            Refresh Application
          </button>
          {this.state.error && (
            <pre className="mt-8 p-4 bg-gray-100 rounded-xl text-[10px] text-gray-400 font-mono text-left max-w-xl overflow-auto">
              {this.state.error.toString()}
            </pre>
          )}
        </div>
      );
    }

    // Fixed: properties passed to the component are correctly inherited and accessible
    return this.props.children;
  }
}

export default ErrorBoundary;
