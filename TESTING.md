# 测试文档

本文档说明如何运行和编写项目的测试。

## 目录

- [概述](#概述)
- [后端测试](#后端测试)
  - [运行后端测试](#运行后端测试)
  - [后端测试结构](#后端测试结构)
  - [编写后端测试](#编写后端测试)
- [前端测试](#前端测试)
  - [安装前端测试依赖](#安装前端测试依赖)
  - [运行前端测试](#运行前端测试)
  - [前端测试结构](#前端测试结构)
  - [编写前端测试](#编写前端测试)
- [测试覆盖率](#测试覆盖率)
- [持续集成](#持续集成)

---

## 概述

本项目包含两部分测试：
- **后端测试**：使用 JUnit 5 + Mockito + Spring Boot Test
- **前端测试**：使用 Vitest + Vue Test Utils

已有测试覆盖：
- ✅ 认证服务 (AuthServiceImpl)
- ✅ JWT工具类 (JwtUtil)
- ✅ 计分服务 (ScoringServiceImpl)
- ✅ 问题策略示例 (Q002PerformanceCostumeStrategy)
- ✅ WebSocket客户端工具函数

---

## 后端测试

### 运行后端测试

#### 1. 使用 Maven 运行所有测试

```bash
cd backend
mvn test
```

#### 2. 运行特定测试类

```bash
mvn test -Dtest=AuthServiceImplTest
```

#### 3. 运行特定测试方法

```bash
mvn test -Dtest=AuthServiceImplTest#register_Success
```

#### 4. 跳过测试（构建时）

```bash
mvn clean install -DskipTests
```

#### 5. 在 IDE 中运行

**IntelliJ IDEA:**
- 右键点击测试类或测试方法
- 选择 "Run 'TestName'"

**Eclipse:**
- 右键点击测试类
- 选择 "Run As" → "JUnit Test"

### 后端测试结构

```
backend/src/test/java/org/example/
├── service/
│   ├── auth/
│   │   └── impl/
│   │       └── AuthServiceImplTest.java          # 认证服务测试
│   ├── scoring/
│   │   └── impl/
│   │       └── ScoringServiceImplTest.java       # 计分服务测试
│   └── strategy/
│       └── Q002PerformanceCostumeStrategyTest.java  # Q002策略测试
└── utils/
    └── JwtUtilTest.java                           # JWT工具测试
```

### 编写后端测试

#### 基本模板

```java
package org.example.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("你的服务测试")
class YourServiceTest {

    @Mock
    private YourDependency dependency;

    @InjectMocks
    private YourService service;

    @BeforeEach
    void setUp() {
        // 测试前的准备工作
    }

    @Test
    @DisplayName("测试描述")
    void testMethod() {
        // Given（准备）
        when(dependency.someMethod()).thenReturn(someValue);

        // When（执行）
        var result = service.methodUnderTest();

        // Then（验证）
        assertNotNull(result);
        assertEquals(expectedValue, result);
        verify(dependency).someMethod();
    }
}
```

#### 测试注解说明

- `@ExtendWith(MockitoExtension.class)` - 启用 Mockito
- `@Mock` - 创建 mock 对象
- `@InjectMocks` - 自动注入 mock 对象到被测试类
- `@BeforeEach` - 每个测试前执行
- `@Test` - 标记测试方法
- `@DisplayName` - 测试的中文描述

#### 常用断言

```java
// 基本断言
assertEquals(expected, actual);
assertNotEquals(value1, value2);
assertTrue(condition);
assertFalse(condition);
assertNull(object);
assertNotNull(object);

// 异常断言
assertThrows(ExceptionClass.class, () -> {
    service.methodThatThrows();
});

// 集合断言
assertTrue(list.isEmpty());
assertEquals(3, list.size());
assertTrue(list.contains(item));
```

#### Mock 使用示例

```java
// 方法返回值
when(mock.method()).thenReturn(value);

// 方法抛异常
when(mock.method()).thenThrow(new Exception());

// 验证方法调用
verify(mock).method();
verify(mock, times(2)).method();
verify(mock, never()).method();

// 参数匹配
when(mock.method(anyString())).thenReturn(value);
when(mock.method(eq("test"))).thenReturn(value);
```

---

## 前端测试

### 安装前端测试依赖

```bash
cd frontend
npm install
```

这会安装以下测试相关依赖：
- `vitest` - 测试框架
- `@vitest/ui` - 测试UI界面
- `@vue/test-utils` - Vue组件测试工具
- `happy-dom` - DOM环境模拟

### 运行前端测试

#### 1. 运行所有测试（监听模式）

```bash
cd frontend
npm test
```

或者：

```bash
npm run test
```

#### 2. 运行一次测试（CI模式）

```bash
npm test -- --run
```

#### 3. 使用UI界面运行测试

```bash
npm run test:ui
```

然后在浏览器打开 http://localhost:51204/__vitest__/

#### 4. 生成测试覆盖率报告

```bash
npm run test:coverage
```

#### 5. 运行特定测试文件

```bash
npm test ws.test.js
```

#### 6. 运行匹配特定模式的测试

```bash
npm test -- --grep="连接状态"
```

### 前端测试结构

```
frontend/src/
├── websocket/
│   ├── __tests__/
│   │   └── ws.test.js                # WebSocket客户端测试
│   └── ws.js
├── composables/
│   └── __tests__/                    # Composable函数测试
├── utils/
│   └── __tests__/                    # 工具函数测试
└── components/
    └── __tests__/                    # Vue组件测试
```

### 编写前端测试

#### 基本模板

```javascript
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

describe('功能模块测试', () => {
  beforeEach(() => {
    // 每个测试前的准备
  })

  afterEach(() => {
    // 每个测试后的清理
  })

  it('应该做某事', () => {
    // Given（准备）
    const input = 'test'

    // When（执行）
    const result = functionUnderTest(input)

    // Then（验证）
    expect(result).toBe('expected')
  })
})
```

#### Vue 组件测试模板

```javascript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import YourComponent from '@/components/YourComponent.vue'

describe('YourComponent', () => {
  it('应该正确渲染', () => {
    const wrapper = mount(YourComponent, {
      props: {
        propName: 'propValue'
      }
    })

    expect(wrapper.text()).toContain('期望的文本')
  })

  it('点击按钮应该触发事件', async () => {
    const wrapper = mount(YourComponent)

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted()).toHaveProperty('eventName')
  })
})
```

#### 常用断言

```javascript
// 基本断言
expect(value).toBe(expected)          // 严格相等 (===)
expect(value).toEqual(expected)       // 深度相等
expect(value).toBeTruthy()
expect(value).toBeFalsy()
expect(value).toBeNull()
expect(value).toBeUndefined()
expect(value).toBeDefined()

// 数字断言
expect(number).toBeGreaterThan(3)
expect(number).toBeLessThan(5)
expect(number).toBeCloseTo(3.14, 2)

// 数组/对象断言
expect(array).toContain(item)
expect(array).toHaveLength(3)
expect(object).toHaveProperty('key')
expect(object).toHaveProperty('key', 'value')

// 函数断言
expect(fn).toThrow()
expect(fn).toThrow(ErrorClass)
expect(fn).not.toThrow()
```

#### Mock 函数

```javascript
import { vi } from 'vitest'

// 创建 mock 函数
const mockFn = vi.fn()

// 设置返回值
mockFn.mockReturnValue(42)
mockFn.mockResolvedValue('async result')

// 验证调用
expect(mockFn).toHaveBeenCalled()
expect(mockFn).toHaveBeenCalledTimes(2)
expect(mockFn).toHaveBeenCalledWith('arg1', 'arg2')
```

#### Mock 模块

```javascript
// 完全 mock 模块
vi.mock('@/utils/api', () => ({
  fetchData: vi.fn(() => Promise.resolve({ data: 'mocked' }))
}))

// 部分 mock 模块
vi.mock('@/utils/api', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    fetchData: vi.fn(() => Promise.resolve({ data: 'mocked' }))
  }
})
```

---

## 测试覆盖率

### 后端测试覆盖率

使用 JaCoCo 插件生成覆盖率报告（需要在 pom.xml 中配置）：

```bash
cd backend
mvn clean test jacoco:report
```

报告位置：`backend/target/site/jacoco/index.html`

### 前端测试覆盖率

```bash
cd frontend
npm run test:coverage
```

报告位置：`frontend/coverage/index.html`

### 查看覆盖率报告

在浏览器中打开生成的 HTML 文件即可查看详细的覆盖率报告。

---

## 测试最佳实践

### 1. 测试命名

- 使用描述性的测试名称
- 说明测试的场景和预期结果

```java
// ✅ 好的命名
@Test
@DisplayName("注册失败 - 用户名已存在")
void register_UsernameExists_ThrowsException() { }

// ❌ 不好的命名
@Test
void test1() { }
```

### 2. 测试结构（AAA模式）

```java
@Test
void testMethod() {
    // Arrange（准备）- Given
    var input = createTestData();

    // Act（执行）- When
    var result = service.method(input);

    // Assert（验证）- Then
    assertEquals(expected, result);
}
```

### 3. 一个测试只测一件事

```java
// ✅ 好的做法
@Test
void login_ValidCredentials_ReturnsToken() {
    // 只测试成功登录
}

@Test
void login_InvalidPassword_ThrowsException() {
    // 只测试密码错误
}

// ❌ 不好的做法
@Test
void testLogin() {
    // 同时测试多个场景
}
```

### 4. 测试独立性

每个测试应该独立运行，不依赖其他测试的执行顺序。

```java
@BeforeEach
void setUp() {
    // 每个测试前都重新初始化
    service = new Service();
}
```

### 5. 使用有意义的测试数据

```java
// ✅ 好的做法
String username = "testuser";
String password = "password123";

// ❌ 不好的做法
String s = "a";
String p = "b";
```

---

## 持续集成

在 CI/CD 流程中运行测试：

### GitHub Actions 示例

```yaml
name: Tests

on: [push, pull_request]

jobs:
  backend-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run tests
        run: |
          cd backend
          mvn test

  frontend-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'
      - name: Install dependencies
        run: |
          cd frontend
          npm install
      - name: Run tests
        run: |
          cd frontend
          npm test -- --run
```

---

## 常见问题

### Q: 后端测试报错 "Cannot find symbol"

**A:** 确保已经编译了主代码：
```bash
mvn clean compile test-compile test
```

### Q: 前端测试报错 "Cannot find module"

**A:** 确保已安装依赖：
```bash
cd frontend
npm install
```

### Q: Mock 不起作用

**A:** 检查：
1. 是否使用了 `@ExtendWith(MockitoExtension.class)`（后端）
2. Mock 对象是否正确注入
3. 是否正确设置了 mock 行为

### Q: 测试在 IDE 中能运行，在命令行失败

**A:** 可能是环境配置问题，尝试：
```bash
mvn clean test  # 后端
npm run test -- --run  # 前端
```

### Q: 如何调试测试

**A:**
- **后端**: 在 IDE 中使用 Debug 模式运行测试
- **前端**: 在测试代码中使用 `console.log()` 或浏览器调试工具（使用 `npm run test:ui`）

---

## 下一步

现在你可以：

1. **运行现有测试**：验证所有测试都通过
   ```bash
   cd backend && mvn test
   cd frontend && npm test -- --run
   ```

2. **查看测试覆盖率**：了解哪些代码还没有测试
   ```bash
   cd frontend && npm run test:coverage
   ```

3. **编写新测试**：参考现有测试为其他功能添加测试

4. **持续改进**：逐步提高测试覆盖率

---

## 参考资料

- [JUnit 5 文档](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito 文档](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Spring Boot Testing](https://spring.io/guides/gs/testing-web)
- [Vitest 文档](https://vitest.dev/)
- [Vue Test Utils](https://test-utils.vuejs.org/)

---

**Happy Testing! 🧪**
