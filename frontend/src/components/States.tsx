import { Inbox, LoaderCircle } from 'lucide-react'

export function Loading() {
  return <div className="state"><LoaderCircle className="spin" size={22} /><span>加载中</span></div>
}

export function Empty({ text = '暂无数据' }: { text?: string }) {
  return <div className="state state--empty"><Inbox size={22} /><span>{text}</span></div>
}

export function ErrorNotice({ message }: { message: string }) {
  return <div className="notice notice--error" role="alert">{message}</div>
}

export function SuccessNotice({ message }: { message: string }) {
  return <div className="notice notice--success" role="status">{message}</div>
}
