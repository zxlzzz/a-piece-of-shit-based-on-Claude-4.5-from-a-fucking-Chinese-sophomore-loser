import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import {
  isConnected,
  getCurrentPlayerId,
  getConnectionState,
  registerSubscriptionCallback,
  unregisterSubscriptionCallback,
  disconnect
} from '../ws.js'

/**
 * WebSocket 客户端单元测试
 *
 * 注意：这些测试主要测试工具函数和状态管理
 * 完整的WebSocket连接测试需要mock STOMP客户端
 *
 * 测试覆盖：
 * 1. 连接状态查询
 * 2. 订阅回调管理
 * 3. 断开连接
 * 4. 工具函数
 */

describe('WebSocket工具函数测试', () => {
  beforeEach(() => {
    // 每个测试前清理连接状态
    disconnect(true)
  })

  afterEach(() => {
    // 每个测试后清理
    disconnect(true)
  })

  describe('连接状态查询', () => {
    it('未连接时 isConnected() 应该返回 false', () => {
      expect(isConnected()).toBe(false)
    })

    it('未连接时 getCurrentPlayerId() 应该返回 null', () => {
      expect(getCurrentPlayerId()).toBe(null)
    })

    it('getConnectionState() 应该返回连接状态对象', () => {
      const state = getConnectionState()

      expect(state).toHaveProperty('connected')
      expect(state).toHaveProperty('reconnectAttempts')
      expect(state).toHaveProperty('maxAttempts')
      expect(state).toHaveProperty('playerId')

      expect(state.connected).toBe(false)
      expect(state.reconnectAttempts).toBe(0)
      expect(state.playerId).toBe(null)
    })
  })

  describe('订阅回调管理', () => {
    it('应该能注册订阅回调', () => {
      const callback = vi.fn()

      registerSubscriptionCallback(callback)

      // 验证回调被注册（通过再次注册同一个回调不会重复）
      registerSubscriptionCallback(callback)

      // 无法直接验证，但不应该抛错
      expect(() => {
        registerSubscriptionCallback(callback)
      }).not.toThrow()
    })

    it('应该能移除订阅回调', () => {
      const callback = vi.fn()

      registerSubscriptionCallback(callback)
      unregisterSubscriptionCallback(callback)

      // 移除后再次移除不应该抛错
      expect(() => {
        unregisterSubscriptionCallback(callback)
      }).not.toThrow()
    })

    it('不应该注册非函数类型的回调', () => {
      const notAFunction = 'not a function'

      expect(() => {
        registerSubscriptionCallback(notAFunction)
      }).not.toThrow()
    })

    it('应该能注册多个不同的回调', () => {
      const callback1 = vi.fn()
      const callback2 = vi.fn()
      const callback3 = vi.fn()

      expect(() => {
        registerSubscriptionCallback(callback1)
        registerSubscriptionCallback(callback2)
        registerSubscriptionCallback(callback3)
      }).not.toThrow()

      // 清理
      unregisterSubscriptionCallback(callback1)
      unregisterSubscriptionCallback(callback2)
      unregisterSubscriptionCallback(callback3)
    })

    it('移除不存在的回调不应该抛错', () => {
      const callback = vi.fn()

      expect(() => {
        unregisterSubscriptionCallback(callback)
      }).not.toThrow()
    })
  })

  describe('断开连接', () => {
    it('disconnect() 应该清理连接状态', () => {
      disconnect()

      expect(isConnected()).toBe(false)
      expect(getCurrentPlayerId()).toBe(null)
    })

    it('disconnect(true) 应该强制清理所有状态', () => {
      const callback = vi.fn()
      registerSubscriptionCallback(callback)

      disconnect(true)

      expect(isConnected()).toBe(false)
      expect(getCurrentPlayerId()).toBe(null)
    })

    it('多次调用 disconnect() 不应该抛错', () => {
      expect(() => {
        disconnect()
        disconnect()
        disconnect()
      }).not.toThrow()
    })
  })

  describe('边界情况', () => {
    it('在未连接状态下调用各种函数不应该抛错', () => {
      expect(() => {
        isConnected()
        getCurrentPlayerId()
        getConnectionState()
        disconnect()
      }).not.toThrow()
    })

    it('注册null回调不应该抛错', () => {
      expect(() => {
        registerSubscriptionCallback(null)
      }).not.toThrow()
    })

    it('注册undefined回调不应该抛错', () => {
      expect(() => {
        registerSubscriptionCallback(undefined)
      }).not.toThrow()
    })

    it('移除null回调不应该抛错', () => {
      expect(() => {
        unregisterSubscriptionCallback(null)
      }).not.toThrow()
    })
  })
})

