import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { TaskLogMessage, SystemStatusMessage, StatisticsMessage, ErrorMessage } from '@/types'

export type LogMessageHandler = (message: TaskLogMessage) => void
export type SystemStatusHandler = (message: SystemStatusMessage) => void
export type StatisticsHandler = (message: StatisticsMessage) => void
export type ErrorHandler = (message: ErrorMessage) => void

class WebSocketManager {
  private client: Client | null = null
  private connected = false
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5
  private reconnectInterval = 3000
  private subscriptions: Map<string, any> = new Map()
  private messageHandlers: Set<LogMessageHandler> = new Set()
  private taskSpecificHandlers: Map<string, Set<LogMessageHandler>> = new Map()
  private systemStatusHandlers: Set<SystemStatusHandler> = new Set()
  private statisticsHandlers: Set<StatisticsHandler> = new Set()
  private errorHandlers: Set<ErrorHandler> = new Set()

  /**
   * 连接到WebSocket服务器
   */
  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.connected) {
        resolve()
        return
      }

      try {
        this.client = new Client({
          // 使用SockJS作为传输层
          webSocketFactory: () => new SockJS('http://localhost:8080/ws/crawler'),
          
          connectHeaders: {
            // 可以在这里添加认证头
          },
          
          debug: (str) => {
            console.log('STOMP Debug:', str)
          },
          
          onConnect: (frame) => {
            console.log('WebSocket连接成功:', frame)
            this.connected = true
            this.reconnectAttempts = 0
            
            // 订阅全局任务日志
            this.subscribeToAllLogs()
            
            resolve()
          },
          
          onStompError: (frame) => {
            console.error('STOMP错误:', frame.headers['message'], frame.body)
            this.connected = false
            reject(new Error(frame.headers['message'] || 'STOMP连接错误'))
          },
          
          onDisconnect: (frame) => {
            console.log('WebSocket连接断开:', frame)
            this.connected = false
            this.handleReconnect()
          },
          
          onWebSocketError: (event) => {
            console.error('WebSocket错误:', event)
            this.connected = false
          }
        })

        this.client.activate()
      } catch (error) {
        console.error('WebSocket连接初始化失败:', error)
        reject(error)
      }
    })
  }

  /**
   * 断开WebSocket连接
   */
  disconnect(): void {
    if (this.client) {
      this.client.deactivate()
      this.client = null
    }
    this.connected = false
    this.subscriptions.clear()
  }

  /**
   * 处理重连逻辑
   */
  private handleReconnect(): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      console.log(`尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)
      
      setTimeout(() => {
        this.connect().catch(error => {
          console.error('重连失败:', error)
        })
      }, this.reconnectInterval * this.reconnectAttempts)
    } else {
      console.error('达到最大重连次数，停止重连')
    }
  }

  /**
   * 订阅所有任务日志
   */
  private subscribeToAllLogs(): void {
    if (!this.client || !this.connected) {
      console.warn('WebSocket未连接，无法订阅日志')
      return
    }

    const subscription = this.client.subscribe('/topic/crawler/task-logs', (message) => {
      try {
        const logData: TaskLogMessage = JSON.parse(message.body)
        this.notifyHandlers(logData)
      } catch (error) {
        console.error('解析日志消息失败:', error, message.body)
      }
    })

    this.subscriptions.set('all-logs', subscription)
  }

  /**
   * 订阅特定任务的日志
   */
  subscribeToTaskLogs(taskId: string, handler?: LogMessageHandler): void {
    if (!this.client || !this.connected) {
      console.warn('WebSocket未连接，无法订阅任务日志')
      return
    }

    const topic = `/topic/crawler/task/${taskId}/logs`
    
    if (!this.subscriptions.has(topic)) {
      const subscription = this.client.subscribe(topic, (message) => {
        try {
          const logData: TaskLogMessage = JSON.parse(message.body)
          console.log(`收到任务 ${taskId} 日志:`, logData)
          this.notifyTaskSpecificHandlers(taskId, logData)
        } catch (error) {
          console.error('解析任务日志消息失败:', error, message.body)
        }
      })
      
      this.subscriptions.set(topic, subscription)
      console.log(`成功订阅任务 ${taskId} 的日志`)
    }

    // 添加任务特定的处理器
    if (handler) {
      if (!this.taskSpecificHandlers.has(taskId)) {
        this.taskSpecificHandlers.set(taskId, new Set())
      }
      this.taskSpecificHandlers.get(taskId)!.add(handler)
    }
  }

  /**
   * 订阅系统状态更新
   */
  subscribeToSystemStatus(handler: SystemStatusHandler): void {
    if (!this.connected || !this.client) {
      console.warn('WebSocket未连接，无法订阅系统状态')
      return
    }

    const destination = '/topic/crawler/system'
    
    if (!this.subscriptions.has(destination)) {
      try {
        const subscription = this.client.subscribe(destination, (message) => {
          try {
            const statusData: SystemStatusMessage = JSON.parse(message.body)
            console.log('收到系统状态更新:', statusData)
            
            // 通知所有系统状态处理器
            this.systemStatusHandlers.forEach(h => h(statusData))
          } catch (error) {
            console.error('解析系统状态消息失败:', error)
          }
        })
        
        this.subscriptions.set(destination, subscription)
        console.log('成功订阅系统状态更新')
      } catch (error) {
        console.error('订阅系统状态失败:', error)
      }
    }
    
    this.systemStatusHandlers.add(handler)
  }

  /**
   * 订阅统计信息更新
   */
  subscribeToStatistics(handler: StatisticsHandler): void {
    if (!this.connected || !this.client) {
      console.warn('WebSocket未连接，无法订阅统计信息')
      return
    }

    const destination = '/topic/crawler/statistics'
    
    if (!this.subscriptions.has(destination)) {
      try {
        const subscription = this.client.subscribe(destination, (message) => {
          try {
            const statsData: StatisticsMessage = JSON.parse(message.body)
            console.log('收到统计信息更新:', statsData)
            
            // 通知所有统计信息处理器
            this.statisticsHandlers.forEach(h => h(statsData))
          } catch (error) {
            console.error('解析统计信息消息失败:', error)
          }
        })
        
        this.subscriptions.set(destination, subscription)
        console.log('成功订阅统计信息更新')
      } catch (error) {
        console.error('订阅统计信息失败:', error)
      }
    }
    
    this.statisticsHandlers.add(handler)
  }

  /**
   * 订阅错误信息
   */
  subscribeToErrors(handler: ErrorHandler): void {
    if (!this.connected || !this.client) {
      console.warn('WebSocket未连接，无法订阅错误信息')
      return
    }

    const destination = '/topic/crawler/errors'
    
    if (!this.subscriptions.has(destination)) {
      try {
        const subscription = this.client.subscribe(destination, (message) => {
          try {
            const errorData: ErrorMessage = JSON.parse(message.body)
            console.log('收到错误信息:', errorData)
            
            // 通知所有错误处理器
            this.errorHandlers.forEach(h => h(errorData))
          } catch (error) {
            console.error('解析错误消息失败:', error)
          }
        })
        
        this.subscriptions.set(destination, subscription)
        console.log('成功订阅错误信息')
      } catch (error) {
        console.error('订阅错误信息失败:', error)
      }
    }
    
    this.errorHandlers.add(handler)
  }

  /**
   * 取消订阅特定任务的日志
   */
  unsubscribeFromTaskLogs(taskId: string): void {
    const topic = `/topic/task-logs/${taskId}`
    const subscription = this.subscriptions.get(topic)
    
    if (subscription) {
      subscription.unsubscribe()
      this.subscriptions.delete(topic)
    }
    
    this.taskSpecificHandlers.delete(taskId)
  }

  /**
   * 添加全局日志消息处理器
   */
  addMessageHandler(handler: LogMessageHandler): void {
    this.messageHandlers.add(handler)
  }

  /**
   * 移除全局日志消息处理器
   */
  removeMessageHandler(handler: LogMessageHandler): void {
    this.messageHandlers.delete(handler)
  }

  /**
   * 通知所有全局处理器
   */
  private notifyHandlers(logData: TaskLogMessage): void {
    this.messageHandlers.forEach(handler => {
      try {
        handler(logData)
      } catch (error) {
        console.error('日志处理器执行错误:', error)
      }
    })
  }

  /**
   * 通知特定任务的处理器
   */
  private notifyTaskSpecificHandlers(taskId: string, logData: TaskLogMessage): void {
    const handlers = this.taskSpecificHandlers.get(taskId)
    if (handlers) {
      handlers.forEach(handler => {
        try {
          handler(logData)
        } catch (error) {
          console.error('任务日志处理器执行错误:', error)
        }
      })
    }
  }

  /**
   * 检查连接状态
   */
  isConnected(): boolean {
    return this.connected
  }

  /**
   * 获取连接状态信息
   */
  getConnectionInfo(): { connected: boolean; reconnectAttempts: number } {
    return {
      connected: this.connected,
      reconnectAttempts: this.reconnectAttempts
    }
  }
}

// 创建单例实例
export const webSocketManager = new WebSocketManager()

// 提供便捷的使用函数
export function useWebSocket() {
  return {
    connect: () => webSocketManager.connect(),
    disconnect: () => webSocketManager.disconnect(),
    subscribeToAllLogs: (handler: LogMessageHandler) => {
      webSocketManager.addMessageHandler(handler)
    },
    unsubscribeFromAllLogs: (handler: LogMessageHandler) => {
      webSocketManager.removeMessageHandler(handler)
    },
    subscribeToTaskLogs: (taskId: string, handler?: LogMessageHandler) => {
      webSocketManager.subscribeToTaskLogs(taskId, handler)
    },
    unsubscribeFromTaskLogs: (taskId: string) => {
      webSocketManager.unsubscribeFromTaskLogs(taskId)
    },
    isConnected: () => webSocketManager.isConnected(),
    getConnectionInfo: () => webSocketManager.getConnectionInfo()
  }
}

// 导出独立的连接和断开连接函数
export const connectWebSocket = () => webSocketManager.connect()
export const disconnectWebSocket = () => webSocketManager.disconnect()