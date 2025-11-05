# Boilune项目介绍
> 一个侧重于玩家心理策略对抗的答题网站
## 1. 简介
本项目是一个桌游性质的多人在线答题的网站，游戏的目标可以为取得最高分（或自行决定，获得最接近平均分，最低分都可以是合适的目标），题目参考各种博弈模型（某种剪刀石头布），如囚徒困境，各种竞价的简单模拟，比起计算和简单的答案，更侧重于人性的博弈。
会考虑长期运营，如果有想交流的内容，题目建议或游戏内容上的建议等内容欢迎联系作者。
## 2.功能概览
- 支持一键注册游玩
- 等待界面可以选择房间内容（包括题目标签筛选，游戏目标等内容）
- 题目多样
- 支持实时通信对话
- 所有题目结束时生成结果显示排行榜，可保存游戏记录
- Docker一键部署
## 3.技术栈介绍
我是新手捏，只能简单说下
前端：以vue3框架为主
后端：以springboot3框架为主，采用java17语法
使用了websocket维持通讯，docker做部署，
## 4.为什么做这个网站
~~因为我想玩，也为了看看ai大人到底有多nb~~
除此之外，也是为了完成java的大作业和想真正的部署一个网站，现在结构似乎拆的又臭又长。。。
由于每道题的逻辑独立，虽可能有部分题目直接存在相似，但为了保持复用性和保持可拓展性，每题采用独立的questionStrategy处理，这种思路似乎恰好和函数式编程的思路恰和，所以这也是作者对于java函数式编程的练手项目（虽然大部分都是ai大人写的），具体可给出下面这道题做参考
```java
/**
 * 题目：你们二人参加演出，盲选服装。如果集齐侍卫+王子，则获得选项分数，否则扣分。
 *  A：精致的侍卫服装（7）
 *  B：王子服装（5）
 *  C：普通侍卫服装（3）
 */
    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        boolean complete = submissions.containsValue("B")&&submissions.containsValue("C")||submissions.containsValue("A");
        return submissions.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e->{
                            int v = switch(e.getValue()){
                                case "A" -> 7;
                                case "B" -> 5;
                                default -> 3;
                            };
                            return complete? v:-v;
                        }
                ));
    }
```

## 5.部署
这里之后应该会贴实际部署的连接

~~这玩意有啥好部署的~~
技术栈前面都写了，应该都是正常的nodejs和mvn的操作，鼠鼠是新手也没啥经验，这部分有问题可以问我（虽然我感觉不如直接问ai）
## 6.疑问
欢迎联系作者


# TODO
希望寒假结束前能先上线玩玩捏
上线之后要是更新再更新这部分内容


## License
MIT License © 2025 zxlzzz