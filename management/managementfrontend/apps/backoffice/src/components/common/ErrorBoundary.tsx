import React, { ReactNode, ErrorInfo } from "react";
import { Button, Empty, Space } from "antd";
import { ReloadOutlined, HomeOutlined } from "@ant-design/icons";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    };
  }

  static getDerivedStateFromError(error: Error): State {
    return {
      hasError: true,
      error,
      errorInfo: null,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // Log error details in development
    if (import.meta.env.DEV) {
      console.error("Error caught by boundary:", error, errorInfo);
    }

    this.setState({
      errorInfo,
    });
  }

  handleReset = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    });
  };

  handleReload = () => {
    window.location.href = "/";
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex flex-col items-center justify-center min-h-screen bg-gradient-to-br from-red-50 to-red-100 px-4">
          <div className="max-w-md w-full text-center space-y-6">
            {/* Error Icon */}
            <div className="text-6xl">⚠️</div>

            {/* Error Message */}
            <Empty
              description={
                <div className="space-y-2">
                  <h1 className="text-2xl font-bold text-slate-900">
                    Algo correu mal
                  </h1>
                  <p className="text-slate-600">
                    Desculpe, ocorreu um erro inesperado. Tente atualizar a
                    página ou volte mais tarde.
                  </p>
                </div>
              }
            />

            {/* Error Details (Dev Only) */}
            {import.meta.env.DEV && this.state.error && (
              <details className="text-left bg-white rounded-lg p-4 border border-red-200 max-h-48 overflow-auto">
                <summary className="cursor-pointer font-mono text-sm text-red-600 font-bold">
                  Detalhes do erro (Desenvolvimento)
                </summary>
                <pre className="text-xs mt-2 text-slate-700 whitespace-pre-wrap break-words">
                  {this.state.error.toString()}
                  {this.state.errorInfo?.componentStack}
                </pre>
              </details>
            )}

            {/* Action Buttons */}
            <Space size="middle" className="w-full flex justify-center">
              <Button
                type="primary"
                icon={<ReloadOutlined />}
                onClick={this.handleReset}
              >
                Tentar novamente
              </Button>
              <Button
                icon={<HomeOutlined />}
                onClick={this.handleReload}
              >
                Ir para Home
              </Button>
            </Space>

            {/* Help Text */}
            <p className="text-xs text-slate-500">
              Se o problema persistir, contacte o suporte técnico.
            </p>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