/**
 * 消息发送函数测试
 *
 * 注意：这些函数依赖连接状态，未连接时应该安全返回
 */
describe('WebSocket消息发送测试', () => {
  beforeEach(() => {
    disconnect(true)
  })

  afterEach(() => {
    disconnect(true)
  })

  // 由于sendJoin、sendStart等函数依赖实际的STOMP连接
  // 这里只测试在未连接状态下调用不会抛错

  it('未连接时调用发送函数不应该抛错', async () => {
    // 动态导入发送函数
    const ws = await import('../ws.js')

    expect(() => {
      ws.sendJoin({ roomCode: 'TEST', playerId: 'player1', playerName: 'Test' })
      ws.sendStart({ roomCode: 'TEST' })
      ws.sendSubmit({ roomCode: 'TEST', playerId: 'player1', choice: 'A' })
      ws.sendReady({ roomCode: 'TEST', playerId: 'player1', ready: true })
      ws.sendLeave({ roomCode: 'TEST', playerId: 'player1' })
      ws.sendMessage('/test/destination', { test: 'data' })
    }).not.toThrow()
  })
})

/**
 * 连接状态对象结构测试
 */
describe('连接状态对象结构', () => {
  it('getConnectionState() 返回的对象应该包含所有必需字段', () => {
    const state = getConnectionState()

    expect(state).toBeDefined()
    expect(typeof state.connected).toBe('boolean')
    expect(typeof state.reconnectAttempts).toBe('number')
    expect(typeof state.maxAttempts).toBe('number')

    // playerId可以是null或string
    expect(state.playerId === null || typeof state.playerId === 'string').toBe(true)
  })

  it('初始状态应该是未连接', () => {
    const state = getConnectionState()

    expect(state.connected).toBe(false)
    expect(state.reconnectAttempts).toBe(0)
    expect(state.playerId).toBe(null)
  })

  it('maxAttempts 应该是正整数', () => {
    const state = getConnectionState()

    expect(state.maxAttempts).toBeGreaterThan(0)
    expect(Number.isInteger(state.maxAttempts)).toBe(true)
  })
})

/**
 * 模块导出测试
 */
describe('WebSocket模块导出', () => {
  it('应该导出所有必需的函数', async () => {
    const ws = await import('../ws.js')

    // 检查必需的导出
    expect(typeof ws.connect).toBe('function')
    expect(typeof ws.disconnect).toBe('function')
    expect(typeof ws.reconnect).toBe('function')
    expect(typeof ws.subscribeRoom).toBe('function')
    expect(typeof ws.unsubscribe).toBe('function')
    expect(typeof ws.unsubscribeAll).toBe('function')
    expect(typeof ws.sendJoin).toBe('function')
    expect(typeof ws.sendStart).toBe('function')
    expect(typeof ws.sendSubmit).toBe('function')
    expect(typeof ws.sendReady).toBe('function')
    expect(typeof ws.sendLeave).toBe('function')
    expect(typeof ws.isConnected).toBe('function')
    expect(typeof ws.getCurrentPlayerId).toBe('function')
    expect(typeof ws.getStompClient).toBe('function')
    expect(typeof ws.sendMessage).toBe('function')
    expect(typeof ws.getConnectionState).toBe('function')
    expect(typeof ws.registerSubscriptionCallback).toBe('function')
    expect(typeof ws.unregisterSubscriptionCallback).toBe('function')
  })

  it('应该有默认导出', async () => {
    const ws = await import('../ws.js')

    expect(ws.default).toBeDefined()
    expect(typeof ws.default).toBe('object')
  })
})
