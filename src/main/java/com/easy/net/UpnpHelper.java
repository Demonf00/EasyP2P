package com.easy.net;

import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.GatewayDevice;

import java.net.InetAddress;
import java.util.Map;

/** 基于 weupnp 的简单 UPnP 工具 */
public final class UpnpHelper {
    private static GatewayDevice device;     // 已选中的网关
    private static InetAddress   localAddr;  // 网关识别出的本机地址

    private UpnpHelper(){}

    /** 发现网关（只做一次缓存） */
    private static synchronized boolean ensureGateway() throws Exception {
        if (device != null && localAddr != null) return true;
        GatewayDiscover discover = new GatewayDiscover();
        Map<InetAddress, GatewayDevice> map = discover.discover();
        device = discover.getValidGateway();
        if (device == null) return false;
        localAddr = device.getLocalAddress();
        return localAddr != null;
    }

    /** 开 TCP 端口映射：external=port → internal=localAddr:port */
    public static synchronized boolean openTcp(int port, String desc) {
        try {
            if (!ensureGateway()) return false;
            // 某些路由已存在时返回 false，先删再加更稳
            try { device.deletePortMapping(port, "TCP"); } catch (Exception ignore) {}
            return device.addPortMapping(port, port, localAddr.getHostAddress(), "TCP", desc);
        } catch (Exception e) {
            return false;
        }
    }

    /** 关 TCP 端口映射 */
    public static synchronized void closeTcp(int port) {
        try {
            if (device != null) device.deletePortMapping(port, "TCP");
        } catch (Exception ignore) {}
    }
}
