package org.example.pojo;

/**
 * 游戏模式
 * SYNCHRONIZED: 同时答题——所有玩家在同一题目页面等待，全部提交或超时后才进入下一题。
 * ASYNC:        异步答题——玩家提交后无需在题目页面等待，可切换至等待视图；
 *               后端仍等全部玩家提交后再结算并推进下一题。
 */
public enum GameMode {
    SYNCHRONIZED,
    ASYNC
}
