package com.herra.stamina.api;

/**
 * 由客户端 HUD 安装的显示桥，让公共代码能读取本地插值显示值，
 * 而不引用任何客户端类（服务端安全）。
 */
public interface ClientDisplayBridge {

    /** 本地玩家 HUD 的插值显示比例 [0, 1]。 */
    double ratio();

    /** 服务端同步来的最大体力值。 */
    float max();

    /** 是否处于透支锁定。 */
    boolean depleted();
}
